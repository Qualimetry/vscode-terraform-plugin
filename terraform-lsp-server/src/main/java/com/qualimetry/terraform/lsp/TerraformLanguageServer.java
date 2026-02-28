package com.qualimetry.terraform.lsp;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.Map;

/**
 * Terraform LSP server: runs tflint/trivy/checkov, publishes diagnostics, provides hover with rule content.
 */
public class TerraformLanguageServer implements LanguageServer, LanguageClientAware {

    private static final int DEBOUNCE_MS = 600;

    private final TerraformTextDocumentService textDocumentService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "terraform-lsp-debounce");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> pendingRun;
    private volatile String pendingRunUri;
    private LanguageClient client;
    private String workspaceRoot;
    /** Executable names/paths from client (terraformAnalyzer.tflintPath etc.); null = use default "tflint"/"trivy"/"checkov". */
    private String tflintPath;
    private String trivyPath;
    private String checkovPath;
    private final List<Diagnostic> lastDiagnostics = new ArrayList<>();
    private String lastDiagnosticsUri;

    public TerraformLanguageServer() {
        this.textDocumentService = new TerraformTextDocumentService(this);
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        Object rootUri = params.getRootUri();
        if (rootUri != null && rootUri.toString().startsWith("file:")) {
            try {
                Path p = Paths.get(URI.create(rootUri.toString()));
                if (p.toFile().exists()) {
                    workspaceRoot = p.toAbsolutePath().toString();
                }
            } catch (Exception e) {
                workspaceRoot = null;
            }
        }
        if (workspaceRoot == null && params.getRootPath() != null) {
            workspaceRoot = params.getRootPath();
        }
        readToolPathsFromInitOptions(params.getInitializationOptions());
        log("initialize workspaceRoot=" + workspaceRoot);
        InitializeResult result = new InitializeResult();
        ServerCapabilities caps = new ServerCapabilities();
        caps.setTextDocumentSync(TextDocumentSyncKind.Full);
        caps.setHoverProvider(true);
        caps.setExecuteCommandProvider(new ExecuteCommandOptions(List.of("terraform.runAnalysis")));
        result.setCapabilities(caps);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        if (pendingRun != null) {
            pendingRun.cancel(false);
        }
        scheduler.shutdown();
    }

    @Override
    public TerraformTextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new WorkspaceService() {
            @Override
            public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
                log("executeCommand received: " + params.getCommand() + " args=" + params.getArguments());
                if ("terraform.runAnalysis".equals(params.getCommand()) && params.getArguments() != null && !params.getArguments().isEmpty()) {
                    Object arg = params.getArguments().get(0);
                    String uri = arg != null ? arg.toString() : null;
                    if (uri != null && (uri.endsWith(".tf") || uri.endsWith(".tf.json"))) {
                        runToolsAndPublishDiagnostics(uri);
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
            @Override
            public CompletableFuture<Either<List<? extends org.eclipse.lsp4j.SymbolInformation>, List<? extends org.eclipse.lsp4j.WorkspaceSymbol>>> symbol(org.eclipse.lsp4j.WorkspaceSymbolParams params) {
                return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
            }
            @Override
            public void didChangeConfiguration(org.eclipse.lsp4j.DidChangeConfigurationParams params) {
            }
            @Override
            public void didChangeWatchedFiles(org.eclipse.lsp4j.DidChangeWatchedFilesParams params) {
            }
        };
    }

    void runToolsAndPublishDiagnostics(String documentUri) {
        String root = workspaceRoot;
        // When a specific file is open, run tools from that file's directory so we get findings for it
        if (documentUri != null && documentUri.startsWith("file:")) {
            try {
                Path p = Paths.get(URI.create(documentUri));
                File f = p.toFile();
                if (f.isFile()) {
                    root = f.getParentFile().getAbsolutePath();
                } else if (root == null) {
                    root = p.toAbsolutePath().toString();
                }
            } catch (Exception e) {
                if (root == null) root = "";
            }
        }
        if (root == null) root = "";
        log("runToolsAndPublishDiagnostics root=" + root + " uri=" + documentUri);
        ToolRunResult tflintResult = ToolRunner.runTflint(root, tflintPath);
        ToolRunResult trivyResult = ToolRunner.runTrivy(root, trivyPath);
        ToolRunResult checkovResult = ToolRunner.runCheckov(root, checkovPath);
        String tflintJson = tflintResult.output();
        String trivyJson = trivyResult.output();
        String checkovJson = checkovResult.output();
        log("tflint=" + (tflintResult.isSuccess() ? "ok" : tflintResult.getFailureMessage()) + " trivy=" + (trivyResult.isSuccess() ? "ok" : trivyResult.getFailureMessage()) + " checkov=" + (checkovResult.isSuccess() ? "ok" : checkovResult.getFailureMessage()));
        if (!tflintResult.isSuccess() && !trivyResult.isSuccess() && !checkovResult.isSuccess()) {
            String userMessage = buildToolsUnavailableMessage(tflintResult, trivyResult, checkovResult);
            log(userMessage);
            if (client != null) {
                MessageParams params = new MessageParams();
                params.setType(MessageType.Warning);
                params.setMessage(userMessage);
                client.showMessage(params);
            }
        }
        List<Diagnostic> diagnostics;
        try {
            DiagnosticMapper mapper = new DiagnosticMapper();
            URI filterUri = documentUri != null ? URI.create(documentUri) : null;
            diagnostics = mapper.mapWorkspaceDiagnostics(root, tflintJson, trivyJson, checkovJson, filterUri);
        } catch (Throwable t) {
            log("DiagnosticMapper error: " + t.getMessage());
            t.printStackTrace(System.err);
            System.err.flush();
            diagnostics = new ArrayList<>();
        }
        synchronized (lastDiagnostics) {
            lastDiagnostics.clear();
            lastDiagnostics.addAll(diagnostics);
            lastDiagnosticsUri = documentUri;
        }
        if (client != null && documentUri != null) {
            client.publishDiagnostics(new org.eclipse.lsp4j.PublishDiagnosticsParams(documentUri, diagnostics));
        }
        log("Published " + diagnostics.size() + " diagnostics for " + documentUri);
    }

    private void readToolPathsFromInitOptions(Object initOptions) {
        tflintPath = null;
        trivyPath = null;
        checkovPath = null;
        if (initOptions instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) initOptions;
            Object v = m.get("tflintPath");
            if (v instanceof String) tflintPath = (String) v;
            v = m.get("trivyPath");
            if (v instanceof String) trivyPath = (String) v;
            v = m.get("checkovPath");
            if (v instanceof String) checkovPath = (String) v;
        }
    }

    private static String buildToolsUnavailableMessage(ToolRunResult tflint, ToolRunResult trivy, ToolRunResult checkov) {
        StringBuilder sb = new StringBuilder("Terraform analysis unavailable: ");
        sb.append("tflint ").append(tflint.getFailureMessage());
        sb.append("; trivy ").append(trivy.getFailureMessage());
        sb.append("; checkov ").append(checkov.getFailureMessage());
        sb.append(". Install the tools on PATH or set terraformAnalyzer.tflintPath / trivyPath / checkovPath. See the extension README.");
        return sb.toString();
    }

    /**
     * Schedules a run of tools after a short delay. Cancels any previously scheduled run.
     * Used on didChange so we re-run after the user stops typing (debounced), matching
     * Gherkin/Ansible behaviour of triggering on edit without running on every keystroke.
     */
    void scheduleRunToolsAfterChange(String documentUri) {
        if (documentUri == null || (!documentUri.endsWith(".tf") && !documentUri.endsWith(".tf.json"))) {
            return;
        }
        if (pendingRun != null) {
            pendingRun.cancel(false);
            pendingRun = null;
        }
        pendingRunUri = documentUri;
        pendingRun = scheduler.schedule(() -> {
            try {
                String uri = pendingRunUri;
                pendingRunUri = null;
                pendingRun = null;
                if (uri != null) {
                    runToolsAndPublishDiagnostics(uri);
                }
            } catch (Exception e) {
                log("Debounced run failed: " + e.getMessage());
            }
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    void log(String msg) {
        System.err.println("[Terraform LSP] " + msg);
        System.err.flush();
    }

    void publishDiagnostics(String uri, List<Diagnostic> diagnostics) {
        if (client != null) {
            client.publishDiagnostics(new org.eclipse.lsp4j.PublishDiagnosticsParams(uri, diagnostics));
        }
    }

    List<Diagnostic> getDiagnosticsForUri(String uri) {
        synchronized (lastDiagnostics) {
            if (uri != null && uri.equals(lastDiagnosticsUri)) {
                return new ArrayList<>(lastDiagnostics);
            }
        }
        return new ArrayList<>();
    }
}

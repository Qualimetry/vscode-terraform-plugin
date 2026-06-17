package com.qualimetry.terraform.lsp;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Handles text document open/change/save (run tools, publish diagnostics) and hover (rule content).
 */
public class TerraformTextDocumentService implements TextDocumentService {

    private final TerraformLanguageServer server;

    public TerraformTextDocumentService(TerraformLanguageServer server) {
        this.server = server;
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        String uri = params.getTextDocument().getUri();
        List<org.eclipse.lsp4j.Diagnostic> diagnostics = server.getDiagnosticsForUri(uri);
        int line = params.getPosition().getLine();
        int character = params.getPosition().getCharacter();
        String ruleKey = null;
        for (org.eclipse.lsp4j.Diagnostic d : diagnostics) {
            if (d.getRange().getStart().getLine() == line) {
                Object code = d.getCode();
                if (code instanceof String) {
                    ruleKey = (String) code;
                    break;
                }
            }
        }
        if (ruleKey != null) {
            String markdown = RuleContentService.getMarkdown(ruleKey);
            if (markdown != null) {
                Hover hover = new Hover();
                hover.setContents(new MarkupContent(MarkupKind.MARKDOWN, markdown));
                return CompletableFuture.completedFuture(hover);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        TextDocumentItem doc = params.getTextDocument();
        String uri = doc.getUri();
        server.log("didOpen uri=" + uri + " isTerraform=" + isTerraformFile(uri));
        if (uri != null && isTerraformFile(uri)) {
            server.runToolsAndPublishDiagnostics(uri);
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        if (uri != null && isTerraformFile(uri)) {
            server.scheduleRunToolsAfterChange(uri);
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        if (params.getTextDocument().getUri() != null) {
            server.publishDiagnostics(params.getTextDocument().getUri(), Collections.emptyList());
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        if (params.getTextDocument().getUri() != null && isTerraformFile(params.getTextDocument().getUri())) {
            server.runToolsAndPublishDiagnostics(params.getTextDocument().getUri());
        }
    }

    private static boolean isTerraformFile(String uri) {
        return uri != null && (uri.endsWith(".tf") || uri.endsWith(".tf.json"));
    }
}

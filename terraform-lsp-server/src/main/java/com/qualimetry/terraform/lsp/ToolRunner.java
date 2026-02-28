package com.qualimetry.terraform.lsp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Runs tflint, Trivy config, and checkov from a workspace root and returns their JSON output
 * or a structured failure reason (NOT_FOUND, TIMEOUT, etc.) so the client can show clear messages
 * when tools are not installed or misconfigured.
 */
public final class ToolRunner {

    private static final int TIMEOUT_SEC = 120;

    private ToolRunner() {
    }

    public static ToolRunResult runTflint(String workspaceRoot, String tflintExecutable) {
        String exe = tflintExecutable != null && !tflintExecutable.isBlank() ? tflintExecutable : "tflint";
        return runProcess(workspaceRoot, exe, "--format", "json");
    }

    public static ToolRunResult runTrivy(String workspaceRoot, String trivyExecutable) {
        String exe = trivyExecutable != null && !trivyExecutable.isBlank() ? trivyExecutable : "trivy";
        ToolRunResult raw = runProcess(workspaceRoot, exe, "config", "-f", "json", ".");
        if (!raw.isSuccess()) return raw;
        String json = extractJsonFromOutput(raw.output());
        return json != null ? ToolRunResult.success(json) : ToolRunResult.failure(ToolRunResult.NO_JSON);
    }

    public static ToolRunResult runCheckov(String workspaceRoot, String checkovExecutable) {
        String exe = checkovExecutable != null && !checkovExecutable.isBlank() ? checkovExecutable : "checkov";
        ToolRunResult raw = runProcess(workspaceRoot, exe, "-d", ".", "--output", "json", "--quiet");
        if (!raw.isSuccess()) return raw;
        String json = extractJsonFromOutput(raw.output());
        return json != null ? ToolRunResult.success(json) : ToolRunResult.failure(ToolRunResult.NO_JSON);
    }

    /**
     * Runs a process and returns either success (stdout) or a failure reason.
     * Distinguishes "not found", timeout, and non-zero exit for clearer user messages.
     */
    private static ToolRunResult runProcess(String workDir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(new File(workDir))
                    .redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            if (!p.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return ToolRunResult.failure(ToolRunResult.TIMEOUT);
            }
            int exitValue = p.exitValue();
            String out = sb.toString();
            if (exitValue != 0) {
                return ToolRunResult.failure(ToolRunResult.NON_ZERO_EXIT);
            }
            return ToolRunResult.success(out);
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("cannot run program") || msg.contains("no such file") || msg.contains("error=2")
                    || msg.contains("not found") || msg.contains("not recognized")) {
                return ToolRunResult.failure(ToolRunResult.NOT_FOUND);
            }
            return ToolRunResult.failure(ToolRunResult.OTHER);
        } catch (Exception e) {
            return ToolRunResult.failure(ToolRunResult.OTHER);
        }
    }

    /**
     * Trivy/checkov can write log lines or a banner to stdout before the JSON. Extract the JSON object (from first '{' to end).
     */
    private static String extractJsonFromOutput(String output) {
        if (output == null || output.isEmpty()) return null;
        int start = output.indexOf('{');
        if (start < 0) return null;
        return output.substring(start);
    }
}

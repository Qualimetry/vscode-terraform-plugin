package com.qualimetry.terraform.lsp;

/**
 * Result of running an external tool (tflint, Trivy, checkov).
 * Either success with JSON output, or failure with a reason for user-facing messages.
 */
public record ToolRunResult(String output, String failureReason) {

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String NON_ZERO_EXIT = "NON_ZERO_EXIT";
    public static final String NO_JSON = "NO_JSON";
    public static final String OTHER = "OTHER";

    public static ToolRunResult success(String output) {
        return new ToolRunResult(output, null);
    }

    public static ToolRunResult failure(String reason) {
        return new ToolRunResult(null, reason);
    }

    public boolean isSuccess() {
        return failureReason == null && output != null;
    }

    /** User-facing short description of the failure for messages. */
    public String getFailureMessage() {
        if (failureReason == null) return null;
        return switch (failureReason) {
            case NOT_FOUND -> "not found on PATH";
            case TIMEOUT -> "timed out";
            case NON_ZERO_EXIT -> "exited with error";
            case NO_JSON -> "no JSON output";
            default -> "failed";
        };
    }
}

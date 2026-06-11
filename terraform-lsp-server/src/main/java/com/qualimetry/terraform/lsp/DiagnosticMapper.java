package com.qualimetry.terraform.lsp;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.qualimetry.terraform.rules.CheckovOutputParser;
import com.qualimetry.terraform.rules.MappedFinding;
import com.qualimetry.terraform.rules.RuleRegistry;
import com.qualimetry.terraform.rules.TflintOutputParser;
import com.qualimetry.terraform.rules.ToolFinding;
import com.qualimetry.terraform.rules.ToolResultMapper;
import com.qualimetry.terraform.rules.TrivyOutputParser;

/**
 * Converts tool output (tflint/trivy/checkov JSON) into LSP Diagnostic list.
 * Filters by file URI when provided. Uses RuleRegistry for severity mapping.
 * Path handling is cross-platform: paths are normalized to forward slashes for
 * comparison so the same logic works on Windows, macOS, and Linux.
 */
public final class DiagnosticMapper {

    public static final String SOURCE = "qualimetry-terraform";

    private final RuleRegistry registry = new RuleRegistry();
    private final ToolResultMapper mapper = new ToolResultMapper(registry);

    public List<Diagnostic> mapWorkspaceDiagnostics(String workspaceRoot, String tflintJson, String trivyJson, String checkovJson, URI filterFileUri) {
        List<MappedFinding> all = new ArrayList<>();
        int tflintCount = 0, trivyCount = 0, checkovCount = 0;
        if (tflintJson != null) {
            List<ToolFinding> tflintFindings = TflintOutputParser.parse(tflintJson, workspaceRoot);
            all.addAll(mapper.mapTflint(tflintFindings));
            tflintCount = tflintFindings.size();
        }
        if (trivyJson != null) {
            List<ToolFinding> trivyFindings = TrivyOutputParser.parse(trivyJson, workspaceRoot);
            all.addAll(mapper.mapTrivy(trivyFindings));
            trivyCount = trivyFindings.size();
        }
        if (checkovJson != null) {
            List<ToolFinding> checkovFindings = CheckovOutputParser.parse(checkovJson, workspaceRoot);
            all.addAll(mapper.mapCheckov(checkovFindings));
            checkovCount = checkovFindings.size();
        }
        List<Diagnostic> out = toDiagnostics(all, workspaceRoot, filterFileUri);
        // Operational summary for Output panel / support
        System.err.println("[Terraform LSP] findings: tflint=" + tflintCount + " trivy=" + trivyCount + " checkov=" + checkovCount
            + " mapped=" + all.size() + " for this file=" + out.size());
        System.err.flush();
        return out;
    }

    /**
     * Normalize a path string to forward slashes for cross-platform comparison.
     * Windows uses backslash; macOS and Linux use forward slash. Using a single
     * separator allows the same comparison logic to work on all platforms.
     */
    private static String toForwardSlashes(String path) {
        return path == null ? null : path.replace('\\', '/');
    }

    /**
     * Normalize a file URI to a path string (forward slashes) for comparison.
     * Works with file URIs on Windows (file:///C:/...), macOS (file:///Users/...), and Linux (file:///home/...).
     */
    private static String uriToPathString(URI uri) {
        if (uri == null) return null;
        try {
            return toForwardSlashes(Paths.get(uri).normalize().toAbsolutePath().toString());
        } catch (Exception e) {
            return null;
        }
    }

    private List<Diagnostic> toDiagnostics(List<MappedFinding> findings, String workspaceRoot, URI filterFileUri) {
        List<Diagnostic> out = new ArrayList<>();
        String basePath = toForwardSlashes(workspaceRoot);
        if (basePath == null) basePath = "";
        if (!basePath.isEmpty() && !basePath.endsWith("/")) basePath += "/";
        String filterStr = filterFileUri != null ? uriToPathString(filterFileUri) : null;
        if (filterStr != null) {
            filterStr = filterStr.toLowerCase();
        }
        List<MappedFinding> kept = new ArrayList<>();
        for (MappedFinding f : findings) {
            String relFile = toForwardSlashes(f.getFile());
            if (relFile == null) relFile = "";
            String filePath = basePath + relFile;
            if (filterStr != null) {
                String findingPathStr = null;
                try {
                    findingPathStr = toForwardSlashes(Paths.get(filePath).normalize().toAbsolutePath().toString());
                    if (findingPathStr != null) findingPathStr = findingPathStr.toLowerCase();
                } catch (Exception e) {
                    // ignore
                }
                boolean keep = findingPathStr != null && findingPathStr.equals(filterStr);
                keep = keep || (findingPathStr != null && filterStr.endsWith("/" + relFile.toLowerCase()));
                keep = keep || filterStr.endsWith("/" + relFile.toLowerCase());
                if (!keep) {
                    continue;
                }
            }
            kept.add(f);
        }
        // Use whole-line ranges so the squiggle spans the full line (LSP end is exclusive: line+1,0 = start of next line)
        for (MappedFinding f : kept) {
            int line = Math.max(0, f.getLine() - 1);
            Diagnostic d = new Diagnostic();
            d.setRange(new Range(new Position(line, 0), new Position(line + 1, 0)));
            d.setMessage(f.getMessage());
            String vscodeSev = registry.getVscodeSeverity(f.getRuleKey());
            if (vscodeSev == null) {
                vscodeSev = com.qualimetry.terraform.rules.SeverityTranslator.toVscodeSeverity(f.getSeverity());
            }
            d.setSeverity(toLspSeverity(vscodeSev));
            d.setSource(SOURCE);
            d.setCode(f.getRuleKey());
            out.add(d);
        }
        return out;
    }

    private static DiagnosticSeverity toLspSeverity(String vscodeSeverity) {
        if (vscodeSeverity == null) return DiagnosticSeverity.Information;
        switch (vscodeSeverity) {
            case "Error":
                return DiagnosticSeverity.Error;
            case "Warning":
                return DiagnosticSeverity.Warning;
            case "Hint":
                return DiagnosticSeverity.Hint;
            case "Information":
            default:
                return DiagnosticSeverity.Information;
        }
    }
}

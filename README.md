# Qualimetry Terraform Analyzer - VSCode Plugin

[![CI](https://github.com/Qualimetry/vscode-terraform-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/Qualimetry/vscode-terraform-plugin/actions/workflows/ci.yml)

**Author**: Qualimetry ([qualimetry.com](https://qualimetry.com)) team at SHAZAM Analytics Ltd

A VS Code extension for static analysis of Terraform (HCL) files. It uses the same rule set as the [Qualimetry Terraform Analyzer for SonarQube](https://github.com/Qualimetry/sonarqube-terraform-plugin). The extension reports misconfigurations, security findings, style and convention violations, and correctness errors in the editor.

The SonarQube plugin applies these rules in CI/CD; the extension applies them during editing. Rule keys and default severity match the SonarQube plugin so that teams using both see the same rules in the editor and in the pipeline.

## How it works

The extension starts a **Java LSP server** (bundled as `server/terraform-lsp-server.jar`) when you open a workspace. The server runs **tflint**, **Trivy**, and **checkov** from the **workspace root** and turns their output into LSP diagnostics. The tools run automatically in two cases:

- **When you open** a `.tf` or `.tf.json` file - the extension triggers analysis when a Terraform document is opened (from explorer or **File → Open**) or when you focus a Terraform tab; the server runs all three tools and publishes diagnostics for the workspace (filtered to the current file).
- **When you edit** - a run is scheduled after 600 ms so diagnostics update shortly after you stop typing (debounced).
- **When you save** - the server runs the tools again and refreshes diagnostics.
- **Manual run** - use **Terraform: Run analysis** from the Command Palette (Ctrl+Shift+P) to run analysis for the active `.tf` file.

The tools are not run on every keystroke (only on open and save) to avoid running tflint, Trivy, and checkov on every change. Results refresh on open and save. **Hover** on a line that has a diagnostic to see the full rule description, compliant/non-compliant examples, and a “More information” link.

## Requirements

- **VS Code** 1.85+
- **Platforms:** Windows, macOS, and Linux. Path and file handling are cross-platform (paths normalized for comparison so the same logic works on all OSes).
- **Java 17+** (JAVA_HOME or `java` on PATH) - used to run the embedded LSP server.
- **tflint**, **Trivy**, **checkov** on **PATH** - the server runs these tools to produce diagnostics.

Install the tools on the machine. All three must be on **PATH**:

- **Windows:** **tflint** and **Trivy** via Chocolatey or Scoop (e.g. `choco install tflint trivy`). **checkov** is not on Chocolatey; install with pip: `pip install checkov` (Python 3.9+), then ensure the Scripts folder is on PATH. See [Trivy installation](https://trivy.dev/docs/latest/getting-started/installation/) if needed.
- **macOS:** `brew install tflint trivy`; **checkov**: `pip install checkov` or see [checkov install](https://www.checkov.io/2.Basics/CLI%20Command%20Reference.html).
- **Linux:** Official install scripts or distro packages; ensure **tflint**, **Trivy**, and **checkov** are all on PATH.

## Features

- **Diagnostics** - Issues from tflint, Trivy, and checkov on `.tf` / `.tf.json` files.
- **Hover** - At a diagnostic, hover to see full rule description, compliant/non-compliant examples, and a “More information” link.

## Configuration

- `terraformAnalyzer.enabled` - Enable or disable analysis (default: true).
- `terraformAnalyzer.tflintPath` - Path to tflint (default: `tflint`).
- `terraformAnalyzer.trivyPath` - Path to Trivy (default: `trivy`).
- `terraformAnalyzer.checkovPath` - Path to checkov (default: `checkov`).
- `terraformAnalyzer.rules` - Per-rule options: `enabled`, `severity`. Keys are rule keys (e.g. `qa-tflint-terraform_naming_convention`, `qa-trivy-AWS-0086`). Omit or use `{}` to show all rules with default severity; add entries to override or disable specific rules.
- `terraformAnalyzer.rulesReplaceDefaults` - When `true`, only the rules listed in `terraformAnalyzer.rules` are shown (e.g. after Import from SonarQube). When `false`, listed rules are overrides and unlisted rules use default visibility.

Rules can be overridden (e.g. disable a rule or change severity) by adding the corresponding keys to `terraformAnalyzer.rules`. Replace mode: set `terraformAnalyzer.rulesReplaceDefaults` to `true` and list only the rules to enable; this is typically used after importing a profile from SonarQube.

## Aligning with a SonarQube quality profile

Use the command **Terraform: Import rules from SonarQube** (Command Palette). Enter the SonarQube server URL, the Terraform quality profile name or key (e.g. **Qualimetry Terraform** or **Qualimetry Way**), and a token if the server requires authentication. The extension fetches the profile's active rules and severities from the API and writes them to `terraformAnalyzer.rules` and sets `terraformAnalyzer.rulesReplaceDefaults` to `true`, so only those rules run.

## Build and package

```bash
# From repo root (requires Node 20+, JDK 17, Maven)
npm run package:vsix
# VSIX: vscode-client/qualimetry-vscode-terraform-plugin-*.vsix
```

Or from the VS Code client directory:

```bash
cd vscode-client
npm install
npm run compile
# Copy terraform-lsp-server/target/*-shaded.jar to server/terraform-lsp-server.jar
npx vsce package --no-dependencies
```

Install the `.vsix` in VS Code via **Extensions** → **...** → **Install from VSIX**. In **Cursor** (VS Code based), use the same path: **Extensions** → **...** → **Install from VSIX**.

## Testing the VSIX in Cursor

1. **Build the VSIX** (from repo root):
   ```bash
   npm run package:vsix
   ```
   This builds the LSP server JAR, copies it into `vscode-client/server/`, and produces `vscode-client/qualimetry-vscode-terraform-plugin-*.vsix`.

2. **Prerequisites:** Java 17 (for the server), and **tflint**, **Trivy**, and **checkov** on PATH (or set `terraformAnalyzer.tflintPath`, `terraformAnalyzer.trivyPath`, `terraformAnalyzer.checkovPath` in settings).

3. **Install in Cursor:** **Extensions** (Ctrl+Shift+X) → **...** (Views and More Actions) → **Install from VSIX** → choose `vscode-client/qualimetry-vscode-terraform-plugin-*.vsix`. Reload if prompted.

4. **Open a Terraform folder** (e.g. `its/projects/noncompliant` or any repo with `.tf` files). Open a `.tf` file. After a few seconds the server runs tflint, Trivy, and checkov; diagnostics (squiggles) appear on lines with issues. **Save** the file to run the tools again and refresh diagnostics.

5. **Hover** on a line with a diagnostic to see the rule description. Check **Output** → **Terraform Analyzer** for server logs or errors (e.g. tools not on PATH).

## Troubleshooting: “Nothing happens” when I open a .tf file

- **Open the folder, not just the file.** Use **File → Open Folder** and choose the directory that contains the `.tf` files (e.g. `its/projects/noncompliant`), then open `main.tf`. The extension activates when the workspace contains `**/*.tf` or when the editor language is Terraform/HCL.
- **Check the extension output.** Go to **View → Output**, select **Terraform Analyzer** in the dropdown. Expected messages: “Language server started successfully.” and, when opening or focusing a `.tf` file, “Triggering analysis for file:///...” and “runAnalysis completed.” If you see “runAnalysis failed: …”, the server did not receive the command or returned an error. If you see “No output from any tool”, install **tflint**, **Trivy**, and **checkov** (see above). To force a run, use **Terraform: Run analysis** from the Command Palette (Ctrl+Shift+P) with a `.tf` file active.
- **Diagnostics require the tools on PATH.** The server runs **tflint**, **Trivy**, and **checkov** from the workspace root. If any of these are missing or not on PATH, no diagnostics are shown. Install them and ensure they are on PATH, or set `terraformAnalyzer.tflintPath`, `terraformAnalyzer.trivyPath`, and `terraformAnalyzer.checkovPath` in settings. The output channel does not list which tool failed; if all three fail, no errors are shown but no results are produced.
- **Other Terraform extensions.** If another extension (e.g. HashiCorp Terraform) registers the `.tf` language, the editor may use that. This extension still attaches to files matching `**/*.tf` once it is activated; use **Open Folder** so that `workspaceContains:**/*.tf` activates it.

# Qualimetry Terraform Analyzer - VS Code Extension

[![CI](https://github.com/Qualimetry/vscode-terraform-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/Qualimetry/vscode-terraform-plugin/actions/workflows/ci.yml)

A VS Code extension for real-time static analysis of Terraform files (`.tf`, `.tf.json`) using tflint, Trivy, and checkov.

Powered by the same analysis engine as the [SonarQube plugin](https://github.com/Qualimetry/sonarqube-terraform-plugin) and the [IntelliJ plugin](https://github.com/Qualimetry/intellij-terraform-plugin).

## Features

- **766 rules** from tflint, Trivy, and checkov covering misconfigurations, security, style, and correctness.
- **Real-time diagnostics** — issues appear as you edit, on save, and on file open.
- **Hover descriptions** — hover on a diagnostic to see the full rule description, compliant/non-compliant examples, and a "More information" link.
- **SonarQube import** — align editor rules with a SonarQube quality profile via the Command Palette.
- **Manual analysis** — run **Terraform: Run analysis** from the Command Palette for on-demand checks.

## Rule categories

| Source | Focus | Examples |
|--------|-------|----------|
| tflint | Terraform conventions | Naming conventions, deprecated syntax, provider requirements |
| Trivy | Security misconfigurations | S3 encryption, IAM policies, network security groups |
| checkov | Compliance & best practices | CIS benchmarks, SOC2, HIPAA, PCI-DSS checks |

## How it works

The extension starts a **Java LSP server** (bundled as `server/terraform-lsp-server.jar`) when you open a workspace. The server runs **tflint**, **Trivy**, and **checkov** from the workspace root and turns their output into LSP diagnostics:

- **On open** — analysis runs when a `.tf` or `.tf.json` file is opened or focused.
- **On edit** — a run is scheduled after 600 ms of inactivity (debounced).
- **On save** — tools run again and diagnostics refresh.
- **Manual** — use **Terraform: Run analysis** from the Command Palette (Ctrl+Shift+P).

## Requirements

- **VS Code** 1.85+
- **Platforms:** Windows, macOS, and Linux.
- **Java 17+** (JAVA_HOME or `java` on PATH) — runs the embedded LSP server.
- **tflint**, **Trivy**, **checkov** on **PATH** — the server runs these tools to produce diagnostics.

Install the tools on your machine. All three must be on **PATH**:

- **Windows:** **tflint** and **Trivy** via Chocolatey or Scoop (e.g. `choco install tflint trivy`). **checkov** is not on Chocolatey; install with pip: `pip install checkov` (Python 3.9+), then ensure the Scripts folder is on PATH. See [Trivy installation](https://trivy.dev/docs/latest/getting-started/installation/) if needed.
- **macOS:** `brew install tflint trivy`; **checkov**: `pip install checkov` or see [checkov install](https://www.checkov.io/2.Basics/CLI%20Command%20Reference.html).
- **Linux:** Official install scripts or distro packages; ensure **tflint**, **Trivy**, and **checkov** are all on PATH.

## Installation

Install the `.vsix` in VS Code via **Extensions** → **...** → **Install from VSIX**. In **Cursor** (VS Code-based), use the same path.

To build and package:

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

## Configuration

- `terraformAnalyzer.enabled` — enable or disable analysis (default: `true`).
- `terraformAnalyzer.tflintPath` — path to tflint (default: `tflint`).
- `terraformAnalyzer.trivyPath` — path to Trivy (default: `trivy`).
- `terraformAnalyzer.checkovPath` — path to checkov (default: `checkov`).
- `terraformAnalyzer.rules` — per-rule options: `enabled`, `severity`. Keys are rule keys (e.g. `qa-tflint-terraform_naming_convention`, `qa-trivy-AWS-0086`). Omit or use `{}` to show all rules with default severity; add entries to override or disable specific rules.
- `terraformAnalyzer.rulesReplaceDefaults` — when `true`, only the rules listed in `terraformAnalyzer.rules` are shown. When `false`, listed rules are overrides and unlisted rules use default visibility.

### Aligning with a SonarQube quality profile

Use the command **Terraform: Import rules from SonarQube** (Command Palette). Enter the SonarQube server URL, the Terraform quality profile name or key (e.g. **Qualimetry Terraform** or **Qualimetry Way**), and a token if the server requires authentication. The extension fetches the profile's active rules and severities and writes them to `terraformAnalyzer.rules`, setting `terraformAnalyzer.rulesReplaceDefaults` to `true` so only those rules run.

## Also available

The same analysis engine powers plugins for other platforms:

- **[SonarQube plugin](https://github.com/Qualimetry/sonarqube-terraform-plugin)** — enforce quality gates in CI/CD pipelines.
- **[IntelliJ plugin](https://github.com/Qualimetry/intellij-terraform-plugin)** — real-time analysis in JetBrains IDEs and Qodana CI/CD.

Rule keys and severities align across all three tools so findings are directly comparable.

## Troubleshooting

**"Nothing happens" when I open a .tf file:**

- **Open the folder, not just the file.** Use **File → Open Folder** and choose the directory that contains the `.tf` files. The extension activates when the workspace contains `**/*.tf` or when the editor language is Terraform/HCL.
- **Check the extension output.** Go to **View → Output**, select **Terraform Analyzer** in the dropdown. Expected messages: "Language server started successfully." and "Triggering analysis for file:///..." followed by "runAnalysis completed." If you see errors, check the tool paths.
- **Diagnostics require the tools on PATH.** The server runs tflint, Trivy, and checkov from the workspace root. If any are missing or not on PATH, no diagnostics are shown. Install them or set `terraformAnalyzer.tflintPath`, `terraformAnalyzer.trivyPath`, and `terraformAnalyzer.checkovPath` in settings.
- **Other Terraform extensions.** If another extension (e.g. HashiCorp Terraform) registers the `.tf` language, this extension still attaches to files matching `**/*.tf` once activated; use **Open Folder** so that `workspaceContains:**/*.tf` activates it.

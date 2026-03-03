# Qualimetry Terraform Analyzer

Static analysis for Terraform (HCL) `.tf` and `.tf.json` files. This extension shares the same rule set as the [Qualimetry Terraform Analyzer for SonarQube](https://github.com/Qualimetry/sonarqube-terraform-plugin) and the [Qualimetry Terraform Analyzer for IntelliJ](https://github.com/Qualimetry/intellij-terraform-plugin). It reports misconfigurations, security findings, style and convention violations, and correctness errors. Rule keys and severities match the SonarQube and IntelliJ plugins for consistency between editor and pipeline.

## Requirements

- **VS Code** 1.85+
- **Java 17+** (JAVA_HOME or `java` on PATH) - runs the embedded LSP server.
- **tflint**, **Trivy**, **checkov** on **PATH** - the server runs these tools to produce diagnostics.

Install the tools on the machine (e.g. Windows: `choco install tflint trivy`, `pip install checkov`).

## How it works

The extension starts a Java LSP server when you open a workspace. The server runs **tflint**, **Trivy**, and **checkov** from the workspace root. The tools run automatically when you **open** or **save** a `.tf` or `.tf.json` file (not on every keystroke). **Hover** on a line with a diagnostic to see the full rule description and a “More information” link.

## Configuration

All settings are under `terraformAnalyzer`:

| Setting | Default | Description |
|--------|---------|-------------|
| `terraformAnalyzer.enabled` | `true` | Enable or disable analysis. |
| `terraformAnalyzer.tflintPath` | `tflint` | Path to tflint executable. |
| `terraformAnalyzer.trivyPath` | `trivy` | Path to Trivy executable. |
| `terraformAnalyzer.checkovPath` | `checkov` | Path to checkov executable. |
| `terraformAnalyzer.rules` | `{}` | Per-rule overrides: `enabled`, `severity`. Omit or use `{}` to show all rules with default severity. |
| `terraformAnalyzer.rulesReplaceDefaults` | `false` | When `true`, only rules listed in `terraformAnalyzer.rules` are shown (e.g. after Import from SonarQube). |

## Import rules from SonarQube

Use the command **Terraform: Import rules from SonarQube** (Command Palette). Enter your SonarQube server URL, the Terraform quality profile name (e.g. **Qualimetry Way**), and a token if required. The extension fetches the profile’s active rules and writes them to `terraformAnalyzer.rules`.

## Also available

- **[SonarQube plugin](https://github.com/Qualimetry/sonarqube-terraform-plugin)** — same rules in CI/CD quality gates.
- **[IntelliJ plugin](https://github.com/Qualimetry/intellij-terraform-plugin)** — same rules in JetBrains IDEs and Qodana.

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0). Copyright 2026 SHAZAM Analytics Ltd.

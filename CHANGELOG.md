# Changelog

## [Unreleased]

- (None.)

## [2.2.8] - 2026-06-17

- In-editor diagnostics now link directly to the documentation for each rule.
- Added full-text, per-rule documentation covering what each rule checks, why it matters, and how to fix it, with compliant and non-compliant examples.

## [2.2.5] - 2026-06-11

- Rules now re-sync automatically from the last-used SonarQube server on startup (`terraformAnalyzer.sonar.autoSyncOnStartup`).
- SonarQube tokens are stored securely in VS Code secret storage.

## [2.2.2] - 2026-02-28

- Published to Visual Studio Marketplace and Open VSX.

## [2.2.1] - 2026-02-28

First public release.

- Real-time diagnostics and rich hover for `.tf` / `.tf.json` files.
- **Terraform: Import rules from SonarQube** – pull quality profile and rule severities from a SonarQube server into settings.
- Configurable per-rule severity and enable/disable via `terraformAnalyzer.rules`.
- Requires Java 17+ and tflint, Trivy, checkov on PATH.

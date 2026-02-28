import * as path from 'path';
import * as fs from 'fs';
import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    State,
} from 'vscode-languageclient/node';
import { getConfig, findJavaExecutable, getJavaVersion } from './configuration';
import {
    fetchQualityProfiles,
    fetchActiveRules,
    resolveProfileKey,
    type SonarConfig,
} from './sonarImport';

const MIN_JAVA_VERSION = 17;

let client: LanguageClient | undefined;
let outputChannel: vscode.OutputChannel;

/** Set after client starts; used by runAnalysisManual and file-open/editor-change handlers. */
let runAnalysisForUri: (uri: string) => void = () => {};
let lastRunUri: string | null = null;
let lastRunTime = 0;

const SONAR_LAST_URL_KEY = 'terraform.sonar.lastServerUrl';
const SONAR_LAST_PROFILE_KEY = 'terraform.sonar.lastProfile';

function getRuleKeyFromDiagnostic(d: vscode.Diagnostic): string | undefined {
    const code = d.code;
    if (typeof code === 'string') {
        return code;
    }
    if (typeof code === 'object' && code !== null && 'value' in code) {
        return String((code as { value: string }).value);
    }
    return undefined;
}

function severityFromSonar(severity: string): vscode.DiagnosticSeverity {
    const s = (severity || '').toLowerCase();
    if (s === 'blocker' || s === 'critical') {
        return vscode.DiagnosticSeverity.Error;
    }
    if (s === 'major') {
        return vscode.DiagnosticSeverity.Error;
    }
    if (s === 'minor') {
        return vscode.DiagnosticSeverity.Warning;
    }
    if (s === 'info') {
        return vscode.DiagnosticSeverity.Information;
    }
    return vscode.DiagnosticSeverity.Warning;
}

function applyRulesFilter(
    diagnostics: vscode.Diagnostic[],
    rules: Record<string, { enabled?: boolean; severity?: string; [k: string]: unknown }>,
    rulesReplaceDefaults: boolean
): vscode.Diagnostic[] {
    const result: vscode.Diagnostic[] = [];
    for (const d of diagnostics) {
        const ruleKey = getRuleKeyFromDiagnostic(d);
        if (!ruleKey) {
            if (!rulesReplaceDefaults) {
                result.push(d);
            }
            continue;
        }
        const entry = rules[ruleKey];
        if (rulesReplaceDefaults) {
            if (!entry) {
                continue;
            }
            if (entry.enabled === false) {
                continue;
            }
            const diag = new vscode.Diagnostic(d.range, d.message, d.severity);
            diag.source = d.source;
            diag.code = d.code;
            if (entry.severity) {
                diag.severity = severityFromSonar(entry.severity);
            } else {
                diag.severity = d.severity;
            }
            result.push(diag);
        } else {
            if (entry?.enabled === false) {
                continue;
            }
            if (entry?.severity) {
                const diag = new vscode.Diagnostic(d.range, d.message, severityFromSonar(entry.severity));
                diag.source = d.source;
                diag.code = d.code;
                result.push(diag);
            } else {
                result.push(d);
            }
        }
    }
    return result;
}

export async function activate(context: vscode.ExtensionContext): Promise<void> {
    outputChannel = vscode.window.createOutputChannel('Terraform Analyzer');
    context.subscriptions.push(outputChannel);
    outputChannel.appendLine('Terraform Analyzer: activating...');

    context.subscriptions.push(
        vscode.commands.registerCommand('terraform.importRulesFromSonarQube', () =>
            importRulesFromSonarQube(context)
        )
    );
    context.subscriptions.push(
        vscode.commands.registerCommand('terraform.runAnalysisManual', () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor?.document) {
                vscode.window.showInformationMessage('Open a .tf or .tf.json file first.');
                return;
            }
            const fn = editor.document.fileName.toLowerCase();
            if (!fn.endsWith('.tf') && !fn.endsWith('.tf.json')) {
                vscode.window.showInformationMessage('Active file is not a Terraform file (.tf / .tf.json).');
                return;
            }
            if (client) {
                runAnalysisForUri(editor.document.uri.toString());
            } else {
                vscode.window.showWarningMessage('Terraform Analyzer language server is not ready.');
            }
        })
    );

    const config = getConfig();
    if (!config.enabled) {
        outputChannel.appendLine('Terraform Analyzer is disabled via settings.');
        return;
    }

    outputChannel.appendLine('Looking for Java...');
    const javaPath = findJavaExecutable(config.javaHome);
    if (!javaPath) {
        const msg = 'Terraform Analyzer: Java 17+ is required. Set "terraformAnalyzer.java.home" or JAVA_HOME.';
        outputChannel.appendLine(msg);
        vscode.window.showErrorMessage(msg);
        return;
    }
    outputChannel.appendLine(`Java: ${javaPath}`);

    const version = getJavaVersion(javaPath);
    outputChannel.appendLine(`Java version: ${version ?? 'unknown'}`);
    if (version === undefined) {
        const msg = 'Terraform Analyzer: Could not detect Java version. The language server requires Java 17+. Set "terraformAnalyzer.java.home" to a JDK 17+ installation.';
        outputChannel.appendLine(msg);
        vscode.window.showErrorMessage(msg);
        return;
    }
    if (version < MIN_JAVA_VERSION) {
        const msg = `Terraform Analyzer: Java ${MIN_JAVA_VERSION}+ is required (server is built with Java 17). Found Java ${version} at "${javaPath}". Set "terraformAnalyzer.java.home" to a JDK 17+ installation.`;
        outputChannel.appendLine(msg);
        vscode.window.showErrorMessage(msg);
        return;
    }

    const serverJar = path.join(context.extensionPath, 'server', 'terraform-lsp-server.jar');
    if (!fs.existsSync(serverJar)) {
        const msg = 'Terraform Analyzer: Server JAR not found at ' + serverJar;
        outputChannel.appendLine(msg);
        vscode.window.showErrorMessage(msg);
        return;
    }
    outputChannel.appendLine(`Server JAR: ${serverJar}`);

    const serverOptions: ServerOptions = {
        command: javaPath,
        args: ['-jar', serverJar],
        options: { env: process.env },
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [
            { language: 'terraform', scheme: 'file' },
            { language: 'hcl', scheme: 'file' },
            { scheme: 'file', pattern: '**/*.tf' },
            { scheme: 'file', pattern: '**/*.tf.json' },
        ],
        synchronize: {
            configurationSection: 'terraformAnalyzer',
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.tf'),
        },
        initializationOptions: {
            tflintPath: config.tflintPath,
            trivyPath: config.trivyPath,
            checkovPath: config.checkovPath,
        },
        middleware: {
            handleDiagnostics: (uri, diagnostics, next) => {
                const cfg = getConfig();
                const filtered = applyRulesFilter(
                    diagnostics,
                    cfg.rules,
                    cfg.rulesReplaceDefaults
                );
                next(uri, filtered);
            },
        },
        outputChannel,
    };

    client = new LanguageClient(
        'terraformAnalyzer',
        'Terraform Analyzer',
        serverOptions,
        clientOptions
    );

    const statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left);
    statusBar.text = '$(checklist) Terraform Analyzer';
    statusBar.tooltip = 'Terraform Analyzer is active';
    statusBar.show();
    context.subscriptions.push(statusBar);

    outputChannel.appendLine('Starting Terraform language server...');
    try {
        await client.start();
        outputChannel.appendLine('Language server started successfully.');
        outputChannel.appendLine('Open a .tf file — server output and findings will appear below.');
        outputChannel.show();
        context.subscriptions.push({
            dispose: () => {
                if (client?.state === State.Running) {
                    client.stop().catch(() => { /* ignore when disposing */ });
                }
            },
        });

        runAnalysisForUri = (uri: string) => {
            if (!client || !uri) return;
            const u = uri.toLowerCase();
            if (!u.endsWith('.tf') && !u.endsWith('.tf.json')) return;
            const now = Date.now();
            if (lastRunUri === uri && now - lastRunTime < 2000) {
                return;
            }
            lastRunUri = uri;
            lastRunTime = now;
            outputChannel.appendLine(`Triggering analysis for ${uri}`);
            client.sendRequest('workspace/executeCommand', {
                command: 'terraform.runAnalysis',
                arguments: [uri],
            }).then(() => {
                outputChannel.appendLine('runAnalysis completed.');
            }).catch((err: unknown) => {
                outputChannel.appendLine('runAnalysis failed: ' + (err instanceof Error ? err.message : String(err)));
            });
        };

        // When a .tf file is opened (from explorer, File > Open, etc.), trigger analysis
        context.subscriptions.push(
            vscode.workspace.onDidOpenTextDocument((doc) => {
                if (!client || doc.uri.scheme !== 'file') return;
                const fn = doc.fileName.toLowerCase();
                if (!fn.endsWith('.tf') && !fn.endsWith('.tf.json')) return;
                const uri = doc.uri.toString();
                setTimeout(() => runAnalysisForUri(uri), 100);
            })
        );

        // When user focuses a .tf file (e.g. switches tab), trigger analysis
        context.subscriptions.push(
            vscode.window.onDidChangeActiveTextEditor((editor) => {
                if (!client || !editor?.document) return;
                const doc = editor.document;
                const fileName = doc.fileName.toLowerCase();
                if (fileName.endsWith('.tf') || fileName.endsWith('.tf.json')) {
                    runAnalysisForUri(doc.uri.toString());
                }
            })
        );

        // Trigger for current .tf editor after delays (in case .tf was already open when window loaded)
        const triggerForActiveEditor = () => {
            const active = vscode.window.activeTextEditor;
            if (active?.document && client) {
                const fn = active.document.fileName.toLowerCase();
                if (fn.endsWith('.tf') || fn.endsWith('.tf.json')) {
                    runAnalysisForUri(active.document.uri.toString());
                }
            }
        };
        setTimeout(triggerForActiveEditor, 400);
        setTimeout(triggerForActiveEditor, 1500);
    } catch (e) {
        const msg = 'Failed to start: ' + (e instanceof Error ? e.message : String(e));
        outputChannel.appendLine(msg);
        vscode.window.showErrorMessage('Terraform Analyzer: failed to start language server.');
        return;
    }
}

async function importRulesFromSonarQube(context: vscode.ExtensionContext): Promise<void> {
    const lastUrl = context.globalState.get<string>(SONAR_LAST_URL_KEY) ?? '';
    const lastProfile = context.globalState.get<string>(SONAR_LAST_PROFILE_KEY) ?? '';

    const serverUrl = await vscode.window.showInputBox({
        title: 'SonarQube server URL',
        prompt: 'e.g. https://sonar.mycompany.com or https://myorg.sonarcloud.io',
        value: lastUrl,
        placeHolder: 'https://',
        ignoreFocusOut: true,
        validateInput: (v) => {
            const s = (v ?? '').trim();
            if (!s) return 'URL is required';
            if (!/^https?:\/\//i.test(s) && !/^[a-zA-Z0-9.-]+/.test(s)) return 'Enter a valid URL';
            return undefined;
        },
    });
    if (serverUrl === undefined) return;
    await context.globalState.update(SONAR_LAST_URL_KEY, serverUrl.trim());

    const profileNameOrKey = await vscode.window.showInputBox({
        title: 'Terraform quality profile',
        prompt: 'Profile name or key (e.g. "Qualimetry Terraform" or "Qualimetry Way")',
        value: lastProfile,
        placeHolder: 'Qualimetry Terraform',
        ignoreFocusOut: true,
    });
    if (profileNameOrKey === undefined) return;
    await context.globalState.update(SONAR_LAST_PROFILE_KEY, profileNameOrKey.trim());

    const token = await vscode.window.showInputBox({
        title: 'SonarQube token (optional)',
        prompt: 'Paste token if the server requires authentication. URL and profile are saved for next time.',
        password: true,
        ignoreFocusOut: true,
    });
    if (token === undefined) return;

    const sonarConfig: SonarConfig = {
        serverUrl: serverUrl.trim(),
        profileNameOrKey: profileNameOrKey.trim(),
        token: (token ?? '').trim() || undefined,
    };

    let result: { count: number; targetLabel: string } | undefined;
    await vscode.window.withProgress(
        {
            location: vscode.ProgressLocation.Notification,
            title: 'Importing rules from SonarQube',
            cancellable: false,
        },
        async () => {
            try {
                const profiles = await fetchQualityProfiles(sonarConfig);
                if (profiles.length === 0) {
                    vscode.window.showErrorMessage(
                        'No Terraform quality profiles found on this SonarQube server.'
                    );
                    return;
                }
                const profileKey = resolveProfileKey(profiles, sonarConfig.profileNameOrKey);
                if (!profileKey) {
                    vscode.window.showErrorMessage(
                        `No matching Terraform profile for "${sonarConfig.profileNameOrKey}". ` +
                        `Available: ${profiles.map((p) => p.name).join(', ')}`
                    );
                    return;
                }
                const rules = await fetchActiveRules(sonarConfig, profileKey);
                if (Object.keys(rules).length === 0) {
                    vscode.window.showErrorMessage(
                        'No active rules found in the selected profile (or profile is not for Qualimetry Terraform).'
                    );
                    return;
                }
                const hasWorkspace = (vscode.workspace.workspaceFolders?.length ?? 0) > 0;
                const configTarget = hasWorkspace
                    ? vscode.ConfigurationTarget.Workspace
                    : vscode.ConfigurationTarget.Global;
                const targetLabel = hasWorkspace ? 'workspace' : 'user';
                const cfg = vscode.workspace.getConfiguration('terraformAnalyzer', null);
                await cfg.update('rules', rules, configTarget);
                await cfg.update('rulesReplaceDefaults', true, configTarget);
                await context.globalState.update(SONAR_LAST_URL_KEY, sonarConfig.serverUrl);
                await context.globalState.update(SONAR_LAST_PROFILE_KEY, sonarConfig.profileNameOrKey);
                result = { count: Object.keys(rules).length, targetLabel };
            } catch (err) {
                const message = err instanceof Error ? err.message : String(err);
                outputChannel.appendLine(`Import from SonarQube failed: ${message}`);
                vscode.window.showErrorMessage(`Import from SonarQube failed: ${message}`);
            }
        }
    );
    if (result) {
        const location = result.targetLabel === 'workspace'
            ? 'workspace settings (.vscode/settings.json)'
            : 'user settings (global settings.json)';
        vscode.window.showInformationMessage(
            `Imported ${result.count} rule${result.count === 1 ? '' : 's'} from SonarQube into ${location}.`
        );
    }
}

export function deactivate(): Promise<void> {
    if (!client) return Promise.resolve();
    if (client.state !== State.Running) {
        client = undefined;
        return Promise.resolve();
    }
    const c = client;
    client = undefined;
    try {
        return c.stop().catch(() => { /* avoid uncaught rejection */ });
    } catch {
        return Promise.resolve();
    }
}

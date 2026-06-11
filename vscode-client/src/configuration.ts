import * as path from 'path';
import * as fs from 'fs';
import * as vscode from 'vscode';
import { execSync } from 'child_process';

export interface RuleEntry {
    enabled?: boolean;
    severity?: string;
    [k: string]: unknown;
}

export interface TerraformAnalyzerConfig {
    enabled: boolean;
    javaHome: string;
    tflintPath: string;
    trivyPath: string;
    checkovPath: string;
    rules: Record<string, RuleEntry>;
    rulesReplaceDefaults: boolean;
}

export function getConfig(): TerraformAnalyzerConfig {
    const cfg = vscode.workspace.getConfiguration('terraformAnalyzer');
    return {
        enabled: cfg.get<boolean>('enabled', true),
        javaHome: cfg.get<string>('java.home', ''),
        tflintPath: cfg.get<string>('tflintPath', 'tflint'),
        trivyPath: cfg.get<string>('trivyPath', 'trivy'),
        checkovPath: cfg.get<string>('checkovPath', 'checkov'),
        rules: cfg.get<Record<string, RuleEntry>>('rules', {}),
        rulesReplaceDefaults: cfg.get<boolean>('rulesReplaceDefaults', false),
    };
}

export function findJavaExecutable(configuredPath?: string): string | undefined {
    const ext = process.platform === 'win32' ? '.exe' : '';
    if (configuredPath) {
        const candidate = path.join(configuredPath, 'bin', `java${ext}`);
        if (fs.existsSync(candidate)) return candidate;
        if (fs.existsSync(configuredPath) && (configuredPath.endsWith('java') || configuredPath.endsWith('java.exe'))) return configuredPath;
    }
    const javaHome = process.env['JAVA_HOME'];
    if (javaHome) {
        const candidate = path.join(javaHome, 'bin', `java${ext}`);
        if (fs.existsSync(candidate)) return candidate;
    }
    try {
        const cmd = process.platform === 'win32' ? 'where java' : 'which java';
        const result = execSync(cmd, { encoding: 'utf8', timeout: 5000 }).trim();
        const firstLine = result.split(/\r?\n/)[0];
        if (firstLine && fs.existsSync(firstLine)) return firstLine;
    } catch {
        // ignore
    }
    return undefined;
}

export function getJavaVersion(javaExe: string): number | undefined {
    try {
        const output = execSync(`"${javaExe}" -version 2>&1`, {
            encoding: 'utf8',
            timeout: 10000,
        });
        return parseJavaVersion(output || '');
    } catch (err: unknown) {
        if (err && typeof err === 'object' && 'stderr' in err) {
            const stderr = (err as { stderr: string }).stderr;
            if (stderr) return parseJavaVersion(stderr);
        }
        return undefined;
    }
}

function parseJavaVersion(output: string): number | undefined {
    const match = output.match(/version\s+"(\d+)(?:\.(\d+))?/);
    if (!match) return undefined;
    const major = parseInt(match[1], 10);
    if (major === 1 && match[2]) return parseInt(match[2], 10);
    return major;
}

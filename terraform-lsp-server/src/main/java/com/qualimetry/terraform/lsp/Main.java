package com.qualimetry.terraform.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Entry point for the Terraform LSP server. Listens on stdin/stdout for LSP messages.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        TerraformLanguageServer server = new TerraformLanguageServer();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
                server,
                System.in,
                System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
    }
}

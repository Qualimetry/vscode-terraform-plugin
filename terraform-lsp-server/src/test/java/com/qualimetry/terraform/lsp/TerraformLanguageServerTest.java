/*
 * Copyright 2026 SHAZAM Analytics Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qualimetry.terraform.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerraformLanguageServerTest {

    private TerraformLanguageServer server;

    @BeforeEach
    void setUp() {
        server = new TerraformLanguageServer();
        server.connect(mock(LanguageClient.class));
    }

    @AfterEach
    void tearDown() {
        server.exit();
    }

    @Test
    void initializeReturnsCapabilities() throws Exception {
        InitializeParams params = new InitializeParams();
        CompletableFuture<InitializeResult> future = server.initialize(params);
        InitializeResult result = future.get();

        assertThat(result).isNotNull();
        ServerCapabilities caps = result.getCapabilities();
        assertThat(caps).isNotNull();
    }

    @Test
    void textDocumentSyncIsFull() throws Exception {
        InitializeParams params = new InitializeParams();
        InitializeResult result = server.initialize(params).get();

        ServerCapabilities caps = result.getCapabilities();
        assertThat(caps.getTextDocumentSync().getLeft()).isEqualTo(TextDocumentSyncKind.Full);
    }

    @Test
    void hoverProviderEnabled() throws Exception {
        InitializeParams params = new InitializeParams();
        InitializeResult result = server.initialize(params).get();

        ServerCapabilities caps = result.getCapabilities();
        assertThat(caps.getHoverProvider().getLeft()).isTrue();
    }

    @Test
    void executeCommandProviderRegistered() throws Exception {
        InitializeParams params = new InitializeParams();
        InitializeResult result = server.initialize(params).get();

        ServerCapabilities caps = result.getCapabilities();
        assertThat(caps.getExecuteCommandProvider()).isNotNull();
        assertThat(caps.getExecuteCommandProvider().getCommands()).contains("terraform.runAnalysis");
    }

    @Test
    void getTextDocumentServiceReturnsNonNull() {
        assertThat(server.getTextDocumentService()).isNotNull();
        assertThat(server.getTextDocumentService()).isInstanceOf(TerraformTextDocumentService.class);
    }

    @Test
    void getWorkspaceServiceReturnsNonNull() {
        assertThat(server.getWorkspaceService()).isNotNull();
    }

    @Test
    void shutdownReturnsCompletedFuture() throws Exception {
        Object result = server.shutdown().get();
        assertThat(result).isNull();
    }

    @Test
    void getDiagnosticsForUnknownUriReturnsEmptyList() {
        assertThat(server.getDiagnosticsForUri("file:///unknown.tf")).isEmpty();
    }

    @Test
    void getDiagnosticsForNullUriReturnsEmptyList() {
        assertThat(server.getDiagnosticsForUri(null)).isEmpty();
    }
}

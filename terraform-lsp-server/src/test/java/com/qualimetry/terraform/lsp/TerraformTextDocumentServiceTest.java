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
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerraformTextDocumentServiceTest {

    private TerraformLanguageServer server;
    private TerraformTextDocumentService service;
    private LanguageClient mockClient;

    @BeforeEach
    void setUp() {
        server = new TerraformLanguageServer();
        mockClient = mock(LanguageClient.class);
        server.connect(mockClient);
        service = server.getTextDocumentService();
    }

    @AfterEach
    void tearDown() {
        server.exit();
    }

    @Test
    void hoverReturnsNullWhenNoDiagnosticsExist() throws Exception {
        HoverParams params = new HoverParams();
        params.setTextDocument(new TextDocumentIdentifier("file:///test.tf"));
        params.setPosition(new Position(0, 0));

        CompletableFuture<Hover> result = service.hover(params);
        assertThat(result.get()).isNull();
    }

    @Test
    void didCloseClearsDiagnostics() {
        DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
        params.setTextDocument(new TextDocumentIdentifier("file:///test.tf"));

        service.didClose(params);

        verify(mockClient).publishDiagnostics(argThat(p ->
                "file:///test.tf".equals(p.getUri()) && p.getDiagnostics().isEmpty()));
    }

    @Test
    void hoverOnLineWithNoDiagnosticReturnsNull() throws Exception {
        HoverParams params = new HoverParams();
        params.setTextDocument(new TextDocumentIdentifier("file:///some/path/main.tf"));
        params.setPosition(new Position(10, 5));

        CompletableFuture<Hover> result = service.hover(params);
        assertThat(result.get()).isNull();
    }
}

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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolRunnerTest {

    private static final String BOGUS_EXE = "/nonexistent/path/tool_xyz_999";

    @TempDir
    Path tempDir;

    @Test
    void runTflintWithNonExistentToolReturnsNotFound() {
        ToolRunResult result = ToolRunner.runTflint(tempDir.toString(), BOGUS_EXE);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ToolRunResult.NOT_FOUND);
    }

    @Test
    void runTrivyWithNonExistentToolReturnsNotFound() {
        ToolRunResult result = ToolRunner.runTrivy(tempDir.toString(), BOGUS_EXE);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ToolRunResult.NOT_FOUND);
    }

    @Test
    void runCheckovWithNonExistentToolReturnsNotFound() {
        ToolRunResult result = ToolRunner.runCheckov(tempDir.toString(), BOGUS_EXE);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ToolRunResult.NOT_FOUND);
    }

    @Test
    void runTflintWithBlankExecutableDoesNotThrow() {
        ToolRunResult result = ToolRunner.runTflint(tempDir.toString(), "   ");
        assertThat(result).isNotNull();
        if (!result.isSuccess()) {
            assertThat(result.failureReason()).isNotBlank();
        }
    }

    @Test
    void runTflintWithNullExecutableDoesNotThrow() {
        ToolRunResult result = ToolRunner.runTflint(tempDir.toString(), null);
        assertThat(result).isNotNull();
        if (!result.isSuccess()) {
            assertThat(result.failureReason()).isNotBlank();
        }
    }

    @Test
    void runTrivyWithNullExecutableDoesNotThrow() {
        ToolRunResult result = ToolRunner.runTrivy(tempDir.toString(), null);
        assertThat(result).isNotNull();
        if (!result.isSuccess()) {
            assertThat(result.failureReason()).isNotBlank();
        }
    }

    @Test
    void runCheckovWithNullExecutableDoesNotThrow() {
        ToolRunResult result = ToolRunner.runCheckov(tempDir.toString(), null);
        assertThat(result).isNotNull();
        if (!result.isSuccess()) {
            assertThat(result.failureReason()).isNotBlank();
        }
    }

    @Test
    void extractJsonFromOutputExtractsFromFirstBrace() throws Exception {
        String result = invokeExtractJson("some log line\n{\"key\": \"value\"}");
        assertThat(result).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void extractJsonFromOutputHandlesPrefixedBanner() throws Exception {
        String input = "INFO: Loading config\nWARNING: stuff\n{\"results\": []}";
        String result = invokeExtractJson(input);
        assertThat(result).isEqualTo("{\"results\": []}");
    }

    @Test
    void extractJsonFromOutputKeepsEverythingAfterFirstBrace() throws Exception {
        String input = "banner\n{\"a\": 1}\ntrailing text";
        String result = invokeExtractJson(input);
        assertThat(result).isEqualTo("{\"a\": 1}\ntrailing text");
    }

    @Test
    void extractJsonFromOutputReturnsNullForNoJson() throws Exception {
        String result = invokeExtractJson("no json here at all");
        assertThat(result).isNull();
    }

    @Test
    void extractJsonFromOutputReturnsNullForNull() throws Exception {
        String result = invokeExtractJson(null);
        assertThat(result).isNull();
    }

    @Test
    void extractJsonFromOutputReturnsNullForEmpty() throws Exception {
        String result = invokeExtractJson("");
        assertThat(result).isNull();
    }

    @Test
    void extractJsonFromOutputHandlesBraceAtStart() throws Exception {
        String result = invokeExtractJson("{\"immediate\": true}");
        assertThat(result).isEqualTo("{\"immediate\": true}");
    }

    @Test
    void utilityClassConstructorIsPrivate() throws Exception {
        Constructor<ToolRunner> ctor = ToolRunner.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers())).isTrue();
        ctor.setAccessible(true);
        try {
            ctor.newInstance();
        } catch (InvocationTargetException e) {
            // acceptable if constructor throws
        }
    }

    @Test
    void failureResultIsNeverNull() {
        ToolRunResult result = ToolRunner.runTflint(tempDir.toString(), BOGUS_EXE);
        assertThat(result).isNotNull();
        assertThat(result.failureReason()).isNotNull();
        assertThat(result.output()).isNull();
    }

    private static String invokeExtractJson(String input) throws Exception {
        Method method = ToolRunner.class.getDeclaredMethod("extractJsonFromOutput", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, input);
    }
}

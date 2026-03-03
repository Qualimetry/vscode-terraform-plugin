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

import org.junit.jupiter.api.Test;

class ToolRunResultTest {

    @Test
    void successFactoryStoresOutput() {
        ToolRunResult result = ToolRunResult.success("{\"ok\": true}");
        assertThat(result.output()).isEqualTo("{\"ok\": true}");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void successIsSuccessReturnsTrue() {
        ToolRunResult result = ToolRunResult.success("output");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void failureFactoryStoresReason() {
        ToolRunResult result = ToolRunResult.failure(ToolRunResult.NOT_FOUND);
        assertThat(result.output()).isNull();
        assertThat(result.failureReason()).isEqualTo(ToolRunResult.NOT_FOUND);
    }

    @Test
    void failureIsSuccessReturnsFalse() {
        ToolRunResult result = ToolRunResult.failure(ToolRunResult.TIMEOUT);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void nullOutputIsNotSuccess() {
        ToolRunResult result = new ToolRunResult(null, null);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void getFailureMessageNotFound() {
        ToolRunResult result = ToolRunResult.failure(ToolRunResult.NOT_FOUND);
        assertThat(result.getFailureMessage()).isEqualTo("not found on PATH");
    }

    @Test
    void getFailureMessageTimeout() {
        ToolRunResult result = ToolRunResult.failure(ToolRunResult.TIMEOUT);
        assertThat(result.getFailureMessage()).isEqualTo("timed out");
    }

    @Test
    void getFailureMessageNonZeroExit() {
        ToolRunResult result = ToolRunResult.failure(ToolRunResult.NON_ZERO_EXIT);
        assertThat(result.getFailureMessage()).isEqualTo("exited with error");
    }

    @Test
    void getFailureMessageNoJson() {
        ToolRunResult result = ToolRunResult.failure(ToolRunResult.NO_JSON);
        assertThat(result.getFailureMessage()).isEqualTo("no JSON output");
    }

    @Test
    void getFailureMessageOther() {
        ToolRunResult result = ToolRunResult.failure(ToolRunResult.OTHER);
        assertThat(result.getFailureMessage()).isEqualTo("failed");
    }

    @Test
    void getFailureMessageUnknownReasonReturnsFailed() {
        ToolRunResult result = ToolRunResult.failure("SOMETHING_ELSE");
        assertThat(result.getFailureMessage()).isEqualTo("failed");
    }

    @Test
    void getFailureMessageNullWhenSuccess() {
        ToolRunResult result = ToolRunResult.success("data");
        assertThat(result.getFailureMessage()).isNull();
    }

    @Test
    void constants() {
        assertThat(ToolRunResult.NOT_FOUND).isEqualTo("NOT_FOUND");
        assertThat(ToolRunResult.TIMEOUT).isEqualTo("TIMEOUT");
        assertThat(ToolRunResult.NON_ZERO_EXIT).isEqualTo("NON_ZERO_EXIT");
        assertThat(ToolRunResult.NO_JSON).isEqualTo("NO_JSON");
        assertThat(ToolRunResult.OTHER).isEqualTo("OTHER");
    }
}

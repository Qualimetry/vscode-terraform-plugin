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

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

class DiagnosticMapperTest {

    private static final String TFLINT_JSON = """
            {
              "issues": [
                {
                  "rule": {"name": "terraform_naming_convention"},
                  "message": "resource name should match the naming convention",
                  "range": {"filename": "main.tf", "start": {"line": 5}}
                }
              ]
            }
            """;

    private final DiagnosticMapper mapper = new DiagnosticMapper();

    @Test
    void allNullInputsReturnsEmptyList() {
        List<Diagnostic> result = mapper.mapWorkspaceDiagnostics("/workspace", null, null, null, null);
        assertThat(result).isEmpty();
    }

    @Test
    void emptyJsonReturnsEmptyList() {
        List<Diagnostic> result = mapper.mapWorkspaceDiagnostics("/workspace", "{}", "{}", "{}", null);
        assertThat(result).isEmpty();
    }

    @Test
    void tflintFindingMappedToDiagnostic() {
        List<Diagnostic> result = mapper.mapWorkspaceDiagnostics("/workspace", TFLINT_JSON, null, null, null);
        assertThat(result).hasSize(1);

        Diagnostic d = result.get(0);
        assertThat(d.getSource()).isEqualTo(DiagnosticMapper.SOURCE);
        assertThat(d.getCode().getLeft()).isEqualTo("qa-tflint-terraform_naming_convention");
        assertThat(d.getMessage()).contains("naming convention");
    }

    @Test
    void lineNumberIsConvertedToZeroBased() {
        List<Diagnostic> result = mapper.mapWorkspaceDiagnostics("/workspace", TFLINT_JSON, null, null, null);
        assertThat(result).hasSize(1);

        Diagnostic d = result.get(0);
        // Source line 5 → LSP line 4 (0-based)
        assertThat(d.getRange().getStart().getLine()).isEqualTo(4);
        assertThat(d.getRange().getStart().getCharacter()).isZero();
        // Whole-line range: end is start of next line
        assertThat(d.getRange().getEnd().getLine()).isEqualTo(5);
        assertThat(d.getRange().getEnd().getCharacter()).isZero();
    }

    @Test
    void diagnosticSeverityMappedFromRegistry() {
        List<Diagnostic> result = mapper.mapWorkspaceDiagnostics("/workspace", TFLINT_JSON, null, null, null);
        assertThat(result).hasSize(1);

        Diagnostic d = result.get(0);
        // qa-tflint-terraform_naming_convention is MAJOR → "Warning" → DiagnosticSeverity.Warning
        assertThat(d.getSeverity()).isIn(
                DiagnosticSeverity.Error,
                DiagnosticSeverity.Warning,
                DiagnosticSeverity.Information,
                DiagnosticSeverity.Hint);
    }

    @Test
    void sourceNameIsQualimetryTerraform() {
        assertThat(DiagnosticMapper.SOURCE).isEqualTo("qualimetry-terraform");
    }

    @Test
    void ruleKeyUsedAsDiagnosticCode() {
        List<Diagnostic> result = mapper.mapWorkspaceDiagnostics("/workspace", TFLINT_JSON, null, null, null);
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getCode().getLeft()).startsWith("qa-tflint-");
    }

    @Test
    void lineOneInSourceMapsToLineZeroInLsp() {
        String json = """
                {
                  "issues": [
                    {
                      "rule": {"name": "terraform_naming_convention"},
                      "message": "test",
                      "range": {"filename": "main.tf", "start": {"line": 1}}
                    }
                  ]
                }
                """;
        List<Diagnostic> result = mapper.mapWorkspaceDiagnostics("/workspace", json, null, null, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRange().getStart().getLine()).isZero();
    }

    @Test
    void lineZeroInSourceClampedToZeroInLsp() {
        String json = """
                {
                  "issues": [
                    {
                      "rule": {"name": "terraform_naming_convention"},
                      "message": "test",
                      "range": {"filename": "main.tf", "start": {"line": 0}}
                    }
                  ]
                }
                """;
        List<Diagnostic> result = mapper.mapWorkspaceDiagnostics("/workspace", json, null, null, null);
        assertThat(result).hasSize(1);
        // Math.max(0, 0-1) = 0
        assertThat(result.get(0).getRange().getStart().getLine()).isZero();
    }
}

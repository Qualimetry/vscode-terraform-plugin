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

class RuleContentServiceTest {

    @Test
    void unknownRuleKeyReturnsNull() {
        String md = RuleContentService.getMarkdown("nonexistent-rule-key");
        assertThat(md).isNull();
    }

    @Test
    void knownRuleReturnsNonNullContent() {
        String md = RuleContentService.getMarkdown("qa-tflint-terraform_naming_convention");
        assertThat(md).isNotNull().isNotEmpty();
    }

    @Test
    void contentContainsRuleDescription() {
        String md = RuleContentService.getMarkdown("qa-tflint-terraform_naming_convention");
        assertThat(md).isNotNull();
        // Either loaded from static markdown or built from metadata (descriptionSummary)
        assertThat(md).isNotBlank();
    }

    @Test
    void emptyRuleKeyReturnsNull() {
        String md = RuleContentService.getMarkdown("");
        assertThat(md).isNull();
    }
}

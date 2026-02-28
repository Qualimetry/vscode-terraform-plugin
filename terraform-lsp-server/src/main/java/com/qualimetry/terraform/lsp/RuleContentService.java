package com.qualimetry.terraform.lsp;

import com.qualimetry.terraform.rules.DescriptionLoader;
import com.qualimetry.terraform.rules.RuleDescriptionBuilder;
import com.qualimetry.terraform.rules.RuleRegistry;

/**
 * Returns markdown content for a rule key (for LSP hover). Loads from terraform-rules resources;
 * when no static file exists, builds from rule metadata (descriptionSummary + docUrl).
 */
public final class RuleContentService {

    private RuleContentService() {
    }

    /**
     * Load markdown description for the rule. Returns static file content if present,
     * otherwise builds from rule metadata. Returns null only if rule is unknown.
     */
    public static String getMarkdown(String ruleKey) {
        String md = DescriptionLoader.loadMarkdown(ruleKey);
        if (md != null && !md.isEmpty()) return md;
        return new RuleRegistry().getRule(ruleKey)
                .map(RuleDescriptionBuilder::buildMarkdown)
                .orElse(null);
    }
}

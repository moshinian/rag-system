package com.example.rag.service.agent;

import com.example.rag.model.enums.AgentActionRiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 推荐动作白名单测试。 */
class RecommendedActionCatalogTest {
    @Test
    void catalogShouldExposeConfirmationActionsButNotMcpTools() {
        RecommendedActionCatalog catalog = new RecommendedActionCatalog();

        assertThat(catalog.isExecutable("embedding.rebuild.submit", AgentActionRiskLevel.MEDIUM)).isTrue();
        assertThat(catalog.isExecutable("document.indexing_task.retry", AgentActionRiskLevel.MEDIUM)).isTrue();
        assertThat(catalog.isExecutable("kb.readiness.check", AgentActionRiskLevel.LOW)).isFalse();
    }
}

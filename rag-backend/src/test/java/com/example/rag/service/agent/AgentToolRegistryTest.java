package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.dto.AgentToolContext;
import com.example.rag.model.dto.AgentToolDefinition;
import com.example.rag.model.dto.AgentToolResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Agent 工具注册表测试。 */
class AgentToolRegistryTest {

    @Test
    void registryShouldFindRegisteredReadOnlyTools() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(
                stubTool("system.health.check"),
                stubTool("kb.readiness.check"),
                stubTool("documents.status.scan"),
                stubTool("indexing.tasks.scan"),
                stubTool("qa.retrieve.probe")
        ));

        assertThat(registry.find("system.health.check")).isPresent();
        assertThat(registry.find("kb.readiness.check")).isPresent();
        assertThat(registry.find("documents.status.scan")).isPresent();
        assertThat(registry.find("indexing.tasks.scan")).isPresent();
        assertThat(registry.find("qa.retrieve.probe")).isPresent();
        assertThat(registry.find("missing.tool")).isEmpty();
        assertThat(registry.definitions())
                .extracting(AgentToolDefinition::toolName)
                .containsExactlyInAnyOrder(
                        "system.health.check",
                        "kb.readiness.check",
                        "documents.status.scan",
                        "indexing.tasks.scan",
                        "qa.retrieve.probe"
                );
    }

    @Test
    void registryShouldRejectDuplicateToolName() {
        AgentTool tool = stubTool("system.health.check");

        assertThatThrownBy(() -> new AgentToolRegistry(List.of(tool, tool)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicate agent tool");
    }

    @Test
    void requireShouldRejectMissingToolName() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of());

        assertThatThrownBy(() -> registry.require("missing.tool"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Agent tool not found");
    }

    private AgentTool stubTool(String toolName) {
        return new AgentTool() {
            @Override
            public String toolName() {
                return toolName;
            }

            @Override
            public AgentToolDefinition definition() {
                return new AgentToolDefinition(
                        toolName,
                        AgentToolExecutionMode.READ_ONLY,
                        AgentActionRiskLevel.LOW
                );
            }

            @Override
            public AgentToolResult execute(AgentToolContext context) {
                return AgentToolResult.success(toolName, "{}", 0L);
            }
        };
    }
}

package com.example.rag.service.agent;

import com.example.rag.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MCP 工具注册表测试。 */
class McpToolRegistryTest {

    @Test
    void registryShouldFindRegisteredReadOnlyTools() {
        McpToolRegistry registry = new McpToolRegistry(List.of(
                stubTool("system.health.check"),
                stubTool("kb.readiness.check"),
                stubTool("documents.status.scan"),
                stubTool("indexing.tasks.scan"),
                stubTool("qa.retrieve.probe"),
                stubTool("retrieval.config.inspect")
        ));

        assertThat(registry.find("system.health.check")).isPresent();
        assertThat(registry.find("kb.readiness.check")).isPresent();
        assertThat(registry.find("documents.status.scan")).isPresent();
        assertThat(registry.find("indexing.tasks.scan")).isPresent();
        assertThat(registry.find("qa.retrieve.probe")).isPresent();
        assertThat(registry.find("retrieval.config.inspect")).isPresent();
        assertThat(registry.find("missing.tool")).isEmpty();
        assertThat(registry.definitions())
                .extracting(McpToolDefinition::name)
                .containsExactlyInAnyOrder(
                        "system.health.check",
                        "kb.readiness.check",
                        "documents.status.scan",
                        "indexing.tasks.scan",
                        "qa.retrieve.probe",
                        "retrieval.config.inspect"
                );
    }

    @Test
    void registryShouldRejectDuplicateToolName() {
        McpTool tool = stubTool("system.health.check");

        assertThatThrownBy(() -> new McpToolRegistry(List.of(tool, tool)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicate MCP tool");
    }

    @Test
    void requireShouldRejectMissingToolName() {
        McpToolRegistry registry = new McpToolRegistry(List.of());

        assertThatThrownBy(() -> registry.require("missing.tool"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MCP tool not found");
    }

    private McpTool stubTool(String toolName) {
        return new McpTool() {
            @Override
            public String name() {
                return toolName;
            }

            @Override
            public McpToolDefinition definition() {
                return McpToolDefinition.readOnlyLow(toolName, toolName, toolName);
            }

            @Override
            public McpToolResult call(McpToolContext context) {
                return McpToolResult.success(toolName, Map.of(), 0L);
            }
        };
    }
}

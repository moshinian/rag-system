package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagAgentProperties;
import com.example.rag.model.dto.AgentToolContext;
import com.example.rag.model.dto.AgentToolDefinition;
import com.example.rag.model.dto.AgentToolResult;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.model.request.AgentToolExecuteRequest;
import com.example.rag.service.agent.AgentTool;
import com.example.rag.service.agent.AgentToolRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Map;

import static com.example.rag.config.RequestIdFilter.REQUEST_ID_ATTRIBUTE;

/**
 * Agent Runtime 内部工具调用接口。
 */
@RestController
@RequestMapping("/api/internal/agent/tools")
public class AgentInternalToolController {
    public static final String INTERNAL_TOOL_TOKEN_HEADER = "X-Agent-Tool-Token";
    private final AgentToolRegistry agentToolRegistry;
    private final RagAgentProperties ragAgentProperties;

    /** 构造AgentInternalToolController。 */
    public AgentInternalToolController(AgentToolRegistry agentToolRegistry,
                                       RagAgentProperties ragAgentProperties) {
        this.agentToolRegistry = agentToolRegistry;
        this.ragAgentProperties = ragAgentProperties;
    }

    /** 查询 Java 权威侧暴露给 Agent Runtime 的工具定义。 */
    @GetMapping
    public ApiResponse<Collection<AgentToolDefinition>> definitions(
            @RequestHeader(value = INTERNAL_TOOL_TOKEN_HEADER, required = false) String token,
            HttpServletRequest request) {
        validateToken(token);
        return ApiResponse.success(agentToolRegistry.definitions(), String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE)));
    }

    /** 执行 Java 权威侧只读 Agent 工具。 */
    @PostMapping("/{toolName}/execute")
    public ApiResponse<AgentToolResult> execute(@PathVariable String toolName,
                                                @RequestHeader(value = INTERNAL_TOOL_TOKEN_HEADER, required = false) String token,
                                                @Valid @RequestBody AgentToolExecuteRequest body,
                                                HttpServletRequest request) {
        validateToken(token);
        AgentTool tool = agentToolRegistry.require(toolName);
        if (tool.definition().executionMode() != AgentToolExecutionMode.READ_ONLY) {
            throw new BusinessException("Only READ_ONLY agent tools can be executed by Agent Runtime: " + toolName);
        }
        AgentToolContext context = new AgentToolContext(
                body.kbCode(),
                normalize(body.question()),
                normalize(body.runCode()),
                normalize(body.operator()),
                body.attributes() == null ? Map.of() : body.attributes()
        );
        AgentToolResult result = tool.execute(context);
        return ApiResponse.success(result, String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE)));
    }

    private void validateToken(String actualToken) {
        String expectedToken = ragAgentProperties.getInternalToolToken();
        if (expectedToken == null || expectedToken.trim().isBlank()) {
            throw new BusinessException("Agent internal tool token is not configured");
        }
        if (actualToken == null || !expectedToken.equals(actualToken)) {
            throw new BusinessException("Invalid agent internal tool token");
        }
    }

    private String normalize(String value) {
        return value == null || value.trim().isBlank() ? null : value.trim();
    }
}

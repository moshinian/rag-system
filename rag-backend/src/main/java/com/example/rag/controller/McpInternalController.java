package com.example.rag.controller;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagAgentProperties;
import com.example.rag.service.agent.McpTool;
import com.example.rag.service.agent.McpToolArgumentsValidator;
import com.example.rag.service.agent.McpToolContext;
import com.example.rag.service.agent.McpToolDefinition;
import com.example.rag.service.agent.McpToolRegistry;
import com.example.rag.service.agent.McpToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Streamable HTTP transport 的内部 tools endpoint。
 */
@RestController
@RequestMapping("/api/internal/mcp")
public class McpInternalController {
    private static final Logger log = LoggerFactory.getLogger(McpInternalController.class);
    public static final String INTERNAL_TOOL_TOKEN_HEADER = "X-Agent-Tool-Token";
    public static final String PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    public static final String SESSION_ID_HEADER = "Mcp-Session-Id";
    public static final String PROTOCOL_VERSION = "2025-06-18";

    private final McpToolRegistry mcpToolRegistry;
    private final RagAgentProperties ragAgentProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    /** 构造McpInternalController。 */
    public McpInternalController(McpToolRegistry mcpToolRegistry,
                                 RagAgentProperties ragAgentProperties,
                                 ObjectMapper objectMapper) {
        this.mcpToolRegistry = mcpToolRegistry;
        this.ragAgentProperties = ragAgentProperties;
        this.objectMapper = objectMapper;
    }

    /** 第一版暂不支持 SSE。 */
    @GetMapping
    public ResponseEntity<Void> get() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    /** 处理单个 JSON-RPC request/notification。 */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> post(@RequestBody Object body,
                                  @RequestHeader HttpHeaders headers,
                                  HttpServletRequest request) {
        ResponseEntity<?> transportError = validateTransport(headers, request, body);
        if (transportError != null) {
            return transportError;
        }
        if (!(body instanceof Map<?, ?> raw)) {
            return ResponseEntity.badRequest().body("MCP endpoint accepts only a single JSON-RPC object");
        }
        Map<String, Object> rpc = normalizeObject(raw);
        String method = asText(rpc.get("method"));
        Object id = rpc.get("id");

        if ("initialize".equals(method)) {
            return handleInitialize(id);
        }
        String sessionId = headers.getFirst(SESSION_ID_HEADER);
        SessionState session = requireSession(sessionId);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if ("notifications/initialized".equals(method)) {
            session.initialized = true;
            return ResponseEntity.accepted().build();
        }
        if (!session.initialized) {
            return jsonRpcError(id, -32002, "MCP session is not initialized");
        }
        return switch (method) {
            case "tools/list" -> handleToolsList(id);
            case "tools/call" -> handleToolsCall(id, rpc.get("params"));
            default -> jsonRpcError(id, -32601, "Method not found: " + method);
        };
    }

    private ResponseEntity<?> handleInitialize(Object id) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new SessionState());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        result.put("serverInfo", Map.of("name", "rag-java-mcp-server", "version", "0.1.0"));
        return jsonRpcResult(id, result, Map.of(SESSION_ID_HEADER, sessionId));
    }

    private ResponseEntity<?> handleToolsList(Object id) {
        List<Map<String, Object>> tools = mcpToolRegistry.definitions().stream()
                .map(McpToolDefinition::toProtocol)
                .toList();
        return jsonRpcResult(id, Map.of("tools", tools), Map.of());
    }

    private ResponseEntity<?> handleToolsCall(Object id, Object params) {
        if (!(params instanceof Map<?, ?> paramsMap)) {
            return jsonRpcError(id, -32602, "tools/call params must be an object");
        }
        String name = asText(paramsMap.get("name"));
        if (name == null) {
            return jsonRpcError(id, -32602, "tools/call params.name is required");
        }
        McpTool tool;
        McpToolDefinition definition;
        try {
            tool = mcpToolRegistry.require(name);
            definition = mcpToolRegistry.requireDefinition(name);
        } catch (BusinessException ex) {
            return jsonRpcError(id, -32602, ex.getMessage());
        }
        Map<String, Object> arguments;
        try {
            arguments = optionalObject(paramsMap.get("arguments"), "tools/call params.arguments");
            McpToolArgumentsValidator.validate(name, definition.inputSchema(), arguments);
        } catch (BusinessException ex) {
            log.warn("Invalid MCP tool arguments: {}", ex.getMessage());
            return jsonRpcError(id, -32602, ex.getMessage());
        }
        Map<String, Object> meta;
        try {
            meta = optionalObject(paramsMap.get("_meta"), "tools/call params._meta");
        } catch (BusinessException ex) {
            return jsonRpcError(id, -32602, ex.getMessage());
        }
        McpToolContext context;
        try {
            context = toContext(arguments, meta);
        } catch (BusinessException ex) {
            return jsonRpcError(id, -32602, ex.getMessage());
        }
        McpToolResult toolResult;
        try {
            toolResult = tool.call(context);
        } catch (BusinessException ex) {
            toolResult = McpToolResult.failure(name, ex.getMessage(), 0L);
        }
        return jsonRpcResult(id, toToolCallResult(toolResult), Map.of());
    }

    private McpToolContext toContext(Map<String, Object> arguments, Map<String, Object> meta) {
        Object attributesObject = arguments.get("attributes");
        if (attributesObject != null && !(attributesObject instanceof Map<?, ?>)) {
            throw new BusinessException("arguments.attributes must be an object");
        }
        return new McpToolContext(arguments, meta);
    }

    private Map<String, Object> optionalObject(Object value, String fieldName) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new BusinessException(fieldName + " must be an object");
        }
        return normalizeObject(raw);
    }

    private Map<String, Object> toToolCallResult(McpToolResult result) {
        Map<String, Object> structuredContent = result.structuredContent() == null
                ? Map.of()
                : result.structuredContent();
        String text = result.isError()
                ? result.errorMessage()
                : toJson(structuredContent);
        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("content", List.of(Map.of("type", "text", "text", text == null ? "" : text)));
        protocol.put("structuredContent", structuredContent);
        protocol.put("isError", result.isError());
        return protocol;
    }

    private ResponseEntity<?> validateTransport(HttpHeaders headers, HttpServletRequest request, Object body) {
        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType == null || !contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Content-Type must be application/json");
        }
        List<MediaType> accepts = headers.getAccept();
        if (accepts.stream().noneMatch(media -> media.isCompatibleWith(MediaType.APPLICATION_JSON))
                || accepts.stream().noneMatch(media -> media.isCompatibleWith(MediaType.TEXT_EVENT_STREAM))) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                    .body("Accept must include application/json and text/event-stream");
        }
        String expectedToken = ragAgentProperties.getInternalToolToken();
        String actualToken = headers.getFirst(INTERNAL_TOOL_TOKEN_HEADER);
        if (expectedToken == null || expectedToken.isBlank() || !expectedToken.equals(actualToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid MCP internal token");
        }
        String origin = headers.getFirst(HttpHeaders.ORIGIN);
        if (origin == null || !ragAgentProperties.getMcpAllowedOrigins().contains(origin)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Origin is not allowed");
        }
        if (body instanceof List<?>) {
            return ResponseEntity.badRequest().body("JSON-RPC batch is not supported");
        }
        String method = body instanceof Map<?, ?> raw ? asText(raw.get("method")) : null;
        if (!"initialize".equals(method)) {
            String protocolVersion = headers.getFirst(PROTOCOL_VERSION_HEADER);
            if (!PROTOCOL_VERSION.equals(protocolVersion)) {
                return ResponseEntity.badRequest().body("MCP-Protocol-Version must be " + PROTOCOL_VERSION);
            }
        }
        return null;
    }

    private SessionState requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessions.get(sessionId);
    }

    private ResponseEntity<Map<String, Object>> jsonRpcResult(Object id, Object result, Map<String, String> extraHeaders) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().contentType(jsonMediaType());
        extraHeaders.forEach(builder::header);
        return builder.body(body);
    }

    private ResponseEntity<Map<String, Object>> jsonRpcError(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("error", error);
        return ResponseEntity.ok().contentType(jsonMediaType()).body(body);
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON);
    }

    private Map<String, Object> normalizeObject(Map<?, ?> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key instanceof String textKey) {
                normalized.put(textKey, value);
            }
        });
        return normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String asText(Object value) {
        return value instanceof String text ? text : null;
    }

    private static final class SessionState {
        private boolean initialized;
    }
}

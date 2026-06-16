package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.model.request.AgentRunCreateRequest;
import com.example.rag.model.response.AgentRunResponse;
import com.example.rag.service.AgentRunService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.rag.config.RequestIdFilter.REQUEST_ID_ATTRIBUTE;

/**
 * Agent 诊断运行接口。
 */
@RestController
@RequestMapping("/api/knowledge-bases/{kbCode}/agent")
public class AgentController {
    private final AgentRunService agentRunService;

    /** 构造AgentController。 */
    public AgentController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    /** 创建一条 Agent 诊断运行记录。 */
    @PostMapping("/runs")
    public ResponseEntity<ApiResponse<AgentRunResponse>> createRun(@PathVariable String kbCode,
                                                                    @Valid @RequestBody AgentRunCreateRequest body,
                                                                    HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        AgentRunResponse response = agentRunService.createRun(kbCode, body);
        return ResponseEntity.accepted().body(ApiResponse.success(response, requestId));
    }

    /** 查询一条 Agent 诊断运行详情。 */
    @GetMapping("/runs/{runCode}")
    public ApiResponse<AgentRunResponse> getRun(@PathVariable String kbCode,
                                                @PathVariable String runCode,
                                                HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        AgentRunResponse response = agentRunService.getRun(kbCode, runCode);
        return ApiResponse.success(response, requestId);
    }
}

package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.model.response.QuestionAnsweringReadinessResponse;
import com.example.rag.service.QuestionAnsweringService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.rag.config.RequestIdFilter.REQUEST_ID_ATTRIBUTE;

/**
 * 问答链路接口。
 */
@RestController
@RequestMapping("/api/knowledge-bases/{kbCode}/qa")
public class QuestionAnsweringController {

    private final QuestionAnsweringService questionAnsweringService;

    public QuestionAnsweringController(QuestionAnsweringService questionAnsweringService) {
        this.questionAnsweringService = questionAnsweringService;
    }

    /** 查看指定知识库的问答链路就绪状态。 */
    @GetMapping("/readiness")
    public ApiResponse<QuestionAnsweringReadinessResponse> readiness(@PathVariable String kbCode,
                                                                     HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        QuestionAnsweringReadinessResponse response = questionAnsweringService.getReadiness(kbCode);
        return ApiResponse.success(response, requestId);
    }
}

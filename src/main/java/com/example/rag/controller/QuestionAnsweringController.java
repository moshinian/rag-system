package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.model.request.QaAskRequest;
import com.example.rag.model.request.QuestionRetrievalRequest;
import com.example.rag.model.response.QuestionAnsweringReadinessResponse;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.QaAnswerResponse;
import com.example.rag.service.QaService;
import com.example.rag.service.QuestionAnsweringService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final QaService qaService;

    public QuestionAnsweringController(QuestionAnsweringService questionAnsweringService,
                                       QaService qaService) {
        this.questionAnsweringService = questionAnsweringService;
        this.qaService = qaService;
    }

    /** 查看指定知识库的问答链路就绪状态。 */
    @GetMapping("/readiness")
    public ApiResponse<QuestionAnsweringReadinessResponse> readiness(@PathVariable String kbCode,
                                                                     HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        QuestionAnsweringReadinessResponse response = questionAnsweringService.getReadiness(kbCode);
        return ApiResponse.success(response, requestId);
    }

    /** 对指定知识库执行第一版 TopK 检索。 */
    @PostMapping("/retrieve")
    public ApiResponse<QuestionRetrievalResponse> retrieve(@PathVariable String kbCode,
                                                           @Valid @RequestBody QuestionRetrievalRequest body,
                                                           HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        QuestionRetrievalResponse response = questionAnsweringService.retrieve(
                kbCode,
                body.question(),
                body.topK()
        );
        return ApiResponse.success(response, requestId);
    }

    /** 对指定知识库执行第一版问答闭环。 */
    @PostMapping("/ask")
    public ApiResponse<QaAnswerResponse> ask(@PathVariable String kbCode,
                                             @Valid @RequestBody QaAskRequest body,
                                             HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        QaAnswerResponse response = qaService.ask(
                kbCode,
                body.question(),
                body.topK()
        );
        return ApiResponse.success(response, requestId);
    }
}

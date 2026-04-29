package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.model.request.CreateKnowledgeBaseRequest;
import com.example.rag.model.response.KnowledgeBaseResponse;
import com.example.rag.service.KnowledgeBaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.rag.config.RequestIdFilter.REQUEST_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(@Valid @RequestBody CreateKnowledgeBaseRequest request,
                                                     HttpServletRequest httpRequest) {
        String requestId = String.valueOf(httpRequest.getAttribute(REQUEST_ID_ATTRIBUTE));
        KnowledgeBaseResponse response = knowledgeBaseService.create(request);
        return ApiResponse.success(response, requestId);
    }
}

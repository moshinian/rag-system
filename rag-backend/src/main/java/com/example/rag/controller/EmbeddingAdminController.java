package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.model.response.EmbeddingRebuildSubmitResponse;
import com.example.rag.service.EmbeddingRebuildService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.example.rag.config.RequestIdFilter.REQUEST_ID_ATTRIBUTE;

/**
 * Embedding 管理接口。
 */
@RestController
@RequestMapping("/api/admin/embeddings")
public class EmbeddingAdminController {
    private final EmbeddingRebuildService embeddingRebuildService;

    /** 构造EmbeddingAdminController。 */
    public EmbeddingAdminController(EmbeddingRebuildService embeddingRebuildService) {
        this.embeddingRebuildService = embeddingRebuildService;
    }

    /** 异步提交一次全量重嵌入。 */
    @PostMapping("/rebuild")
    public ResponseEntity<ApiResponse<EmbeddingRebuildSubmitResponse>> rebuild(@RequestParam(value = "operator", required = false) String operator,
                                                                               HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        EmbeddingRebuildSubmitResponse response = embeddingRebuildService.submit(operator);
        return ResponseEntity.accepted().body(ApiResponse.success(response, requestId));
    }
}

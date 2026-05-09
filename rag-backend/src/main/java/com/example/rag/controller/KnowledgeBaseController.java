package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.model.request.CreateKnowledgeBaseRequest;
import com.example.rag.model.response.KnowledgeBaseResponse;
import com.example.rag.model.response.PageResponse;
import com.example.rag.service.KnowledgeBaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.example.rag.config.RequestIdFilter.REQUEST_ID_ATTRIBUTE;

/**
 * 知识库管理接口。
 */
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /** 分页查询知识库。 */
    @GetMapping
    public ApiResponse<PageResponse<KnowledgeBaseResponse>> list(@RequestParam(value = "status", required = false) String status,
                                                                 @RequestParam(value = "pageNo", required = false) Long pageNo,
                                                                 @RequestParam(value = "pageSize", required = false) Long pageSize,
                                                                 HttpServletRequest httpRequest) {
        String requestId = String.valueOf(httpRequest.getAttribute(REQUEST_ID_ATTRIBUTE));
        PageResponse<KnowledgeBaseResponse> response = knowledgeBaseService.list(status, pageNo, pageSize);
        return ApiResponse.success(response, requestId);
    }

    /** 查询知识库详情。 */
    @GetMapping("/{kbCode}")
    public ApiResponse<KnowledgeBaseResponse> get(@PathVariable String kbCode,
                                                  HttpServletRequest httpRequest) {
        String requestId = String.valueOf(httpRequest.getAttribute(REQUEST_ID_ATTRIBUTE));
        KnowledgeBaseResponse response = knowledgeBaseService.get(kbCode);
        return ApiResponse.success(response, requestId);
    }

    /** 禁用知识库。 */
    @PostMapping("/{kbCode}/disable")
    public ApiResponse<KnowledgeBaseResponse> disable(@PathVariable String kbCode,
                                                      HttpServletRequest httpRequest) {
        String requestId = String.valueOf(httpRequest.getAttribute(REQUEST_ID_ATTRIBUTE));
        KnowledgeBaseResponse response = knowledgeBaseService.disable(kbCode);
        return ApiResponse.success(response, requestId);
    }

    /** 启用知识库。 */
    @PostMapping("/{kbCode}/enable")
    public ApiResponse<KnowledgeBaseResponse> enable(@PathVariable String kbCode,
                                                     HttpServletRequest httpRequest) {
        String requestId = String.valueOf(httpRequest.getAttribute(REQUEST_ID_ATTRIBUTE));
        KnowledgeBaseResponse response = knowledgeBaseService.enable(kbCode);
        return ApiResponse.success(response, requestId);
    }

    /** 创建知识库。 */
    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(@Valid @RequestBody CreateKnowledgeBaseRequest request,
                                                     HttpServletRequest httpRequest) {
        String requestId = String.valueOf(httpRequest.getAttribute(REQUEST_ID_ATTRIBUTE));
        KnowledgeBaseResponse response = knowledgeBaseService.create(request);
        return ApiResponse.success(response, requestId);
    }
}

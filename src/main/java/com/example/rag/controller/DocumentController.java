package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.model.response.DocumentProcessResponse;
import com.example.rag.model.response.DocumentUploadResponse;
import com.example.rag.service.DocumentProcessingService;
import com.example.rag.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static com.example.rag.config.RequestIdFilter.REQUEST_ID_ATTRIBUTE;

/**
 * 文档相关接口。
 *
 * 上传接口负责接收原始文件并保存元数据。
 * 处理接口负责解析文档、执行切块并写入 chunk 数据。
 */
@RestController
@RequestMapping("/api/knowledge-bases/{kbCode}/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentProcessingService documentProcessingService;

    public DocumentController(DocumentService documentService,
                              DocumentProcessingService documentProcessingService) {
        this.documentService = documentService;
        this.documentProcessingService = documentProcessingService;
    }

    /** 上传原始文档。 */
    @PostMapping("/upload")
    public ApiResponse<DocumentUploadResponse> upload(@PathVariable String kbCode,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "documentName", required = false) String documentName,
                                                      @RequestParam(value = "tags", required = false) String tags,
                                                      @RequestParam(value = "source", required = false) String source,
                                                      @RequestParam(value = "operator", required = false) String operator,
                                                      HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        DocumentUploadResponse response = documentService.upload(kbCode, file, documentName, tags, source, operator);
        return ApiResponse.success(response, requestId);
    }

    /** 处理已上传文档，执行解析、切块和入库。 */
    @PostMapping("/{documentCode}/process")
    public ApiResponse<DocumentProcessResponse> process(@PathVariable String kbCode,
                                                        @PathVariable String documentCode,
                                                        @RequestParam(value = "operator", required = false) String operator,
                                                        HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        DocumentProcessResponse response = documentProcessingService.process(kbCode, documentCode, operator);
        return ApiResponse.success(response, requestId);
    }
}

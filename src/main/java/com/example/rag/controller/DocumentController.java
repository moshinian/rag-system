package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.model.response.DocumentUploadResponse;
import com.example.rag.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static com.example.rag.config.RequestIdFilter.REQUEST_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/knowledge-bases/{kbCode}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

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
}

package com.example.rag.controller;

import com.example.rag.common.ApiResponse;
import com.example.rag.model.response.DocumentChunkResponse;
import com.example.rag.model.response.DocumentDetailResponse;
import com.example.rag.model.response.DocumentEmbeddingResponse;
import com.example.rag.model.response.DocumentIndexingTaskResponse;
import com.example.rag.model.response.DocumentProcessResponse;
import com.example.rag.model.response.DocumentSummaryResponse;
import com.example.rag.model.response.DocumentUploadResponse;
import com.example.rag.model.response.PageResponse;
import com.example.rag.service.DocumentEmbeddingService;
import com.example.rag.service.DocumentIndexingService;
import com.example.rag.service.DocumentProcessingService;
import com.example.rag.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final DocumentEmbeddingService documentEmbeddingService;
    private final DocumentIndexingService documentIndexingService;

    public DocumentController(DocumentService documentService,
                              DocumentProcessingService documentProcessingService,
                              DocumentEmbeddingService documentEmbeddingService,
                              DocumentIndexingService documentIndexingService) {
        this.documentService = documentService;
        this.documentProcessingService = documentProcessingService;
        this.documentEmbeddingService = documentEmbeddingService;
        this.documentIndexingService = documentIndexingService;
    }

    /** 分页查询知识库下的文档。 */
    @GetMapping
    public ApiResponse<PageResponse<DocumentSummaryResponse>> list(@PathVariable String kbCode,
                                                                  @RequestParam(value = "status", required = false) String status,
                                                                  @RequestParam(value = "pageNo", required = false) Long pageNo,
                                                                  @RequestParam(value = "pageSize", required = false) Long pageSize,
                                                                  HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        PageResponse<DocumentSummaryResponse> response = documentService.listDocuments(kbCode, status, pageNo, pageSize);
        return ApiResponse.success(response, requestId);
    }

    /** 查询文档详情。 */
    @GetMapping("/{documentCode}")
    public ApiResponse<DocumentDetailResponse> get(@PathVariable String kbCode,
                                                   @PathVariable String documentCode,
                                                   HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        DocumentDetailResponse response = documentService.getDocument(kbCode, documentCode);
        return ApiResponse.success(response, requestId);
    }

    /** 禁用文档。 */
    @PostMapping("/{documentCode}/disable")
    public ApiResponse<DocumentDetailResponse> disable(@PathVariable String kbCode,
                                                       @PathVariable String documentCode,
                                                       HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        DocumentDetailResponse response = documentService.disableDocument(kbCode, documentCode);
        return ApiResponse.success(response, requestId);
    }

    /** 查询文档的全部 chunk。 */
    @GetMapping("/{documentCode}/chunks")
    public ApiResponse<java.util.List<DocumentChunkResponse>> listChunks(@PathVariable String kbCode,
                                                                         @PathVariable String documentCode,
                                                                         HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        java.util.List<DocumentChunkResponse> response = documentService.listDocumentChunks(kbCode, documentCode);
        return ApiResponse.success(response, requestId);
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

    /** 重新处理已上传文档。 */
    @PostMapping("/{documentCode}/reprocess")
    public ApiResponse<DocumentProcessResponse> reprocess(@PathVariable String kbCode,
                                                          @PathVariable String documentCode,
                                                          @RequestParam(value = "operator", required = false) String operator,
                                                          HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        DocumentProcessResponse response = documentProcessingService.process(kbCode, documentCode, operator);
        return ApiResponse.success(response, requestId);
    }

    /** 对已切块文档执行本地 embedding，并把向量写入 pgvector。 */
    @PostMapping("/{documentCode}/embed")
    public ApiResponse<DocumentEmbeddingResponse> embed(@PathVariable String kbCode,
                                                        @PathVariable String documentCode,
                                                        HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        DocumentEmbeddingResponse response = documentEmbeddingService.embed(kbCode, documentCode);
        return ApiResponse.success(response, requestId);
    }

    /** 提交一条异步索引任务，后台串行执行 process + embed。 */
    @PostMapping("/{documentCode}/index")
    public ApiResponse<DocumentIndexingTaskResponse> index(@PathVariable String kbCode,
                                                           @PathVariable String documentCode,
                                                           @RequestParam(value = "operator", required = false) String operator,
                                                           HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        DocumentIndexingTaskResponse response = documentIndexingService.submit(kbCode, documentCode, operator);
        return ApiResponse.success(response, requestId);
    }

    /** 查询文档的索引任务历史。 */
    @GetMapping("/{documentCode}/indexing-tasks")
    public ApiResponse<java.util.List<DocumentIndexingTaskResponse>> listIndexingTasks(@PathVariable String kbCode,
                                                                                        @PathVariable String documentCode,
                                                                                        HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        java.util.List<DocumentIndexingTaskResponse> response = documentIndexingService.listTasks(kbCode, documentCode);
        return ApiResponse.success(response, requestId);
    }

    /** 手动重试指定索引任务。 */
    @PostMapping("/{documentCode}/indexing-tasks/{taskId}/retry")
    public ApiResponse<DocumentIndexingTaskResponse> retryIndexingTask(@PathVariable String kbCode,
                                                                       @PathVariable String documentCode,
                                                                       @PathVariable Long taskId,
                                                                       @RequestParam(value = "operator", required = false) String operator,
                                                                       HttpServletRequest request) {
        String requestId = String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        DocumentIndexingTaskResponse response = documentIndexingService.retry(kbCode, documentCode, taskId, operator);
        return ApiResponse.success(response, requestId);
    }
}

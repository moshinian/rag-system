package com.example.rag.evaluation;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentEmbeddingResponse;
import com.example.rag.model.response.DocumentProcessResponse;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.service.DocumentEmbeddingService;
import com.example.rag.service.DocumentProcessingService;
import com.example.rag.service.QuestionAnsweringService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Disabled("Requires unrestricted socket access to PostgreSQL and embedding service for real retrieval evaluation")
class QaRetrievalEvaluationIntegrationTest {

    private static final Path DATASET_PATH = Path.of("work/evaluation/day20-qa-eval-cases.json");

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private DocumentEmbeddingService documentEmbeddingService;

    @Autowired
    private QuestionAnsweringService questionAnsweringService;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldEvaluateChineseRetrievalCasesAgainstChineseSamples() throws IOException {
        JsonNode dataset = objectMapper.readTree(Files.readString(DATASET_PATH));
        String kbCode = dataset.path("kbCode").asText() + "-itest-" + snowflakeIdGenerator.nextId();
        int topK = dataset.path("topK").asInt();
        List<String> documentCodes = new ArrayList<>();

        createKnowledgeBase(kbCode);
        documentCodes.add(createDocument(kbCode, "结算异常处理指南", "work/samples/day20-cn-结算异常处理指南.md", "md", "text/markdown"));
        documentCodes.add(createDocument(kbCode, "对账常见问题", "work/samples/day20-cn-对账常见问题.md", "md", "text/markdown"));
        documentCodes.add(createDocument(kbCode, "值班巡检清单", "work/samples/day20-cn-值班巡检清单.txt", "txt", "text/plain"));

        processAndEmbedAll(kbCode, documentCodes);

        List<String> reportLines = new ArrayList<>();
        reportLines.add("Day20 Chinese Retrieval Evaluation Report");
        reportLines.add("| caseCode | category | retrievalHit | keywordMatches | topDocuments |");
        reportLines.add("| --- | --- | --- | ---: | --- |");

        for (JsonNode caseNode : dataset.path("cases")) {
            String caseCode = caseNode.path("caseCode").asText();
            String category = caseNode.path("category").asText();
            String question = caseNode.path("question").asText();
            String expectedDocument = caseNode.path("expectedDocument").asText();
            String expectationType = caseNode.path("expectationType").asText();

            QuestionRetrievalResponse response = questionAnsweringService.retrieve(kbCode, question, topK);
            String mergedContent = response.chunks().stream()
                    .map(chunk -> chunk.documentName() + "\n" + chunk.content())
                    .reduce("", (left, right) -> left + "\n" + right);
            boolean retrievalHit = !expectedDocument.isBlank() && response.chunks().stream()
                    .anyMatch(chunk -> expectedDocument.equals(chunk.documentName()));
            long keywordMatches = countKeywordMatches(caseNode.path("expectedKeywords"), mergedContent);
            String topDocuments = response.chunks().stream()
                    .map(chunk -> chunk.documentName() + "#" + chunk.chunkIndex())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("-");

            reportLines.add("| " + caseCode + " | " + category + " | " + retrievalHit + " | "
                    + keywordMatches + " | " + topDocuments + " |");

            if ("SHOULD_ANSWER".equals(expectationType)) {
                assertThat(retrievalHit).as(caseCode + " should hit expected document").isTrue();
                assertThat(keywordMatches).as(caseCode + " should match at least one expected keyword").isGreaterThan(0);
            }
        }

        System.out.println(String.join("\n", reportLines));
    }

    private void createKnowledgeBase(String kbCode) {
        long knowledgeBaseId = snowflakeIdGenerator.nextId();
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(knowledgeBaseId);
        knowledgeBase.setKbCode(kbCode);
        knowledgeBase.setName("Day20 中文评测知识库");
        knowledgeBase.setDescription("Day20 中文检索评测专用");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
        knowledgeBase.setCreatedBy("itest");
        knowledgeBaseRepository.insert(knowledgeBase);
    }

    private String createDocument(String kbCode,
                                  String displayName,
                                  String filePath,
                                  String fileType,
                                  String mediaType) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode).orElseThrow();
        long documentId = snowflakeIdGenerator.nextId();
        String documentCode = "DOC-" + documentId;
        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        document.setKnowledgeBaseId(knowledgeBase.getId());
        document.setDocumentCode(documentCode);
        document.setFileName(Path.of(filePath).getFileName().toString());
        document.setDisplayName(displayName);
        document.setFileType(fileType);
        document.setMediaType(mediaType);
        document.setStoragePath(Path.of(filePath).toAbsolutePath().toString());
        document.setFileSize(fileSize(filePath));
        document.setContentHash(displayName + "-hash-" + documentId);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setVersion(1);
        document.setCreatedBy("itest");
        documentRepository.insert(document);
        return documentCode;
    }

    private long fileSize(String filePath) {
        try {
            return Files.size(Path.of(filePath));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to resolve file size: " + filePath, ex);
        }
    }

    private void processAndEmbedAll(String kbCode, List<String> documentCodes) {
        for (String documentCode : documentCodes) {
            DocumentProcessResponse processResponse = documentProcessingService.process(kbCode, documentCode, "itest");
            assertThat(processResponse.chunkCount()).isGreaterThan(0);
            DocumentEmbeddingResponse embeddingResponse = documentEmbeddingService.embed(kbCode, documentCode);
            assertThat(embeddingResponse.embeddedChunkCount()).isGreaterThan(0);
        }
    }

    private long countKeywordMatches(JsonNode keywords, String mergedContent) {
        long matches = 0;
        for (JsonNode keywordNode : keywords) {
            String keyword = keywordNode.asText();
            if (!keyword.isBlank() && mergedContent.contains(keyword)) {
                matches++;
            }
        }
        return matches;
    }
}

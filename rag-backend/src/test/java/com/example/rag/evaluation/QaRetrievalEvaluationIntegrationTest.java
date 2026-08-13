package com.example.rag.evaluation;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.config.RagRetrievalProperties;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.enums.RerankStatus;
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
import com.example.rag.support.TestPaths;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实中文检索评测集成测试。 */
@SpringBootTest
@Transactional
@Disabled("Requires unrestricted socket access to PostgreSQL and embedding service for real retrieval evaluation")
class QaRetrievalEvaluationIntegrationTest {

    private static final Path DATASET_PATH = backendFile("work/evaluation/day20-qa-eval-cases.json");
    private static final Path HYBRID_DATASET_PATH = backendFile("work/evaluation/day25-hybrid-eval-cases.json");

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
    private RagRetrievalProperties retrievalProperties;

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
        documentCodes.add(createDocument(kbCode, "结算异常处理指南", backendFile("work/samples/day20-cn-结算异常处理指南.md"), "md", "text/markdown"));
        documentCodes.add(createDocument(kbCode, "对账常见问题", backendFile("work/samples/day20-cn-对账常见问题.md"), "md", "text/markdown"));
        documentCodes.add(createDocument(kbCode, "值班巡检清单", backendFile("work/samples/day20-cn-值班巡检清单.txt"), "txt", "text/plain"));

        processAndEmbedAll(kbCode, documentCodes);

        List<String> reportLines = new ArrayList<>();
        reportLines.add("Chinese Retrieval Evaluation Report");
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

    @Test
    void shouldPrintDenseVsHybridComparisonReport() throws IOException {
        JsonNode dataset = objectMapper.readTree(Files.readString(HYBRID_DATASET_PATH));
        String kbCode = dataset.path("kbCode").asText() + "-hybrid-itest-" + snowflakeIdGenerator.nextId();
        int topK = dataset.path("topK").asInt();
        List<String> documentCodes = new ArrayList<>();

        createKnowledgeBase(kbCode);
        documentCodes.add(createDocument(kbCode, "结算异常处理指南", backendFile("work/samples/day20-cn-结算异常处理指南.md"), "md", "text/markdown"));
        documentCodes.add(createDocument(kbCode, "对账常见问题", backendFile("work/samples/day20-cn-对账常见问题.md"), "md", "text/markdown"));
        documentCodes.add(createDocument(kbCode, "值班巡检清单", backendFile("work/samples/day20-cn-值班巡检清单.txt"), "txt", "text/plain"));

        processAndEmbedAll(kbCode, documentCodes);

        List<String> reportLines = new ArrayList<>();
        reportLines.add("Dense vs Hybrid Retrieval Evaluation Report");
        reportLines.add("| caseCode | category | comparisonFocus | denseRetrievalHit | hybridRetrievalHit | denseKeywordMatches | hybridKeywordMatches | denseTopDocuments | hybridTopDocuments |");
        reportLines.add("| --- | --- | --- | --- | --- | ---: | ---: | --- | --- |");

        for (JsonNode caseNode : dataset.path("cases")) {
            String caseCode = caseNode.path("caseCode").asText();
            String category = caseNode.path("category").asText();
            String comparisonFocus = caseNode.path("comparisonFocus").asText();
            String question = caseNode.path("question").asText();
            String expectedDocument = caseNode.path("expectedDocument").asText();
            String expectationType = caseNode.path("expectationType").asText();

            QuestionRetrievalResponse denseResponse = questionAnsweringService.retrieve(kbCode, question, topK, RetrievalMode.DENSE);
            QuestionRetrievalResponse hybridResponse = questionAnsweringService.retrieve(kbCode, question, topK, RetrievalMode.HYBRID);

            RetrievalObservation denseObservation = observe(caseNode, expectedDocument, denseResponse);
            RetrievalObservation hybridObservation = observe(caseNode, expectedDocument, hybridResponse);

            reportLines.add("| " + caseCode + " | " + category + " | " + comparisonFocus + " | "
                    + denseObservation.retrievalHit() + " | " + hybridObservation.retrievalHit() + " | "
                    + denseObservation.keywordMatches() + " | " + hybridObservation.keywordMatches() + " | "
                    + denseObservation.topDocuments() + " | " + hybridObservation.topDocuments() + " |");

            if ("SHOULD_ANSWER".equals(expectationType)) {
                assertThat(denseObservation.keywordMatches()).as(caseCode + " dense should surface at least one expected keyword")
                        .isGreaterThan(0);
                assertThat(hybridObservation.keywordMatches()).as(caseCode + " hybrid should surface at least one expected keyword")
                        .isGreaterThan(0);
            }
        }

        System.out.println(String.join("\n", reportLines));
    }

    @Test
    void shouldPrintDenseAndHybridRerankComparisonReport() throws IOException {
        JsonNode dataset = objectMapper.readTree(Files.readString(HYBRID_DATASET_PATH));
        String kbCode = dataset.path("kbCode").asText() + "-rerank-itest-" + snowflakeIdGenerator.nextId();
        int topK = dataset.path("topK").asInt();
        List<String> documentCodes = new ArrayList<>();

        createKnowledgeBase(kbCode);
        documentCodes.add(createDocument(kbCode, "结算异常处理指南", backendFile("work/samples/day20-cn-结算异常处理指南.md"), "md", "text/markdown"));
        documentCodes.add(createDocument(kbCode, "对账常见问题", backendFile("work/samples/day20-cn-对账常见问题.md"), "md", "text/markdown"));
        documentCodes.add(createDocument(kbCode, "值班巡检清单", backendFile("work/samples/day20-cn-值班巡检清单.txt"), "txt", "text/plain"));
        processAndEmbedAll(kbCode, documentCodes);

        RagRetrievalProperties.Rerank rerank = retrievalProperties.getRerank();
        boolean originalEnabled = rerank.isEnabled();
        Map<String, List<Integer>> ranksByVariant = new LinkedHashMap<>();
        Map<String, List<Long>> durationsByVariant = new LinkedHashMap<>();
        for (String variant : List.of("DENSE", "DENSE_RERANK", "HYBRID", "HYBRID_RERANK")) {
            ranksByVariant.put(variant, new ArrayList<>());
            durationsByVariant.put(variant, new ArrayList<>());
        }

        try {
            for (JsonNode caseNode : dataset.path("cases")) {
                if (!"SHOULD_ANSWER".equals(caseNode.path("expectationType").asText())) {
                    continue;
                }
                String question = caseNode.path("question").asText();
                String expectedDocument = caseNode.path("expectedDocument").asText();
                for (RetrievalMode mode : RetrievalMode.values()) {
                    rerank.setEnabled(false);
                    recordRanking(
                            mode.name(),
                            expectedDocument,
                            questionAnsweringService.retrieve(kbCode, question, topK, mode),
                            ranksByVariant,
                            durationsByVariant
                    );

                    rerank.setEnabled(true);
                    QuestionRetrievalResponse rerankedResponse = questionAnsweringService.retrieve(
                            kbCode, question, topK, mode
                    );
                    assertThat(rerankedResponse.rerankStatus())
                            .as(caseNode.path("caseCode").asText() + " should apply rerank")
                            .isEqualTo(RerankStatus.APPLIED);
                    recordRanking(
                            mode.name() + "_RERANK",
                            expectedDocument,
                            rerankedResponse,
                            ranksByVariant,
                            durationsByVariant
                    );
                }
            }
        } finally {
            rerank.setEnabled(originalEnabled);
        }

        List<String> reportLines = new ArrayList<>();
        reportLines.add("Dense / Hybrid Rerank Evaluation Report");
        reportLines.add("| variant | cases | Hit@1 | Hit@3 | MRR@3 | p95 ms |");
        reportLines.add("| --- | ---: | ---: | ---: | ---: | ---: |");
        for (Map.Entry<String, List<Integer>> entry : ranksByVariant.entrySet()) {
            List<Integer> ranks = entry.getValue();
            reportLines.add("| " + entry.getKey()
                    + " | " + ranks.size()
                    + " | " + formatMetric(hitAt(ranks, 1))
                    + " | " + formatMetric(hitAt(ranks, 3))
                    + " | " + formatMetric(mrrAt(ranks, 3))
                    + " | " + percentile95(durationsByVariant.get(entry.getKey()))
                    + " |");
        }
        System.out.println(String.join("\n", reportLines));
    }

    private void createKnowledgeBase(String kbCode) {
        long knowledgeBaseId = snowflakeIdGenerator.nextId();
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(knowledgeBaseId);
        knowledgeBase.setKbCode(kbCode);
        knowledgeBase.setName("中文评测知识库");
        knowledgeBase.setDescription("中文检索评测专用");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
        knowledgeBase.setCreatedBy("itest");
        knowledgeBaseRepository.insert(knowledgeBase);
    }

    private String createDocument(String kbCode,
                                  String displayName,
                                  Path filePath,
                                  String fileType,
                                  String mediaType) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode).orElseThrow();
        long documentId = snowflakeIdGenerator.nextId();
        String documentCode = "DOC-" + documentId;
        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        document.setKnowledgeBaseId(knowledgeBase.getId());
        document.setDocumentCode(documentCode);
        document.setFileName(filePath.getFileName().toString());
        document.setDisplayName(displayName);
        document.setFileType(fileType);
        document.setMediaType(mediaType);
        document.setStoragePath(filePath.toAbsolutePath().toString());
        document.setFileSize(fileSize(filePath));
        document.setContentHash(displayName + "-hash-" + documentId);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setVersion(1);
        document.setCreatedBy("itest");
        documentRepository.insert(document);
        return documentCode;
    }

    private long fileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to resolve file size: " + filePath, ex);
        }
    }

    private static Path backendFile(String relativePath) {
        return TestPaths.backendFile(relativePath);
    }

    private void processAndEmbedAll(String kbCode, List<String> documentCodes) {
        for (String documentCode : documentCodes) {
            DocumentProcessResponse processResponse = documentProcessingService.process(
                    Objects.requireNonNull(kbCode, "kbCode must not be null"),
                    Objects.requireNonNull(documentCode, "documentCode must not be null"),
                    "itest"
            );
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

    private RetrievalObservation observe(JsonNode caseNode,
                                         String expectedDocument,
                                         QuestionRetrievalResponse response) {
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
        return new RetrievalObservation(retrievalHit, keywordMatches, topDocuments);
    }

    private void recordRanking(String variant,
                               String expectedDocument,
                               QuestionRetrievalResponse response,
                               Map<String, List<Integer>> ranksByVariant,
                               Map<String, List<Long>> durationsByVariant) {
        int rank = 0;
        for (int index = 0; index < response.chunks().size(); index++) {
            if (expectedDocument.equals(response.chunks().get(index).documentName())) {
                rank = index + 1;
                break;
            }
        }
        ranksByVariant.get(variant).add(rank);
        durationsByVariant.get(variant).add(response.totalDurationMs());
    }

    private double hitAt(List<Integer> ranks, int cutoff) {
        return ranks.stream().filter(rank -> rank > 0 && rank <= cutoff).count() / (double) ranks.size();
    }

    private double mrrAt(List<Integer> ranks, int cutoff) {
        return ranks.stream()
                .mapToDouble(rank -> rank > 0 && rank <= cutoff ? 1D / rank : 0D)
                .average()
                .orElse(0D);
    }

    private long percentile95(List<Long> durations) {
        List<Long> sorted = durations.stream().sorted().toList();
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95D) - 1);
        return sorted.get(index);
    }

    private String formatMetric(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private record RetrievalObservation(boolean retrievalHit, long keywordMatches, String topDocuments) {
    }
}

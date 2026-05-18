package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.config.RagRetrievalProperties;
import com.example.rag.common.logging.StructuredLogMessage;
import com.example.rag.config.CacheNames;
import com.example.rag.integration.ai.AiGatewayClient;
import com.example.rag.model.dto.RetrievedChunkCandidate;
import com.example.rag.model.enums.KeywordStrategy;
import com.example.rag.model.enums.RetrievalMode;
import com.example.rag.model.response.QuestionAnsweringReadinessResponse;
import com.example.rag.model.response.QuestionRetrievalResponse;
import com.example.rag.model.response.RetrievedChunkResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.springframework.cache.annotation.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 问答检索服务。
 *
 * 负责提供问答链路就绪度检查，以及基于向量的 TopK 检索能力。
 */
@Service
public class QuestionAnsweringService {
    private static final String FUSION_STRATEGY_NONE = "NONE";
    private static final String FUSION_STRATEGY_RRF = "RRF";
    private static final Pattern LATIN_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern LATIN_PHRASE_PATTERN = Pattern.compile("([A-Za-z0-9_-]+(?:\\s+[A-Za-z0-9_-]+)+)");
    private static final Pattern CJK_SEGMENT_PATTERN = Pattern.compile("[\\u4E00-\\u9FFF]+");
    private static final Logger log = LoggerFactory.getLogger(QuestionAnsweringService.class);
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final RagRetrievalProperties ragRetrievalProperties;
    private final AiGatewayClient aiGatewayClient;
    private final RetrievalReadinessService retrievalReadinessService;

    /** 构造QuestionAnsweringService。 */
    public QuestionAnsweringService(KnowledgeBaseRepository knowledgeBaseRepository,
                                    DocumentChunkRepository documentChunkRepository,
                                    RagEmbeddingProperties ragEmbeddingProperties,
                                    RagRetrievalProperties ragRetrievalProperties,
                                    AiGatewayClient aiGatewayClient,
                                    RetrievalReadinessService retrievalReadinessService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.ragEmbeddingProperties = ragEmbeddingProperties;
        this.ragRetrievalProperties = ragRetrievalProperties;
        this.aiGatewayClient = aiGatewayClient;
        this.retrievalReadinessService = retrievalReadinessService;
    }

    /** 返回指定知识库当前是否具备进入检索和问答阶段的前置条件。 */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.QA_READINESS, key = "#kbCode")
    public QuestionAnsweringReadinessResponse getReadiness(String kbCode) {
        return retrievalReadinessService.getReadiness(kbCode);
    }

    /** 对指定知识库执行向量检索，并返回命中的 chunk 列表。 */
    @Transactional(readOnly = true)
    public QuestionRetrievalResponse retrieve(String kbCode, String question, Integer topK) {
        return retrieve(kbCode, question, topK, null);
    }

    /** 对指定知识库执行检索，并返回命中的 chunk 列表。 */
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.QA_RETRIEVAL,
            key = "#kbCode + ':' + (#question == null ? 'null' : #question.trim()) + ':' + (#topK == null ? 'null' : #topK) + ':' + (#retrievalMode == null ? 'AUTO' : #retrievalMode.name()) + ':' + #root.target.currentKeywordStrategyName()"
    )
    public QuestionRetrievalResponse retrieve(String kbCode,
                                              String question,
                                              Integer topK,
                                              RetrievalMode retrievalMode) {
        long retrievalStartedAt = System.currentTimeMillis();
        KnowledgeBaseEntity knowledgeBase = getKnowledgeBase(kbCode);
        // cache 命中前后都必须与同一套 readiness gate 保持一致，避免页面显示可用但检索真实不可用。
        retrievalReadinessService.assertRetrievalReady(kbCode);
        String normalizedQuestion = normalizeQuestion(question);
        int resolvedTopK = resolveTopK(topK);
        RetrievalMode resolvedRetrievalMode = resolveRetrievalMode(retrievalMode);
        KeywordStrategy keywordStrategy = resolveKeywordStrategy();
        long denseStartedAt = System.currentTimeMillis();
        log.info(StructuredLogMessage.of("qa.retrieve.started")
                .field("kbCode", kbCode)
                .field("topK", resolvedTopK)
                .field("retrievalMode", resolvedRetrievalMode.name())
                .field("keywordStrategy", resolvedRetrievalMode == RetrievalMode.HYBRID ? keywordStrategy.name() : "NONE")
                .field("questionLength", normalizedQuestion.length())
                .build());

        List<Double> queryVector = aiGatewayClient.createEmbedding(
                ragEmbeddingProperties.getModel(),
                normalizedQuestion
        );
        String queryVectorLiteral = toVectorLiteral(queryVector);
        List<RetrievedChunkCandidate> denseCandidates = documentChunkRepository.findTopKSimilarChunks(
                knowledgeBase.getId(),
                queryVectorLiteral,
                resolvedRetrievalMode == RetrievalMode.HYBRID ? resolveDenseCandidateLimit(resolvedTopK) : resolvedTopK
        );
        long denseDurationMs = System.currentTimeMillis() - denseStartedAt;
        log.info(StructuredLogMessage.of("qa.retrieve.dense.completed")
                .field("kbCode", kbCode)
                .field("retrievalMode", resolvedRetrievalMode.name())
                .field("candidateCount", denseCandidates.size())
                .field("durationMs", denseDurationMs)
                .build());

        List<RetrievedChunkCandidate> keywordCandidates = List.of();
        long keywordDurationMs = 0L;
        long fusionDurationMs = 0L;
        String fusionStrategy = FUSION_STRATEGY_NONE;
        List<RetrievedChunkResponse> chunks;
        if (resolvedRetrievalMode == RetrievalMode.HYBRID) {
            long keywordStartedAt = System.currentTimeMillis();
            keywordCandidates = findTopKeywordChunks(
                    knowledgeBase.getId(),
                    normalizedQuestion,
                    keywordStrategy,
                    resolveKeywordCandidateLimit(resolvedTopK)
            );
            keywordDurationMs = System.currentTimeMillis() - keywordStartedAt;
            log.info(StructuredLogMessage.of("qa.retrieve.keyword.completed")
                    .field("kbCode", kbCode)
                    .field("retrievalMode", resolvedRetrievalMode.name())
                    .field("keywordStrategy", keywordStrategy.name())
                    .field("candidateCount", keywordCandidates.size())
                    .field("durationMs", keywordDurationMs)
                    .build());

            long fusionStartedAt = System.currentTimeMillis();
            chunks = fuseCandidates(denseCandidates, keywordCandidates, resolvedTopK);
            fusionDurationMs = System.currentTimeMillis() - fusionStartedAt;
            fusionStrategy = FUSION_STRATEGY_RRF;
            log.info(StructuredLogMessage.of("qa.retrieve.fusion.completed")
                    .field("kbCode", kbCode)
                    .field("retrievalMode", resolvedRetrievalMode.name())
                    .field("keywordStrategy", keywordStrategy.name())
                    .field("denseCandidateCount", denseCandidates.size())
                    .field("keywordCandidateCount", keywordCandidates.size())
                    .field("finalHitCount", chunks.size())
                    .field("durationMs", fusionDurationMs)
                    .build());
        } else {
            chunks = denseCandidates.stream()
                    .limit(resolvedTopK)
                    .map(this::toRetrievedChunkResponse)
                    .toList();
        }

        long totalDurationMs = System.currentTimeMillis() - retrievalStartedAt;
        log.info(StructuredLogMessage.of("qa.retrieve.completed")
                .field("kbCode", kbCode)
                .field("topK", resolvedTopK)
                .field("retrievalMode", resolvedRetrievalMode.name())
                .field("keywordStrategy", resolvedRetrievalMode == RetrievalMode.HYBRID ? keywordStrategy.name() : "NONE")
                .field("fusionStrategy", fusionStrategy)
                .field("denseCandidateCount", denseCandidates.size())
                .field("keywordCandidateCount", keywordCandidates.size())
                .field("finalHitCount", chunks.size())
                .field("denseDurationMs", denseDurationMs)
                .field("keywordDurationMs", keywordDurationMs)
                .field("fusionDurationMs", fusionDurationMs)
                .field("totalDurationMs", totalDurationMs)
                .field("embeddingModel", ragEmbeddingProperties.getModel())
                .build());

        return new QuestionRetrievalResponse(
                knowledgeBase.getKbCode(),
                normalizedQuestion,
                ragEmbeddingProperties.getModel(),
                resolvedTopK,
                resolvedRetrievalMode,
                fusionStrategy,
                denseCandidates.size(),
                keywordCandidates.size(),
                chunks.size(),
                denseDurationMs,
                keywordDurationMs,
                fusionDurationMs,
                totalDurationMs,
                chunks
        );
    }

    /** 读取知识库，不存在时统一抛业务异常。 */
    private KnowledgeBaseEntity getKnowledgeBase(String kbCode) {
        return knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
    }

    /** 统一校验并清理问题文本。 */
    private String normalizeQuestion(String question) {
        if (question == null || question.trim().isBlank()) {
            throw new BusinessException("Question must not be blank");
        }
        return question.trim();
    }

    /** 结合默认值和最大值限制，解析最终的检索条数。 */
    private int resolveTopK(Integer topK) {
        int fallbackTopK = ragRetrievalProperties.getDefaultTopK() == null ? 5 : ragRetrievalProperties.getDefaultTopK();
        int maxTopK = ragRetrievalProperties.getMaxTopK() == null ? 10 : ragRetrievalProperties.getMaxTopK();
        // 默认值和上限统一走配置，避免前端和后端各自维护一套口径。
        int resolvedTopK = topK == null ? fallbackTopK : topK;
        if (resolvedTopK < 1) {
            throw new BusinessException("topK must be >= 1");
        }
        if (resolvedTopK > maxTopK) {
            throw new BusinessException("topK must be <= " + maxTopK);
        }
        return resolvedTopK;
    }

    /** 解析最终检索模式，不传时回退到配置默认值。 */
    private RetrievalMode resolveRetrievalMode(RetrievalMode retrievalMode) {
        return retrievalMode == null
                ? (ragRetrievalProperties.getDefaultMode() == null ? RetrievalMode.DENSE : ragRetrievalProperties.getDefaultMode())
                : retrievalMode;
    }

    /** 解析 hybrid 模式下 dense 候选规模。 */
    private int resolveDenseCandidateLimit(int resolvedTopK) {
        Integer configured = ragRetrievalProperties.getDenseCandidateLimit();
        return configured == null || configured < resolvedTopK ? resolvedTopK : configured;
    }

    /** 解析 hybrid 模式下 keyword 候选规模。 */
    private int resolveKeywordCandidateLimit(int resolvedTopK) {
        Integer configured = ragRetrievalProperties.getKeywordCandidateLimit();
        return configured == null || configured < resolvedTopK ? resolvedTopK : configured;
    }

    /** 解析 RRF 融合常量。 */
    private int resolveFusionK() {
        Integer configured = ragRetrievalProperties.getFusionK();
        return configured == null || configured < 1 ? 60 : configured;
    }

    /** 解析 hybrid 使用的 lexical 策略。 */
    private KeywordStrategy resolveKeywordStrategy() {
        return ragRetrievalProperties.getKeywordStrategy() == null
                ? KeywordStrategy.LIKE
                : ragRetrievalProperties.getKeywordStrategy();
    }

    /** 暴露给缓存 SpEL 使用的当前 keyword strategy 名称。 */
    public String currentKeywordStrategyName() {
        return resolveKeywordStrategy().name();
    }

    /** 路由到当前配置的 lexical recall 实现。 */
    private List<RetrievedChunkCandidate> findTopKeywordChunks(Long knowledgeBaseId,
                                                               String normalizedQuestion,
                                                               KeywordStrategy keywordStrategy,
                                                               int limit) {
        return switch (keywordStrategy) {
            case POSTGRES_FTS -> findTopKeywordChunksWithFtsFallback(
                    knowledgeBaseId,
                    normalizedQuestion,
                    limit
            );
            case LIKE -> documentChunkRepository.findTopKeywordChunks(
                    knowledgeBaseId,
                    normalizedQuestion,
                    extractKeywordTerms(normalizedQuestion),
                    resolveLikePhraseWeight(),
                    resolveLikeTitleWeight(),
                    resolveKeywordMinHitThreshold(),
                    limit
            );
        };
    }

    /**
     * PostgreSQL FTS 在当前 simple config 下对中文条文短句支持较弱；
     * 当 lexical 分支零命中时，退回当前 LIKE 方案，避免出现“LIKE 能命中、FTS 完全空”的明显体验倒挂。
     */
    private List<RetrievedChunkCandidate> findTopKeywordChunksWithFtsFallback(Long knowledgeBaseId,
                                                                              String normalizedQuestion,
                                                                              int limit) {
        List<RetrievedChunkCandidate> ftsCandidates = documentChunkRepository.findTopKeywordChunksByFts(
                knowledgeBaseId,
                buildFtsQueryText(normalizedQuestion),
                resolveKeywordTsConfig(),
                resolveKeywordRankFunction(),
                limit
        );
        if (!ftsCandidates.isEmpty()) {
            return ftsCandidates;
        }

        if (!containsCjk(normalizedQuestion)) {
            return ftsCandidates;
        }

        log.info(StructuredLogMessage.of("qa.retrieve.keyword.fallback_like")
                .field("keywordStrategy", KeywordStrategy.POSTGRES_FTS.name())
                .field("reason", "fts_zero_hit_on_cjk_query")
                .field("questionLength", normalizedQuestion.length())
                .build());
        return documentChunkRepository.findTopKeywordChunks(
                knowledgeBaseId,
                normalizedQuestion,
                extractKeywordTerms(normalizedQuestion),
                resolveLikePhraseWeight(),
                resolveLikeTitleWeight(),
                resolveKeywordMinHitThreshold(),
                limit
        );
    }

    /** 提取第一版关键词召回使用的检索 term。 */
    private List<String> extractKeywordTerms(String normalizedQuestion) {
        int minTokenLength = ragRetrievalProperties.getKeywordMinTokenLength() == null
                ? 3
                : Math.max(2, ragRetrievalProperties.getKeywordMinTokenLength());
        String lowerQuestion = normalizedQuestion.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher latinMatcher = LATIN_TOKEN_PATTERN.matcher(lowerQuestion);
        while (latinMatcher.find()) {
            String token = latinMatcher.group();
            if (token.length() >= minTokenLength) {
                terms.add(token);
            }
        }

        Matcher cjkMatcher = CJK_SEGMENT_PATTERN.matcher(normalizedQuestion);
        while (cjkMatcher.find()) {
            String segment = cjkMatcher.group();
            addChineseTerms(segment, terms, minTokenLength, 8, 12);
            if (terms.size() >= 12) {
                break;
            }
        }
        terms.remove(lowerQuestion);
        terms.remove(normalizedQuestion);
        return new ArrayList<>(terms);
    }

    /**
     * 构造 PostgreSQL FTS 使用的查询文本。
     * 优先抽取 ASCII/mixed term，避免中文提示词把 tsquery 约束得过严导致 mixed-term 检索失效。
     */
    private String buildFtsQueryText(String normalizedQuestion) {
        List<String> latinTokens = new ArrayList<>();
        Matcher latinMatcher = LATIN_TOKEN_PATTERN.matcher(normalizedQuestion);
        while (latinMatcher.find()) {
            latinTokens.add(latinMatcher.group());
        }

        if (!latinTokens.isEmpty()) {
            StringBuilder queryBuilder = new StringBuilder();
            Matcher phraseMatcher = LATIN_PHRASE_PATTERN.matcher(normalizedQuestion);
            if (phraseMatcher.find()) {
                queryBuilder.append('"').append(phraseMatcher.group(1).trim()).append('"');
            }
            for (String token : new LinkedHashSet<>(latinTokens)) {
                if (!queryBuilder.isEmpty()) {
                    queryBuilder.append(" OR ");
                }
                queryBuilder.append(token);
            }
            return queryBuilder.toString();
        }

        return normalizedQuestion;
    }

    private boolean containsCjk(String normalizedQuestion) {
        return CJK_SEGMENT_PATTERN.matcher(normalizedQuestion).find();
    }

    private String resolveKeywordTsConfig() {
        String configured = ragRetrievalProperties.getKeywordTsConfig();
        return configured == null || configured.isBlank() ? "simple" : configured.trim();
    }

    private String resolveKeywordRankFunction() {
        String configured = ragRetrievalProperties.getKeywordRankFunction();
        return "ts_rank".equalsIgnoreCase(configured) ? "ts_rank" : "ts_rank_cd";
    }

    private double resolveLikePhraseWeight() {
        Double configured = ragRetrievalProperties.getKeywordLikePhraseWeight();
        return configured == null || configured <= 0D ? 3D : configured;
    }

    private double resolveLikeTitleWeight() {
        Double configured = ragRetrievalProperties.getKeywordLikeTitleWeight();
        return configured == null || configured <= 0D ? 1.5D : configured;
    }

    private double resolveKeywordMinHitThreshold() {
        Double configured = ragRetrievalProperties.getKeywordMinHitThreshold();
        return configured == null || configured < 0D ? 0.5D : configured;
    }

    /** 为中文连续片段生成有限数量的关键词窗口，避免 term 数量过多。 */
    private void addChineseTerms(String segment,
                                 LinkedHashSet<String> terms,
                                 int minTokenLength,
                                 int maxWindowLength,
                                 int maxTerms) {
        int effectiveMaxWindow = Math.min(maxWindowLength, segment.length());
        for (int windowLength = effectiveMaxWindow; windowLength >= minTokenLength; windowLength--) {
            for (int start = 0; start + windowLength <= segment.length(); start++) {
                terms.add(segment.substring(start, start + windowLength));
                if (terms.size() >= maxTerms) {
                    return;
                }
            }
        }
    }

    /** 把向量转换成 pgvector 可消费的字面量格式。 */
    private String toVectorLiteral(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new BusinessException("Query embedding vector must not be empty");
        }
        return "[" + vector.stream()
                .map(value -> String.format(Locale.ROOT, "%.12f", value))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow() + "]";
    }

    /** 使用 RRF 融合 dense 和 keyword 双路召回结果。 */
    private List<RetrievedChunkResponse> fuseCandidates(List<RetrievedChunkCandidate> denseCandidates,
                                                        List<RetrievedChunkCandidate> keywordCandidates,
                                                        int topK) {
        Map<Long, FusedChunkCandidate> fused = new LinkedHashMap<>();
        for (int i = 0; i < denseCandidates.size(); i++) {
            RetrievedChunkCandidate candidate = denseCandidates.get(i);
            fused.computeIfAbsent(candidate.getId(), ignored -> new FusedChunkCandidate(candidate))
                    .setDenseRank(i + 1);
        }
        for (int i = 0; i < keywordCandidates.size(); i++) {
            RetrievedChunkCandidate candidate = keywordCandidates.get(i);
            FusedChunkCandidate fusedCandidate = fused.computeIfAbsent(candidate.getId(), ignored -> new FusedChunkCandidate(candidate));
            if (fusedCandidate.getCandidate().getEmbeddingModel() == null && candidate.getEmbeddingModel() != null) {
                fusedCandidate.setCandidate(candidate);
            }
            fusedCandidate.setKeywordRank(i + 1);
        }
        int fusionK = resolveFusionK();
        return fused.values().stream()
                .peek(candidate -> candidate.setFusedScore(computeRrfScore(candidate.getDenseRank(), candidate.getKeywordRank(), fusionK)))
                .sorted(Comparator
                        .comparingDouble(FusedChunkCandidate::getFusedScore).reversed()
                        .thenComparing(FusedChunkCandidate::getDenseRank, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(FusedChunkCandidate::getKeywordRank, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(candidate -> candidate.getCandidate().getId()))
                .limit(topK)
                .map(candidate -> toRetrievedChunkResponse(candidate.getCandidate(), candidate.getFusedScore()))
                .toList();
    }

    /** 计算单个候选的 RRF 融合分数。 */
    private double computeRrfScore(Integer denseRank, Integer keywordRank, int fusionK) {
        double denseScore = denseRank == null ? 0D : 1D / (fusionK + denseRank);
        double keywordScore = keywordRank == null ? 0D : 1D / (fusionK + keywordRank);
        return denseScore + keywordScore;
    }

    /** 把持久层候选结果转换成接口返回对象。 */
    private RetrievedChunkResponse toRetrievedChunkResponse(RetrievedChunkCandidate chunk) {
        return toRetrievedChunkResponse(chunk, chunk.getScore());
    }

    /** 把持久层候选结果转换成接口返回对象，并允许覆盖输出分数语义。 */
    private RetrievedChunkResponse toRetrievedChunkResponse(RetrievedChunkCandidate chunk, Double score) {
        return new RetrievedChunkResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getDocumentCode(),
                chunk.getDocumentName(),
                chunk.getChunkIndex(),
                chunk.getChunkType(),
                chunk.getContent(),
                chunk.getStartOffset(),
                chunk.getEndOffset(),
                chunk.getEmbeddingModel(),
                score
        );
    }

    /** 融合阶段使用的临时候选结构。 */
    private static final class FusedChunkCandidate {
        private RetrievedChunkCandidate candidate;
        private Integer denseRank;
        private Integer keywordRank;
        private double fusedScore;

        private FusedChunkCandidate(RetrievedChunkCandidate candidate) {
            this.candidate = candidate;
        }

        private RetrievedChunkCandidate getCandidate() {
            return candidate;
        }

        private void setCandidate(RetrievedChunkCandidate candidate) {
            this.candidate = candidate;
        }

        private Integer getDenseRank() {
            return denseRank;
        }

        private void setDenseRank(Integer denseRank) {
            this.denseRank = denseRank;
        }

        private Integer getKeywordRank() {
            return keywordRank;
        }

        private void setKeywordRank(Integer keywordRank) {
            this.keywordRank = keywordRank;
        }

        private double getFusedScore() {
            return fusedScore;
        }

        private void setFusedScore(double fusedScore) {
            this.fusedScore = fusedScore;
        }
    }
}

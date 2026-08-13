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
import com.example.rag.model.enums.RerankStatus;
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
import java.util.HashSet;
import java.util.Set;
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
            key = "#kbCode + ':' + (#question == null ? 'null' : #question.trim()) + ':' + (#topK == null ? 'null' : #topK) + ':' + (#retrievalMode == null ? 'AUTO' : #retrievalMode.name()) + ':' + #root.target.currentRetrievalPolicySignature()",
            unless = "#result.rerankStatus().name() == 'DEGRADED'"
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
        boolean rerankEnabled = isRerankEnabled();
        int rerankCandidateLimit = rerankEnabled ? resolveRerankCandidateLimit(resolvedTopK) : resolvedTopK;
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
                resolvedRetrievalMode == RetrievalMode.HYBRID
                        ? Math.max(resolveDenseCandidateLimit(resolvedTopK), rerankCandidateLimit)
                        : rerankCandidateLimit
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
        List<RetrievedChunkResponse> preRerankChunks;
        if (resolvedRetrievalMode == RetrievalMode.HYBRID) {
            long keywordStartedAt = System.currentTimeMillis();
            keywordCandidates = findTopKeywordChunks(
                    knowledgeBase.getId(),
                    normalizedQuestion,
                    keywordStrategy,
                    Math.max(resolveKeywordCandidateLimit(resolvedTopK), rerankCandidateLimit)
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
            preRerankChunks = fuseCandidates(denseCandidates, keywordCandidates, rerankCandidateLimit);
            fusionDurationMs = System.currentTimeMillis() - fusionStartedAt;
            fusionStrategy = FUSION_STRATEGY_RRF;
            log.info(StructuredLogMessage.of("qa.retrieve.fusion.completed")
                    .field("kbCode", kbCode)
                    .field("retrievalMode", resolvedRetrievalMode.name())
                    .field("keywordStrategy", keywordStrategy.name())
                    .field("denseCandidateCount", denseCandidates.size())
                    .field("keywordCandidateCount", keywordCandidates.size())
                    .field("candidateCount", preRerankChunks.size())
                    .field("durationMs", fusionDurationMs)
                    .build());
        } else {
            preRerankChunks = denseCandidates.stream()
                    .limit(rerankCandidateLimit)
                    .map(this::toRetrievedChunkResponse)
                    .toList();
        }

        RerankOutcome rerankOutcome = applyRerank(normalizedQuestion, preRerankChunks, resolvedTopK);
        List<RetrievedChunkResponse> chunks = rerankOutcome.chunks();

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
                .field("rerankStatus", rerankOutcome.status().name())
                .field("rerankModel", rerankOutcome.model())
                .field("rerankCandidateCount", rerankOutcome.candidateCount())
                .field("rerankDurationMs", rerankOutcome.durationMs())
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
                rerankOutcome.status(),
                rerankOutcome.model(),
                rerankOutcome.candidateCount(),
                rerankOutcome.durationMs(),
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

    /** 暴露给缓存 SpEL 使用的检索策略签名，避免配置切换后命中旧排序。 */
    public String currentRetrievalPolicySignature() {
        RagRetrievalProperties.Rerank rerank = rerankProperties();
        return resolveKeywordStrategy().name()
                + ":" + rerank.isEnabled()
                + ":" + normalizeRerankModel(rerank.getModel())
                + ":" + rerank.getCandidateLimit()
                + ":" + normalizePolicyVersion(rerank.getPolicyVersion())
                + ":" + normalizeInstruct(rerank.getInstruct()).hashCode();
    }

    /** 判断当前是否启用召回后重排序。 */
    private boolean isRerankEnabled() {
        return rerankProperties().isEnabled();
    }

    /** 解析重排序候选数，并确保不少于调用方要求的最终 topK。 */
    private int resolveRerankCandidateLimit(int topK) {
        Integer configured = rerankProperties().getCandidateLimit();
        int candidateLimit = configured == null ? 20 : configured;
        return Math.min(50, Math.max(topK, candidateLimit));
    }

    /** 返回非空的重排序配置对象。 */
    private RagRetrievalProperties.Rerank rerankProperties() {
        return ragRetrievalProperties.getRerank() == null
                ? new RagRetrievalProperties.Rerank()
                : ragRetrievalProperties.getRerank();
    }

    /** 解析重排序模型名。 */
    private String resolveRerankModel() {
        return normalizeRerankModel(rerankProperties().getModel());
    }

    private String normalizeRerankModel(String model) {
        return model == null || model.isBlank() ? "qwen3-rerank" : model.trim();
    }

    private String normalizeInstruct(String instruct) {
        return instruct == null ? "" : instruct.trim();
    }

    private String normalizePolicyVersion(String version) {
        return version == null || version.isBlank() ? "rerank-v1" : version.trim();
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

    /** 判断问题中是否包含连续中文字符。 */
    private boolean containsCjk(String normalizedQuestion) {
        return CJK_SEGMENT_PATTERN.matcher(normalizedQuestion).find();
    }

    /** 解析 PostgreSQL FTS 配置名，空值回退为 simple。 */
    private String resolveKeywordTsConfig() {
        String configured = ragRetrievalProperties.getKeywordTsConfig();
        return configured == null || configured.isBlank() ? "simple" : configured.trim();
    }

    /** 解析 FTS 排名函数，仅允许 ts_rank 或默认 ts_rank_cd。 */
    private String resolveKeywordRankFunction() {
        String configured = ragRetrievalProperties.getKeywordRankFunction();
        return "ts_rank".equalsIgnoreCase(configured) ? "ts_rank" : "ts_rank_cd";
    }

    /** 解析 LIKE 短语命中的权重。 */
    private double resolveLikePhraseWeight() {
        Double configured = ragRetrievalProperties.getKeywordLikePhraseWeight();
        return configured == null || configured <= 0D ? 3D : configured;
    }

    /** 解析 LIKE 标题命中的权重。 */
    private double resolveLikeTitleWeight() {
        Double configured = ragRetrievalProperties.getKeywordLikeTitleWeight();
        return configured == null || configured <= 0D ? 1.5D : configured;
    }

    /** 解析 keyword 候选最低命中阈值。 */
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

    /** 在召回或融合候选集上执行重排序；供应商失败时保留原排序。 */
    private RerankOutcome applyRerank(String question,
                                      List<RetrievedChunkResponse> candidates,
                                      int topK) {
        if (!isRerankEnabled()) {
            return new RerankOutcome(
                    RerankStatus.DISABLED,
                    null,
                    0,
                    0L,
                    candidates.stream().limit(topK).toList()
            );
        }
        String model = resolveRerankModel();
        if (candidates.isEmpty()) {
            return new RerankOutcome(RerankStatus.SKIPPED_EMPTY, model, 0, 0L, List.of());
        }

        long startedAt = System.currentTimeMillis();
        log.info(StructuredLogMessage.of("qa.rerank.started")
                .field("model", model)
                .field("candidateCount", candidates.size())
                .field("topK", topK)
                .build());
        try {
            List<String> documents = candidates.stream()
                    .map(this::toRerankDocument)
                    .toList();
            AiGatewayClient.RerankGatewayResponse response = aiGatewayClient.createRerank(
                    model,
                    question,
                    documents,
                    Math.min(topK, candidates.size()),
                    normalizeInstruct(rerankProperties().getInstruct())
            );
            List<RetrievedChunkResponse> reranked = validateAndMapRerankResults(response, candidates, topK);
            long durationMs = System.currentTimeMillis() - startedAt;
            String actualModel = response.model() == null || response.model().isBlank() ? model : response.model();
            log.info(StructuredLogMessage.of("qa.rerank.completed")
                    .field("model", actualModel)
                    .field("candidateCount", candidates.size())
                    .field("resultCount", reranked.size())
                    .field("durationMs", durationMs)
                    .build());
            return new RerankOutcome(
                    RerankStatus.APPLIED,
                    actualModel,
                    candidates.size(),
                    durationMs,
                    reranked
            );
        } catch (BusinessException ex) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.warn(StructuredLogMessage.of("qa.rerank.degraded")
                    .field("model", model)
                    .field("candidateCount", candidates.size())
                    .field("durationMs", durationMs)
                    .field("reason", ex.getMessage())
                    .build());
            return new RerankOutcome(
                    RerankStatus.DEGRADED,
                    model,
                    candidates.size(),
                    durationMs,
                    candidates.stream().limit(topK).toList()
            );
        }
    }

    /** 为 rerank 提供标题和正文，同时不改变 chunk 的稳定映射关系。 */
    private String toRerankDocument(RetrievedChunkResponse chunk) {
        return "Document title: " + chunk.documentName() + "\nPassage: " + chunk.content();
    }

    /** 验证供应商 index/score 契约并映射回原 chunk。 */
    private List<RetrievedChunkResponse> validateAndMapRerankResults(
            AiGatewayClient.RerankGatewayResponse response,
            List<RetrievedChunkResponse> candidates,
            int topK) {
        int expectedCount = Math.min(topK, candidates.size());
        if (response == null || response.results() == null || response.results().size() != expectedCount) {
            throw new BusinessException("Rerank result count does not match requested top_n");
        }
        Set<Integer> seenIndexes = new HashSet<>();
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (AiGatewayClient.RerankGatewayResult result : response.results()) {
            Integer index = result == null ? null : result.index();
            Double score = result == null ? null : result.relevanceScore();
            if (index == null || index < 0 || index >= candidates.size() || !seenIndexes.add(index)) {
                throw new BusinessException("Rerank response contains an invalid or duplicate index");
            }
            if (score == null || !Double.isFinite(score) || score < 0D || score > 1D) {
                throw new BusinessException("Rerank response contains an invalid score");
            }
            scoredChunks.add(new ScoredChunk(index, score, withRerankScore(candidates.get(index), score)));
        }
        scoredChunks.sort((left, right) -> {
            int scoreComparison = Double.compare(right.score(), left.score());
            return scoreComparison != 0
                    ? scoreComparison
                    : Integer.compare(left.originalIndex(), right.originalIndex());
        });
        List<RetrievedChunkResponse> rerankedChunks = new ArrayList<>(scoredChunks.size());
        for (ScoredChunk scoredChunk : scoredChunks) {
            rerankedChunks.add(scoredChunk.chunk());
        }
        return List.copyOf(rerankedChunks);
    }

    /** 复制 chunk 并附加本次重排序分数。 */
    private RetrievedChunkResponse withRerankScore(RetrievedChunkResponse chunk, Double rerankScore) {
        return new RetrievedChunkResponse(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.documentCode(),
                chunk.documentName(),
                chunk.chunkIndex(),
                chunk.chunkType(),
                chunk.content(),
                chunk.startOffset(),
                chunk.endOffset(),
                chunk.embeddingModel(),
                chunk.score(),
                rerankScore
        );
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
                        .comparingDouble((FusedChunkCandidate candidate) -> candidate.getFusedScore()).reversed()
                        .thenComparing(candidate -> candidate.getDenseRank(),
                                Comparator.nullsLast((left, right) -> Integer.compare(left, right)))
                        .thenComparing(candidate -> candidate.getKeywordRank(),
                                Comparator.nullsLast((left, right) -> Integer.compare(left, right)))
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

        /** 返回当前用于输出的候选 chunk。 */
        private RetrievedChunkCandidate getCandidate() {
            return candidate;
        }

        /** 在另一召回分支信息更完整时替换候选对象。 */
        private void setCandidate(RetrievedChunkCandidate candidate) {
            this.candidate = candidate;
        }

        /** 返回候选在 Dense 召回中的名次。 */
        private Integer getDenseRank() {
            return denseRank;
        }

        /** 记录候选在 Dense 召回中的名次。 */
        private void setDenseRank(Integer denseRank) {
            this.denseRank = denseRank;
        }

        /** 返回候选在 keyword 召回中的名次。 */
        private Integer getKeywordRank() {
            return keywordRank;
        }

        /** 记录候选在 keyword 召回中的名次。 */
        private void setKeywordRank(Integer keywordRank) {
            this.keywordRank = keywordRank;
        }

        /** 返回根据双路名次计算的 RRF 分数。 */
        private double getFusedScore() {
            return fusedScore;
        }

        /** 保存根据双路名次计算的 RRF 分数。 */
        private void setFusedScore(double fusedScore) {
            this.fusedScore = fusedScore;
        }
    }

    /** 一次重排序阶段的内部结果。 */
    private record RerankOutcome(
            RerankStatus status,
            String model,
            int candidateCount,
            long durationMs,
            List<RetrievedChunkResponse> chunks
    ) {
    }

    /** 带原始名次的重排序候选，用于同分时稳定排序。 */
    private record ScoredChunk(
            int originalIndex,
            double score,
            RetrievedChunkResponse chunk
    ) {
    }
}

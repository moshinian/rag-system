package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.request.AgentRunCreateRequest;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责在独立事务中创建 Agent run 记录。
 */
@Service
public class AgentRunRecordService {
    private static final String RUN_CODE_PREFIX = "AR-";

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AgentRunRepository agentRunRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /** 构造 AgentRunRecordService。 */
    public AgentRunRecordService(KnowledgeBaseRepository knowledgeBaseRepository,
                                 AgentRunRepository agentRunRepository,
                                 SnowflakeIdGenerator snowflakeIdGenerator) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.agentRunRepository = agentRunRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /**
     * 创建 QUEUED run；任意 Pod 提交后均由集群 Worker Claim。
     */
    @Transactional
    public AgentRunEntity create(String kbCode, AgentRunCreateRequest request) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));

        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setRunCode(snowflakeIdGenerator.nextId(RUN_CODE_PREFIX));
        entity.setKnowledgeBaseId(knowledgeBase.getId());
        entity.setGoal(request.goal().trim());
        entity.setQuestion(trimToNull(request.question()));
        entity.setStatus(AgentRunStatus.QUEUED);
        entity.setRuntimeHeartbeatAt(null);
        entity.setCreatedBy(defaultCreatedBy(request.createdBy()));
        return agentRunRepository.insert(entity);
    }

    /** 把空白文本转换成 null。 */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 缺省创建人统一记为 system。 */
    private String defaultCreatedBy(String createdBy) {
        String normalized = trimToNull(createdBy);
        return normalized == null ? "system" : normalized;
    }
}

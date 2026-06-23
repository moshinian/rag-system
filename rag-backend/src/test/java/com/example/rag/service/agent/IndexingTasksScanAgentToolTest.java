package com.example.rag.service.agent;

import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.model.enums.IndexingTaskStage;
import com.example.rag.model.enums.IndexingTaskStatus;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.IndexingTaskRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.IndexingTaskEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Agent 索引任务扫描工具测试。 */
class IndexingTasksScanAgentToolTest {

    @Test
    void executeShouldReturnTaskStatusSummaryAndFailedSamples() throws Exception {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        IndexingTaskRepository indexingTaskRepository = mock(IndexingTaskRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        IndexingTasksScanAgentTool tool = new IndexingTasksScanAgentTool(
                knowledgeBaseRepository,
                documentRepository,
                indexingTaskRepository
        );
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(indexingTaskRepository.findByKnowledgeBaseIdAndStatusesOrderByCreatedAtDesc(eq(1L), anyList(), eq(50)))
                .thenReturn(List.of(
                        task(100L, 10L, IndexingTaskStatus.SUCCEEDED, null),
                        task(101L, 11L, IndexingTaskStatus.FAILED, "embedding failed")
                ));
        when(documentRepository.findById(11L)).thenReturn(Optional.of(document(11L, "DOC-2")));

        McpToolResult result = tool.call(McpToolContext.forKnowledgeBase("day20-cn-kb"));

        assertThat(!result.isError()).isTrue();
        assertThat(result.toolName()).isEqualTo(IndexingTasksScanAgentTool.TOOL_NAME);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result.structuredContent()));
        assertThat(json.get("kbCode").asText()).isEqualTo("day20-cn-kb");
        assertThat(json.get("scannedTaskCount").asInt()).isEqualTo(2);
        assertThat(json.get("statusCounts").get("SUCCEEDED").asLong()).isEqualTo(1L);
        assertThat(json.get("statusCounts").get("FAILED").asLong()).isEqualTo(1L);
        assertThat(json.get("failedTasks")).hasSize(1);
        assertThat(json.get("failedTasks").get(0).get("taskId").asLong()).isEqualTo(101L);
        assertThat(json.get("failedTasks").get(0).get("documentCode").asText()).isEqualTo("DOC-2");
        assertThat(json.get("failedTasks").get(0).get("errorMessage").asText()).isEqualTo("embedding failed");
    }

    @Test
    void definitionShouldDeclareReadOnlyLowRiskTool() {
        IndexingTasksScanAgentTool tool = new IndexingTasksScanAgentTool(
                mock(KnowledgeBaseRepository.class),
                mock(DocumentRepository.class),
                mock(IndexingTaskRepository.class)
        );

        assertThat(tool.definition().name()).isEqualTo(IndexingTasksScanAgentTool.TOOL_NAME);
        assertThat(tool.definition().executionMode()).isEqualTo(AgentToolExecutionMode.READ_ONLY);
        assertThat(tool.definition().maxRiskLevel()).isEqualTo(AgentActionRiskLevel.LOW);
    }

    private KnowledgeBaseEntity knowledgeBase(Long id, String kbCode) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setKbCode(kbCode);
        return entity;
    }

    private DocumentEntity document(Long id, String documentCode) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(id);
        entity.setDocumentCode(documentCode);
        return entity;
    }

    private IndexingTaskEntity task(Long id, Long documentId, IndexingTaskStatus status, String errorMessage) {
        IndexingTaskEntity entity = new IndexingTaskEntity();
        entity.setId(id);
        entity.setKnowledgeBaseId(1L);
        entity.setDocumentId(documentId);
        entity.setTaskType("DOCUMENT_INDEXING");
        entity.setTaskStage(status == IndexingTaskStatus.FAILED ? IndexingTaskStage.DOCUMENT_EMBEDDING : IndexingTaskStage.COMPLETED);
        entity.setStatus(status);
        entity.setRetryCount(1);
        entity.setMaxRetryCount(3);
        entity.setErrorMessage(errorMessage);
        return entity;
    }
}

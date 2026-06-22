package com.example.rag.service.agent;

import com.example.rag.model.dto.AgentToolContext;
import com.example.rag.model.dto.AgentToolResult;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentToolExecutionMode;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Agent 文档状态扫描工具测试。 */
class DocumentsStatusAgentToolTest {

    @Test
    void executeShouldReturnDocumentStatusSummaryAndFailedSamples() throws Exception {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DocumentsStatusAgentTool tool = new DocumentsStatusAgentTool(
                knowledgeBaseRepository,
                documentRepository,
                objectMapper
        );
        KnowledgeBaseEntity knowledgeBase = knowledgeBase(1L, "day20-cn-kb");
        when(knowledgeBaseRepository.findByCode("day20-cn-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.findByKnowledgeBaseId(1L)).thenReturn(List.of(
                document(10L, "DOC-1", "结算规则.pdf", DocumentStatus.INDEXED, null),
                document(11L, "DOC-2", "坏文档.pdf", DocumentStatus.FAILED, "parse failed")
        ));

        AgentToolResult result = tool.execute(AgentToolContext.forKnowledgeBase("day20-cn-kb"));

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo(DocumentsStatusAgentTool.TOOL_NAME);
        JsonNode json = objectMapper.readTree(result.outputJson());
        assertThat(json.get("kbCode").asText()).isEqualTo("day20-cn-kb");
        assertThat(json.get("totalDocumentCount").asInt()).isEqualTo(2);
        assertThat(json.get("statusCounts").get("INDEXED").asLong()).isEqualTo(1L);
        assertThat(json.get("statusCounts").get("FAILED").asLong()).isEqualTo(1L);
        assertThat(json.get("failedDocuments")).hasSize(1);
        assertThat(json.get("failedDocuments").get(0).get("documentCode").asText()).isEqualTo("DOC-2");
        assertThat(json.get("failedDocuments").get(0).get("errorMessage").asText()).isEqualTo("parse failed");
    }

    @Test
    void definitionShouldDeclareReadOnlyLowRiskTool() {
        DocumentsStatusAgentTool tool = new DocumentsStatusAgentTool(
                mock(KnowledgeBaseRepository.class),
                mock(DocumentRepository.class),
                new ObjectMapper()
        );

        assertThat(tool.definition().toolName()).isEqualTo(DocumentsStatusAgentTool.TOOL_NAME);
        assertThat(tool.definition().executionMode()).isEqualTo(AgentToolExecutionMode.READ_ONLY);
        assertThat(tool.definition().maxRiskLevel()).isEqualTo(AgentActionRiskLevel.LOW);
    }

    private KnowledgeBaseEntity knowledgeBase(Long id, String kbCode) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setKbCode(kbCode);
        return entity;
    }

    private DocumentEntity document(Long id, String documentCode, String displayName, DocumentStatus status, String errorMessage) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(id);
        entity.setDocumentCode(documentCode);
        entity.setDisplayName(displayName);
        entity.setStatus(status);
        entity.setErrorMessage(errorMessage);
        return entity;
    }
}

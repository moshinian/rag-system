package com.example.rag.service;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.request.AgentRunCreateRequest;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Agent run 创建记录测试。 */
@ExtendWith(MockitoExtension.class)
class AgentRunRecordServiceTest {
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private AgentRunRepository runRepository;
    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Test
    void createShouldInitializeRuntimeHeartbeatAt() {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(1L);
        kb.setKbCode("kb-1");
        when(knowledgeBaseRepository.findByCode("kb-1")).thenReturn(Optional.of(kb));
        when(idGenerator.nextId()).thenReturn(100L);
        when(idGenerator.nextId("AR-")).thenReturn("AR-1");
        ArgumentCaptor<AgentRunEntity> captor = ArgumentCaptor.forClass(AgentRunEntity.class);
        when(runRepository.insert(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        AgentRunRecordService service = new AgentRunRecordService(
                knowledgeBaseRepository,
                runRepository,
                idGenerator
        );

        AgentRunEntity run = service.create("kb-1", new AgentRunCreateRequest(
                "诊断",
                null,
                "tester"
        ));

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(captor.getValue().getRuntimeHeartbeatAt()).isNotNull();
    }
}

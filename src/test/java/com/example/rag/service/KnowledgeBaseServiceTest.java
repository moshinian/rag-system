package com.example.rag.service;

import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.KnowledgeBaseResponse;
import com.example.rag.model.response.PageResponse;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.query.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private com.example.rag.common.id.SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    @Test
    void listShouldReturnPagedKnowledgeBases() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setName("Settlement KB");
        entity.setDescription("desc");
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);
        entity.setCreatedBy("tester");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        when(knowledgeBaseRepository.page(any()))
                .thenReturn(new PageResult<>(List.of(entity), 1, 1, 20));

        PageResponse<KnowledgeBaseResponse> response = knowledgeBaseService.list("active", 1L, 20L);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).singleElement()
                .extracting(KnowledgeBaseResponse::kbCode, KnowledgeBaseResponse::status)
                .containsExactly("settlement-kb", "ACTIVE");
    }

    @Test
    void getShouldReturnKnowledgeBaseDetail() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setName("Settlement KB");
        entity.setDescription("desc");
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);
        entity.setCreatedBy("tester");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(entity));

        KnowledgeBaseResponse response = knowledgeBaseService.get("settlement-kb");

        assertThat(response.kbCode()).isEqualTo("settlement-kb");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void disableShouldUpdateKnowledgeBaseStatus() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(entity));
        when(knowledgeBaseRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseResponse response = knowledgeBaseService.disable("settlement-kb");

        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void enableShouldUpdateKnowledgeBaseStatus() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setKbCode("settlement-kb");
        entity.setStatus(KnowledgeBaseStatus.INACTIVE);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(entity));
        when(knowledgeBaseRepository.updateById(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseResponse response = knowledgeBaseService.enable("settlement-kb");

        assertThat(response.status()).isEqualTo("ACTIVE");
    }
}

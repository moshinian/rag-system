package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.config.RagEmbeddingProperties;
import com.example.rag.integration.llm.OpenAiCompatibleClient;
import com.example.rag.model.enums.DocumentChunkStatus;
import com.example.rag.model.enums.DocumentStatus;
import com.example.rag.model.enums.EmbeddingStatus;
import com.example.rag.model.enums.KnowledgeBaseStatus;
import com.example.rag.model.response.DocumentEmbeddingResponse;
import com.example.rag.persistence.DocumentChunkRepository;
import com.example.rag.persistence.DocumentRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.DocumentChunkEntity;
import com.example.rag.persistence.entity.DocumentEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentEmbeddingServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private OpenAiCompatibleClient openAiCompatibleClient;

    private DocumentEmbeddingService documentEmbeddingService;

    @BeforeEach
    void setUp() {
        RagEmbeddingProperties properties = new RagEmbeddingProperties();
        properties.setBaseUrl("http://localhost:8001/v1");
        properties.setApiKey("");
        properties.setModel("bge-small-zh-v1.5");
        properties.setVectorDimensions(512);
        properties.setEmbeddingPath("/embeddings");
        properties.setBatchSize(16);
        documentEmbeddingService = new DocumentEmbeddingService(
                knowledgeBaseRepository,
                documentRepository,
                documentChunkRepository,
                properties,
                openAiCompatibleClient
        );
    }

    @Test
    void embedShouldWriteVectorsForPendingChunks() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        DocumentEntity document = new DocumentEntity();
        document.setId(200L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");
        document.setStatus(DocumentStatus.INDEXED);

        DocumentChunkEntity chunk1 = createChunk(1L, 200L, 0, "第一段");
        DocumentChunkEntity chunk2 = createChunk(2L, 200L, 1, "第二段");
        DocumentChunkEntity chunk3 = createChunk(3L, 200L, 2, "第三段");

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb")).thenReturn(Optional.of(document));
        when(documentChunkRepository.findEmbeddableChunksByDocumentId(eq(200L), any(), eq(16)))
                .thenReturn(List.of(chunk1, chunk2))
                .thenReturn(List.of(chunk3))
                .thenReturn(List.of());
        when(openAiCompatibleClient.createEmbeddings(
                eq("http://localhost:8001/v1"),
                eq(""),
                eq("/embeddings"),
                eq("bge-small-zh-v1.5"),
                eq(List.of("第一段", "第二段"))
        )).thenReturn(List.of(
                List.of(0.1D, 0.2D),
                List.of(0.3D, 0.4D)
        ));
        when(openAiCompatibleClient.createEmbeddings(
                eq("http://localhost:8001/v1"),
                eq(""),
                eq("/embeddings"),
                eq("bge-small-zh-v1.5"),
                eq(List.of("第三段"))
        )).thenReturn(List.of(
                List.of(0.5D, 0.6D)
        ));
        when(documentChunkRepository.countByDocumentIdAndEmbeddingStatus(200L, EmbeddingStatus.EMBEDDED)).thenReturn(3L);

        DocumentEmbeddingResponse response = documentEmbeddingService.embed("settlement-kb", "DOC-1");

        assertThat(response.embeddedChunkCount()).isEqualTo(3);
        assertThat(response.failedChunkCount()).isZero();
        assertThat(response.totalEmbeddedChunkCount()).isEqualTo(3L);
        verify(documentChunkRepository).updateEmbeddingVector(eq(1L), eq(EmbeddingStatus.EMBEDDED), eq("bge-small-zh-v1.5"), eq("[0.100000000000,0.200000000000]"), any());
        verify(documentChunkRepository).updateEmbeddingVector(eq(2L), eq(EmbeddingStatus.EMBEDDED), eq("bge-small-zh-v1.5"), eq("[0.300000000000,0.400000000000]"), any());
        verify(documentChunkRepository).updateEmbeddingVector(eq(3L), eq(EmbeddingStatus.EMBEDDED), eq("bge-small-zh-v1.5"), eq("[0.500000000000,0.600000000000]"), any());
    }

    @Test
    void embedShouldRejectWhenDocumentIsNotIndexed() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(100L);
        knowledgeBase.setKbCode("settlement-kb");
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);

        DocumentEntity document = new DocumentEntity();
        document.setId(200L);
        document.setKnowledgeBaseId(100L);
        document.setDocumentCode("DOC-1");
        document.setStatus(DocumentStatus.UPLOADED);

        when(knowledgeBaseRepository.findByCode("settlement-kb")).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.findByCodeInKnowledgeBase("DOC-1", "settlement-kb")).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> documentEmbeddingService.embed("settlement-kb", "DOC-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Document must be INDEXED");
    }

    private DocumentChunkEntity createChunk(Long id, Long documentId, int chunkIndex, String content) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setId(id);
        entity.setDocumentId(documentId);
        entity.setChunkIndex(chunkIndex);
        entity.setContent(content);
        entity.setStatus(DocumentChunkStatus.ACTIVE);
        entity.setEmbeddingStatus(EmbeddingStatus.PENDING);
        return entity;
    }
}

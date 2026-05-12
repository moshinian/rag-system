package com.example.rag.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseLongIdSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldSerializeChunkIdentifiersAsStrings() throws Exception {
        DocumentChunkResponse response = new DocumentChunkResponse(
                311506087037767680L,
                311400183286075392L,
                59,
                "TEXT",
                "title",
                "content",
                7,
                12,
                0,
                99,
                null,
                "ACTIVE",
                "EMBEDDED",
                null,
                null,
                null,
                311600000000000001L,
                null,
                null,
                null,
                null
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("id").asText()).isEqualTo("311506087037767680");
        assertThat(json.get("documentId").isTextual()).isTrue();
        assertThat(json.get("documentId").asText()).isEqualTo("311400183286075392");
        assertThat(json.get("embeddingRebuildRunId").isTextual()).isTrue();
        assertThat(json.get("embeddingRebuildRunId").asText()).isEqualTo("311600000000000001");
        assertThat(json.get("chunkIndex").isInt()).isTrue();
    }

    @Test
    void shouldKeepPaginationNumbersNumeric() {
        PageResponse<String> response = new PageResponse<>(
                java.util.List.of("a", "b"),
                156L,
                1L,
                20L
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("total").isNumber()).isTrue();
        assertThat(json.get("pageNo").isNumber()).isTrue();
        assertThat(json.get("pageSize").isNumber()).isTrue();
    }

    @Test
    void shouldSerializeReadinessRunIdAsStringButKeepCountersNumeric() {
        QuestionAnsweringReadinessResponse response = new QuestionAnsweringReadinessResponse(
                "settlement-kb",
                "ACTIVE",
                true,
                "openai-compatible",
                "text-embedding-3-large",
                "text-embedding-3-large",
                3072,
                "pgvector",
                5,
                156L,
                156L,
                false,
                true,
                311700000000000001L,
                "ready"
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("currentRebuildRunId").isTextual()).isTrue();
        assertThat(json.get("currentRebuildRunId").asText()).isEqualTo("311700000000000001");
        assertThat(json.get("indexedChunkCount").isNumber()).isTrue();
        assertThat(json.get("embeddedChunkCount").isNumber()).isTrue();
    }
}

package com.example.rag.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLogMessageTest {

    @Test
    void buildShouldRenderKeyValuePairs() {
        String message = StructuredLogMessage.of("qa.ask.completed")
                .field("kbCode", "day14-kb")
                .field("retrievedChunkCount", 3)
                .field("message", "hello world")
                .build();

        assertThat(message).isEqualTo(
                "event=qa.ask.completed kbCode=day14-kb retrievedChunkCount=3 message=\"hello world\""
        );
    }
}

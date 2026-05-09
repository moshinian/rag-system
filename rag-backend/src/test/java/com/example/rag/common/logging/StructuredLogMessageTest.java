package com.example.rag.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 结构化日志消息构造器测试。 */
class StructuredLogMessageTest {

    @Test
    void buildShouldRenderKeyValuePairs() {
        // 结构化日志采用扁平 key=value 格式，便于日志平台直接检索。
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

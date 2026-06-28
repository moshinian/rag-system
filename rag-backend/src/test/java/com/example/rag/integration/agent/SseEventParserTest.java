package com.example.rag.integration.agent;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 内部 SSE parser 测试。 */
class SseEventParserTest {

    @Test
    void parserShouldSupportCommentsAndMultilineData() {
        SseEventParser parser = new SseEventParser();

        assertThat(parser.accept(": other comment")).isEmpty();
        assertThat(parser.accept("id: EVT-1")).isEmpty();
        assertThat(parser.accept("event: STEP_COMPLETED")).isEmpty();
        assertThat(parser.accept("data: {\"a\":1,")).isEmpty();
        assertThat(parser.accept("data: \"b\":2}")).isEmpty();
        Optional<SseEventParser.SseFrame> frame = parser.accept("");

        assertThat(frame).isPresent();
        assertThat(frame.orElseThrow().id()).isEqualTo("EVT-1");
        assertThat(frame.orElseThrow().event()).isEqualTo("STEP_COMPLETED");
        assertThat(frame.orElseThrow().data()).isEqualTo("{\"a\":1,\n\"b\":2}");
    }

    @Test
    void parserShouldFlushFrameAtEof() {
        SseEventParser parser = new SseEventParser();
        parser.accept("id: EVT-2");
        parser.accept("event: RUN_COMPLETED");
        parser.accept("data: {}");

        assertThat(parser.finish()).get()
                .extracting(frame -> frame.id())
                .isEqualTo("EVT-2");
    }

    @Test
    void parserShouldEmitHeartbeatImmediatelyWithoutWaitingForBlankLine() {
        SseEventParser parser = new SseEventParser();
        AtomicInteger heartbeatCount = new AtomicInteger();

        Optional<SseEventParser.SseFrame> frame = parser.accept(
                ": heartbeat",
                ignored -> heartbeatCount.incrementAndGet()
        );

        assertThat(frame).isEmpty();
        assertThat(heartbeatCount).hasValue(1);
        assertThat(parser.accept("")).isEmpty();
    }
}

package com.example.rag.common.id;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    private static final long BASE_TIMESTAMP = 1704067201000L;

    @Test
    void acceptsSmallClockRollbackIfTimeCatchesUpWithinWaitWindow() {
        TestSnowflakeIdGenerator generator = new TestSnowflakeIdGenerator(
                new SnowflakeIdProperties(1, 1),
                new long[]{BASE_TIMESTAMP, BASE_TIMESTAMP - 2L, BASE_TIMESTAMP + 1L}
        );

        long firstId = generator.nextId();
        long secondId = generator.nextId();

        assertTrue(firstId > 0L);
        assertTrue(secondId > firstId);
    }

    @Test
    void waitsForNextMillisWhenSequenceRollsOver() {
        TestSnowflakeIdGenerator generator = new TestSnowflakeIdGenerator(
                new SnowflakeIdProperties(1, 1),
                new long[]{BASE_TIMESTAMP, BASE_TIMESTAMP, BASE_TIMESTAMP + 1L}
        );
        ReflectionTestUtils.setField(generator, "lastTimestamp", BASE_TIMESTAMP);
        ReflectionTestUtils.setField(generator, "sequence", 4095L);

        long nextId = generator.nextId();

        assertTrue(nextId > 0L);
    }

    private static final class TestSnowflakeIdGenerator extends SnowflakeIdGenerator {

        private final Deque<Long> timestamps;
        private long currentTimestamp;

        private TestSnowflakeIdGenerator(SnowflakeIdProperties properties, long[] timestamps) {
            super(properties);
            this.timestamps = toDeque(timestamps);
        }

        @Override
        protected long timestamp() {
            if (!timestamps.isEmpty()) {
                currentTimestamp = timestamps.removeFirst();
            }
            return currentTimestamp;
        }

        private static Deque<Long> toDeque(long[] values) {
            Deque<Long> deque = new ArrayDeque<>(values.length);
            for (long value : values) {
                deque.addLast(value);
            }
            return deque;
        }
    }
}

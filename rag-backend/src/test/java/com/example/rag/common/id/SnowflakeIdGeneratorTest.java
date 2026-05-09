package com.example.rag.common.id;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 雪花 ID 生成器测试。
 */
class SnowflakeIdGeneratorTest {

    private static final long BASE_TIMESTAMP = 1704067201000L;

    @Test
    void acceptsSmallClockRollbackIfTimeCatchesUpWithinWaitWindow() {
        // 模拟轻微时钟回拨后又追平的场景。
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
        // 模拟同一毫秒内序列号耗尽，需要等待到下一毫秒。
        TestSnowflakeIdGenerator generator = new TestSnowflakeIdGenerator(
                new SnowflakeIdProperties(1, 1),
                new long[]{BASE_TIMESTAMP, BASE_TIMESTAMP, BASE_TIMESTAMP + 1L}
        );
        ReflectionTestUtils.setField(generator, "lastTimestamp", BASE_TIMESTAMP);
        ReflectionTestUtils.setField(generator, "sequence", 4095L);

        long nextId = generator.nextId();

        assertTrue(nextId > 0L);
    }

    /**
     * 可控时间戳的测试用生成器。
     */
    private static final class TestSnowflakeIdGenerator extends SnowflakeIdGenerator {

        private final Deque<Long> timestamps;
        private long currentTimestamp;

        private TestSnowflakeIdGenerator(SnowflakeIdProperties properties, long[] timestamps) {
            super(properties);
            this.timestamps = toDeque(timestamps);
        }

        /** 按预设顺序返回时间戳。 */
        @Override
        protected long timestamp() {
            if (!timestamps.isEmpty()) {
                currentTimestamp = timestamps.removeFirst();
            }
            return currentTimestamp;
        }

        /** 把数组转换成队列，便于按顺序消费。 */
        private static Deque<Long> toDeque(long[] values) {
            Deque<Long> deque = new ArrayDeque<>(values.length);
            for (long value : values) {
                deque.addLast(value);
            }
            return deque;
        }
    }
}

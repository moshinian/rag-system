package com.example.rag.integration.agent;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 最小 SSE 文本解析器，支持 id/event/data/comment 和空行提交。
 */
public class SseEventParser {
    private String id;
    private String event;
    private final StringBuilder data = new StringBuilder();
    private boolean hasField;

    /**
     * 接收一行 SSE 文本；空行表示当前 frame 结束。
     */
    public Optional<SseFrame> accept(String line) {
        return accept(line, ignored -> {
        });
    }

    /**
     * 接收一行 SSE 文本，并在读取到 heartbeat comment 时立即回调。
     *
     * <p>heartbeat 是 Python Runtime 的存活信号，不属于业务事件，不能等待空行提交。</p>
     */
    public Optional<SseFrame> accept(String line, Consumer<String> onHeartbeat) {
        if (line == null) {
            return finish();
        }
        if (line.isEmpty()) {
            return completeFrame();
        }
        if (line.startsWith(":")) {
            if ("heartbeat".equals(line.substring(1).trim())) {
                onHeartbeat.accept("heartbeat");
            }
            // heartbeat/comment 不属于业务事件。
            return Optional.empty();
        }

        int separator = line.indexOf(':');
        String field = separator < 0 ? line : line.substring(0, separator);
        String value = separator < 0 ? "" : line.substring(separator + 1);
        if (value.startsWith(" ")) {
            value = value.substring(1);
        }

        switch (field) {
            case "id" -> {
                id = value;
                hasField = true;
            }
            case "event" -> {
                event = value;
                hasField = true;
            }
            case "data" -> {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(value);
                hasField = true;
            }
            default -> {
                // 第一版忽略 retry 等当前协议未使用字段。
            }
        }
        return Optional.empty();
    }

    /** 在输入流 EOF 时提交尚未以空行结束的 frame。 */
    public Optional<SseFrame> finish() {
        return completeFrame();
    }

    /** 组装并清空当前 frame。 */
    private Optional<SseFrame> completeFrame() {
        if (!hasField) {
            reset();
            return Optional.empty();
        }
        SseFrame frame = new SseFrame(id, event, data.toString());
        reset();
        return Optional.of(frame);
    }

    /** 清理当前解析状态。 */
    private void reset() {
        id = null;
        event = null;
        data.setLength(0);
        hasField = false;
    }

    /** 一条解析完成的 SSE frame。 */
    public record SseFrame(String id, String event, String data) {
    }
}

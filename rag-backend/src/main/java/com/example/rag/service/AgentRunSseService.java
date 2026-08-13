package com.example.rag.service;

import com.example.rag.common.exception.BusinessException;
import com.example.rag.model.response.AgentRunEventResponse;
import com.example.rag.persistence.AgentRunRepository;
import com.example.rag.persistence.KnowledgeBaseRepository;
import com.example.rag.persistence.entity.AgentRunEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 面向 React 的 Agent SSE 订阅服务。
 */
@Service
public class AgentRunSseService {
    private static final Logger log = LoggerFactory.getLogger(AgentRunSseService.class);
    private static final long NO_TIMEOUT = 0L;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentRunEventService eventService;
    private final ConcurrentHashMap<String, RunChannel> channels = new ConcurrentHashMap<>();

    /** 构造 AgentRunSseService。 */
    public AgentRunSseService(KnowledgeBaseRepository knowledgeBaseRepository,
                              AgentRunRepository agentRunRepository,
                              AgentRunEventService eventService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.agentRunRepository = agentRunRepository;
        this.eventService = eventService;
    }

    /**
     * 创建 SSE 订阅并补发历史事件。
     *
     * <p>历史查询、注册和补发共用 run 级锁，避免注册窗口漏掉实时事件。</p>
     */
    public SseEmitter subscribe(String kbCode, String runCode, String lastEventId) {
        validateRunOwnership(kbCode, runCode);
        RunChannel channel = channels.computeIfAbsent(runCode, ignored -> new RunChannel());
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        Subscription subscription = new Subscription(emitter);
        registerCleanupCallbacks(runCode, channel, subscription);

        synchronized (channel.monitor) {
            subscription.lastDeliveredDatabaseId.set(
                    eventService.resolveDatabaseCursor(runCode, lastEventId));
            List<AgentRunEventResponse> history = eventService.findEventsAfter(runCode, lastEventId);
            channel.subscriptions.add(subscription);
            try {
                for (AgentRunEventResponse event : history) {
                    sendIfNeeded(subscription, event);
                }
                // JDT null analysis 对 record method reference 会产生 unchecked conversion 告警，lambda 可保持同等语义。
                if (history.stream().anyMatch(event -> event.terminal())) {
                    removeAndComplete(runCode, channel, subscription);
                }
            } catch (IOException ex) {
                removeAndCompleteWithError(runCode, channel, subscription, ex);
            }
        }
        return emitter;
    }

    /**
     * 向一个 run 的所有在线订阅者发送已提交事件。
     */
    public void publish(AgentRunEventResponse event) {
        RunChannel channel = channels.get(event.runCode());
        if (channel == null) {
            return;
        }
        synchronized (channel.monitor) {
            for (Subscription subscription : new ArrayList<>(channel.subscriptions)) {
                try {
                    sendIfNeeded(subscription, event);
                    if (event.terminal()) {
                        removeAndComplete(event.runCode(), channel, subscription);
                    }
                } catch (IOException ex) {
                    removeAndCompleteWithError(event.runCode(), channel, subscription, ex);
                }
            }
        }
    }

    /** 校验知识库和 run 的归属关系。 */
    private void validateRunOwnership(String kbCode, String runCode) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findByCode(kbCode)
                .orElseThrow(() -> new BusinessException("Knowledge base not found: " + kbCode));
        AgentRunEntity run = agentRunRepository.findByRunCode(runCode)
                .orElseThrow(() -> new BusinessException("Agent run not found: " + runCode));
        if (!knowledgeBase.getId().equals(run.getKnowledgeBaseId())) {
            throw new BusinessException("Agent run does not belong to knowledge base: " + runCode);
        }
    }

    /** 注册 emitter 生命周期清理回调。 */
    private void registerCleanupCallbacks(String runCode,
                                          RunChannel channel,
                                          Subscription subscription) {
        subscription.emitter.onCompletion(() -> removeSubscription(runCode, channel, subscription));
        subscription.emitter.onTimeout(() -> removeAndComplete(runCode, channel, subscription));
        subscription.emitter.onError(error -> removeSubscription(runCode, channel, subscription));
    }

    /** 仅发送数据库顺序大于当前订阅游标的事件。 */
    private void sendIfNeeded(Subscription subscription,
                              AgentRunEventResponse event) throws IOException {
        if (event.databaseId() <= subscription.lastDeliveredDatabaseId.get()) {
            return;
        }
        subscription.emitter.send(SseEmitter.event()
                .id(sseEventId(event))
                .name(sseEventName(event))
                .data(event));
        subscription.lastDeliveredDatabaseId.set(event.databaseId());
    }

    /** SseEmitter builder 要求非空 id，缺失时退化为空字符串。 */
    private @NonNull String sseEventId(AgentRunEventResponse event) {
        String eventId = event.eventId();
        return eventId == null ? "" : eventId;
    }

    /** SseEmitter builder 要求非空 event name，缺失时使用 message 兜底。 */
    private @NonNull String sseEventName(AgentRunEventResponse event) {
        if (event.type() == null) {
            return "message";
        }
        String eventName = event.type().name();
        return eventName == null ? "message" : eventName;
    }

    /** 移除并正常关闭订阅。 */
    private void removeAndComplete(String runCode,
                                   RunChannel channel,
                                   Subscription subscription) {
        removeSubscription(runCode, channel, subscription);
        subscription.emitter.complete();
    }

    /** 移除并以错误关闭订阅。 */
    private void removeAndCompleteWithError(String runCode,
                                            RunChannel channel,
                                            Subscription subscription,
                                            Exception error) {
        log.debug("Agent SSE connection closed: runCode={}, message={}", runCode, error.getMessage());
        removeSubscription(runCode, channel, subscription);
        subscription.emitter.completeWithError(error);
    }

    /** 从 run channel 中移除订阅，并在空闲时回收 channel。 */
    private void removeSubscription(String runCode,
                                    RunChannel channel,
                                    Subscription subscription) {
        synchronized (channel.monitor) {
            channel.subscriptions.remove(subscription);
            if (channel.subscriptions.isEmpty()) {
                channels.remove(runCode, channel);
            }
        }
    }

    /** 单个 run 的同步监视器和订阅集合。 */
    private static final class RunChannel {
        private final Object monitor = new Object();
        private final List<Subscription> subscriptions = new ArrayList<>();
    }

    /** 单个浏览器连接及其已发送数据库游标。 */
    private static final class Subscription {
        private final SseEmitter emitter;
        private final AtomicLong lastDeliveredDatabaseId = new AtomicLong(0L);

        private Subscription(SseEmitter emitter) {
            this.emitter = emitter;
        }
    }
}

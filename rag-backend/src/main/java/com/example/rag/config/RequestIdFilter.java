package com.example.rag.config;

import com.example.rag.common.id.SnowflakeIdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.example.rag.common.logging.StructuredLogMessage;

/**
 * 请求级 requestId 过滤器。
 *
 * 如果请求头没有携带 X-Request-Id，则自动生成一个。
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final ThreadPoolExecutor REQUEST_LOG_EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1024),
            requestLogThreadFactory(),
            new ThreadPoolExecutor.DiscardPolicy()
    );
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /** 构造RequestIdFilter。 */
    public RequestIdFilter(SnowflakeIdGenerator snowflakeIdGenerator) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    /** 为请求补充 requestId 并写回响应头。 */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            // 没有外部 requestId 时统一在入口生成，保证后续日志和异常响应都能串起来。
            requestId = snowflakeIdGenerator.nextId("REQ-");
        }

        // requestId 同时放入请求上下文和响应头，便于链路追踪。
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put("requestId", requestId);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("requestPath", request.getRequestURI());
        logAsync(StructuredLogMessage.of("http.request.started")
                .field("requestId", requestId)
                .field("method", request.getMethod())
                .field("path", request.getRequestURI())
                .field("query", request.getQueryString())
                .build());
        try {
            filterChain.doFilter(request, response);
        } finally {
            logAsync(StructuredLogMessage.of("http.request.completed")
                    .field("requestId", requestId)
                    .field("method", request.getMethod())
                    .field("path", request.getRequestURI())
                    .field("status", response.getStatus())
                    .field("durationMs", System.currentTimeMillis() - startedAt)
                    .build());
            MDC.remove("requestPath");
            MDC.remove("httpMethod");
            MDC.remove("requestId");
        }
    }

    /** 配置requestLogThreadFactory相关组件。 */
    private static ThreadFactory requestLogThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "rag-request-log");
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 避免请求线程被控制台/终端输出阻塞；日志系统卡住时最多丢弃请求访问日志，
     * 不影响接口本身继续处理。
     */
    private void logAsync(String message) {
        try {
            // 请求日志异步落盘，避免慢终端或阻塞输出反向拖慢接口响应。
            REQUEST_LOG_EXECUTOR.execute(() -> log.info(message));
        } catch (RejectedExecutionException ignored) {
            // 请求日志允许降级丢弃，优先保证请求线程不被阻塞。
        }
    }
}

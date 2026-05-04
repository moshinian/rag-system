package com.example.rag.config;

import com.example.rag.common.id.SnowflakeIdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public RequestIdFilter(SnowflakeIdGenerator snowflakeIdGenerator) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /** 为请求补充 requestId 并写回响应头。 */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = snowflakeIdGenerator.nextId("REQ-");
        }

        // requestId 同时放入请求上下文和响应头，便于链路追踪。
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put("requestId", requestId);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("requestPath", request.getRequestURI());
        log.info(StructuredLogMessage.of("http.request.started")
                .field("requestId", requestId)
                .field("method", request.getMethod())
                .field("path", request.getRequestURI())
                .field("query", request.getQueryString())
                .build());
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info(StructuredLogMessage.of("http.request.completed")
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
}

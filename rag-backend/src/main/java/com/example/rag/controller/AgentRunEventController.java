package com.example.rag.controller;

import com.example.rag.service.AgentRunSseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 运行事件 SSE 接口。
 */
@RestController
@RequestMapping("/api/knowledge-bases/{kbCode}/agent")
public class AgentRunEventController {
    private final AgentRunSseService sseService;

    /** 构造 AgentRunEventController。 */
    public AgentRunEventController(AgentRunSseService sseService) {
        this.sseService = sseService;
    }

    /** 订阅一个 Agent run 的历史和实时事件。 */
    @GetMapping(value = "/runs/{runCode}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable String kbCode,
                                   @PathVariable String runCode,
                                   @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return sseService.subscribe(kbCode, runCode, lastEventId);
    }
}

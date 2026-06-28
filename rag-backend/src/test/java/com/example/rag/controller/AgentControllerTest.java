package com.example.rag.controller;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.config.RequestIdFilter;
import com.example.rag.model.enums.AgentActionRiskLevel;
import com.example.rag.model.enums.AgentActionStatus;
import com.example.rag.model.enums.AgentRunMode;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.enums.AgentStepStatus;
import com.example.rag.model.enums.AgentStepType;
import com.example.rag.model.response.AgentActionResponse;
import com.example.rag.model.response.AgentRunResponse;
import com.example.rag.model.response.AgentStepResponse;
import com.example.rag.service.AgentRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerTest {
    private MockMvc mockMvc;
    private AgentRunService agentRunService;
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @BeforeEach
    void setUp() {
        agentRunService = mock(AgentRunService.class);
        snowflakeIdGenerator = mock(SnowflakeIdGenerator.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(agentRunService))
                .addFilters(new RequestIdFilter(snowflakeIdGenerator))
                .build();
    }

    @Test
    void createRunShouldReturnAcceptedRunResponse() throws Exception {
        when(agentRunService.createRun(eq("day20-cn-kb"), any())).thenReturn(runningRunResponse());
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(post("/api/knowledge-bases/day20-cn-kb/agent/runs")
                        .contentType("application/json")
                        .content("""
                                {
                                  "goal": "诊断这个知识库为什么不能问答",
                                  "question": "第二百三十八条是什么",
                                  "runMode": "DIAGNOSE_AND_RECOMMEND",
                                  "createdBy": "tester"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Request-Id", "REQ-test"))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.runCode").value("AR-100"))
                .andExpect(jsonPath("$.data.knowledgeBaseCode").value("day20-cn-kb"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.steps").isArray())
                .andExpect(jsonPath("$.data.steps").isEmpty())
                .andExpect(jsonPath("$.data.actions").isArray())
                .andExpect(jsonPath("$.data.actions").isEmpty());
    }

    @Test
    void getRunShouldReturnRunResponse() throws Exception {
        when(agentRunService.getRun("day20-cn-kb", "AR-100")).thenReturn(runResponse());
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(get("/api/knowledge-bases/day20-cn-kb/agent/runs/AR-100"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "REQ-test"))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.runCode").value("AR-100"))
                .andExpect(jsonPath("$.data.runMode").value("DIAGNOSE_AND_RECOMMEND"));
    }

    @Test
    void confirmActionShouldReturnUpdatedRunResponse() throws Exception {
        when(agentRunService.confirmAction(eq("day20-cn-kb"), eq("AR-100"), eq("ACT-100"), any()))
                .thenReturn(runResponse(AgentRunStatus.SUCCEEDED, AgentActionStatus.SUCCEEDED));
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(post("/api/knowledge-bases/day20-cn-kb/agent/runs/AR-100/actions/ACT-100/confirm")
                        .contentType("application/json")
                        .content("""
                                {
                                  "operator": "tester"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "REQ-test"))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.runCode").value("AR-100"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.actions[0].status").value("SUCCEEDED"));
    }

    @Test
    void rejectActionShouldReturnUpdatedRunResponse() throws Exception {
        when(agentRunService.rejectAction(eq("day20-cn-kb"), eq("AR-100"), eq("ACT-100"), any()))
                .thenReturn(runResponse(AgentRunStatus.SUCCEEDED, AgentActionStatus.REJECTED));
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(post("/api/knowledge-bases/day20-cn-kb/agent/runs/AR-100/actions/ACT-100/reject")
                        .contentType("application/json")
                        .content("""
                                {
                                  "operator": "tester",
                                  "reason": "暂不重试"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "REQ-test"))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.runCode").value("AR-100"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.actions[0].status").value("REJECTED"));
    }

    private AgentRunResponse runResponse() {
        return runResponse(AgentRunStatus.WAITING_CONFIRMATION, AgentActionStatus.PENDING_CONFIRMATION);
    }

    private AgentRunResponse runningRunResponse() {
        return new AgentRunResponse(
                "AR-100",
                "day20-cn-kb",
                "诊断这个知识库为什么不能问答",
                "第二百三十八条是什么",
                AgentRunMode.DIAGNOSE_AND_RECOMMEND,
                AgentRunStatus.RUNNING,
                null,
                null,
                List.of(),
                List.of(),
                "tester",
                OffsetDateTime.parse("2026-06-16T10:00:00Z"),
                OffsetDateTime.parse("2026-06-16T10:00:00Z"),
                null
        );
    }

    private AgentRunResponse runResponse(AgentRunStatus runStatus, AgentActionStatus actionStatus) {
        return new AgentRunResponse(
                "AR-100",
                "day20-cn-kb",
                "诊断这个知识库为什么不能问答",
                "第二百三十八条是什么",
                AgentRunMode.DIAGNOSE_AND_RECOMMEND,
                runStatus,
                "知识库当前不可问答，主要原因是 embedding 配置变化后尚未完成重嵌入。",
                null,
                List.of(new AgentStepResponse(
                        "AST-100",
                        "kb_readiness_check",
                        "kb.readiness.check",
                        AgentStepType.TOOL_CALL,
                        AgentStepStatus.SUCCEEDED,
                        null,
                        "{\"reembedRequired\":true}",
                        12L,
                        null,
                        OffsetDateTime.parse("2026-06-16T10:00:00Z"),
                        OffsetDateTime.parse("2026-06-16T10:00:00Z"),
                        OffsetDateTime.parse("2026-06-16T10:00:00Z"),
                        OffsetDateTime.parse("2026-06-16T10:00:00Z")
                )),
                List.of(new AgentActionResponse(
                        "ACT-100",
                        "embedding.rebuild.submit",
                        "提交知识库重嵌入任务",
                        "readiness 显示需要重嵌入",
                        AgentActionRiskLevel.MEDIUM,
                        true,
                        actionStatus,
                        "{\"kbCode\":\"day20-cn-kb\"}",
                        actionStatus == AgentActionStatus.PENDING_CONFIRMATION ? null : "tester",
                        actionStatus == AgentActionStatus.PENDING_CONFIRMATION ? null : OffsetDateTime.parse("2026-06-16T10:01:00Z"),
                        actionStatus == AgentActionStatus.SUCCEEDED ? OffsetDateTime.parse("2026-06-16T10:02:00Z") : null,
                        actionStatus == AgentActionStatus.SUCCEEDED ? "{\"status\":\"QUEUED\"}" : null,
                        null,
                        OffsetDateTime.parse("2026-06-16T10:00:00Z"),
                        OffsetDateTime.parse("2026-06-16T10:00:00Z")
                )),
                "tester",
                OffsetDateTime.parse("2026-06-16T10:00:00Z"),
                OffsetDateTime.parse("2026-06-16T10:00:00Z"),
                runStatus == AgentRunStatus.WAITING_CONFIRMATION ? null : OffsetDateTime.parse("2026-06-16T10:02:00Z")
        );
    }
}

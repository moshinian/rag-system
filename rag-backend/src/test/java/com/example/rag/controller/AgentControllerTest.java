package com.example.rag.controller;

import com.example.rag.common.id.SnowflakeIdGenerator;
import com.example.rag.config.RequestIdFilter;
import com.example.rag.model.enums.AgentRunMode;
import com.example.rag.model.enums.AgentRunStatus;
import com.example.rag.model.response.AgentRunResponse;
import com.example.rag.service.AgentRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
        when(agentRunService.createRun(eq("day20-cn-kb"), any())).thenReturn(runResponse());
        when(snowflakeIdGenerator.nextId("REQ-")).thenReturn("REQ-test");

        mockMvc.perform(post("/api/knowledge-bases/day20-cn-kb/agent/runs")
                        .contentType(MediaType.APPLICATION_JSON)
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
                .andExpect(jsonPath("$.data.actions").isArray());
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

    private AgentRunResponse runResponse() {
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
}

package com.example.rag.persistence;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 保护 Agent SSE 迁移脚本里的关键索引约束。 */
class AgentRunEventMigrationScriptTest {

    @Test
    void migrationShouldUsePartialUniqueIndexForNodeInvocationIdAndDatabaseIdOrdering() throws Exception {
        String migration;
        try (var input = getClass().getResourceAsStream("/db/migration/V19__create_agent_run_event_table.sql")) {
            assertThat(input).isNotNull();
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(migration)
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_step")
                .contains("WHERE node_invocation_id IS NOT NULL")
                .contains("CREATE INDEX IF NOT EXISTS idx_agent_run_event_run_id")
                .containsIgnoringWhitespaces("ON agent_step(run_code, node_invocation_id)")
                .containsIgnoringWhitespaces("ON agent_run_event(run_code, id)");
    }

    @Test
    void runtimeHeartbeatMigrationShouldBackfillOnlyRunningRuns() throws Exception {
        String migration;
        try (var input = getClass().getResourceAsStream("/db/migration/V20__add_agent_run_runtime_heartbeat.sql")) {
            assertThat(input).isNotNull();
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(migration)
                .contains("runtime_heartbeat_at TIMESTAMPTZ")
                .contains("SET runtime_heartbeat_at = now()")
                .contains("WHERE status = 'RUNNING'")
                .contains("AND runtime_heartbeat_at IS NULL");
    }
}

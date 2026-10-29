package com.kunirank.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("railway-readonly")
@TestPropertySource(properties = {
    "PGHOST=unused", "PGDATABASE=unused", "PGUSER=unused", "PGPASSWORD=unused"
})
@Testcontainers
class ReadOnlyDeploymentIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("production-schema-fixture.sql");

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void validatesExistingSchemaAndEnforcesReadOnlyConnections() throws Exception {
        assertEquals("on", jdbc.queryForObject("SHOW default_transaction_read_only", String.class));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("DELETE FROM universities WHERE id = -1"));
        mvc.perform(get("/api/v1/universities")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void blocksVotesSessionCreationAndMemberOperationsBeforeControllers() throws Exception {
        mvc.perform(get("/api/v1/boards/overall/next")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/boards/overall/next/")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/v1/boards/overall/vote")).andExpect(status().isServiceUnavailable());
        mvc.perform(put("/api/v1/members/favorites/universities/1"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/member/me")).andExpect(status().isServiceUnavailable());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM vote_sessions", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM comparison_votes", Long.class));
    }
}

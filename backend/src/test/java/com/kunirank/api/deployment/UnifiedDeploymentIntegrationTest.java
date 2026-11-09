package com.kunirank.api.deployment;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "MEMBER_FRONTEND_TOKEN=isolated-member-frontend-token-32-characters",
        "MEMBER_AUTH_SECRET=isolated-member-signing-secret-32-characters",
        "MEMBER_ALLOWED_ORIGINS=https://frontend.test",
        "VS_FRONTEND_TOKEN=isolated-voting-frontend-token-32-characters",
        "VS_ALLOWED_ORIGIN=https://frontend.test"
})
@AutoConfigureMockMvc
@ActiveProfiles("railway-unified")
@Testcontainers
class UnifiedDeploymentIntegrationTest {
    private static final String TEST_PASSWORD = "isolated-role-password";
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("production-schema-fixture.sql");

    @Autowired UnifiedDeploymentFilter filter;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired CacheManager caches;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        postgres.start();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); var sql = connection.createStatement()) {
            sql.execute("CREATE ROLE member_unified_test LOGIN PASSWORD 'isolated-role-password'");
            sql.execute("CREATE ROLE voting_unified_test LOGIN PASSWORD 'isolated-role-password'");
            sql.execute("""
                    CREATE TABLE kunirank_member_limits (
                        bucket_key char(64) PRIMARY KEY, attempts integer NOT NULL, expires_at timestamptz NOT NULL
                    )
                    """);
            sql.execute("GRANT SELECT, INSERT, UPDATE ON kunirank_member_limits TO member_unified_test");
            sql.execute("""
                    INSERT INTO universities(university_id, name, is_active, created_at, updated_at)
                    VALUES (100, 'Cache test university', true, now(), now())
                    """);
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("PGUSER", postgres::getUsername);
        registry.add("PGPASSWORD", postgres::getPassword);
        registry.add("MEMBER_DB_USER", () -> "member_unified_test");
        registry.add("MEMBER_DB_PASSWORD", () -> TEST_PASSWORD);
        registry.add("VS_DB_USER", () -> "voting_unified_test");
        registry.add("VS_DB_PASSWORD", () -> TEST_PASSWORD);
    }

    @Test
    void selectsTheRoleBeforeAuthenticationDatabaseWorkAndClearsItOnFailure() throws Exception {
        var login = new MockHttpServletRequest("POST", "/api/v1/member/login");
        login.setContentType("application/json");
        login.addHeader("Origin", "https://frontend.test");
        login.addHeader("X-Member-Frontend-Token", "isolated-member-frontend-token-32-characters");
        login.addHeader("X-Member-Network-Key", "a".repeat(64));
        filter.doFilter(login, new MockHttpServletResponse(), (request, response) -> {
            assertEquals("member_unified_test", jdbc.queryForObject("SELECT current_user", String.class));
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM kunirank_member_limits", Long.class));
        });

        var vote = new MockHttpServletRequest("GET", "/api/v1/boards/overall/next");
        vote.addHeader("X-VS-Frontend-Token", "isolated-voting-frontend-token-32-characters");
        vote.addHeader("X-VS-Client-Key", "b".repeat(64));
        assertThrows(jakarta.servlet.ServletException.class, () ->
                filter.doFilter(vote, new MockHttpServletResponse(), (request, response) -> {
                    assertEquals("voting_unified_test", jdbc.queryForObject("SELECT current_user", String.class));
                    throw new jakarta.servlet.ServletException("test error");
                }));

        assertEquals(postgres.getUsername(), jdbc.queryForObject("SELECT current_user", String.class));
        assertEquals("on", jdbc.queryForObject("SHOW default_transaction_read_only", String.class));
    }

    @Test
    void cachesOnlySuccessfulPublicUniversitySummaries() throws Exception {
        for (String endpoint : new String[] {"overview", "admission-reference"}) {
            var cache = caches.getCache(endpoint.equals("overview") ? "university-overview" : endpoint);
            assertNotNull(cache);
            var first = mvc.perform(get("/api/v1/universities/100/" + endpoint))
                    .andExpect(status().isOk()).andReturn();
            var entry = cache.get(100L);
            assertNotNull(entry);
            var second = mvc.perform(get("/api/v1/universities/100/" + endpoint))
                    .andExpect(status().isOk()).andReturn();
            assertEquals(first.getResponse().getContentAsString(), second.getResponse().getContentAsString());
            assertSame(entry.get(), cache.get(100L).get());
            mvc.perform(get("/api/v1/universities/999999/" + endpoint)).andExpect(status().isNotFound());
            assertNull(cache.get(999999L));
        }
    }

    @Test
    void exposesOnlyExistingApprovedRoutesAndChecksAllPools() throws Exception {
        mvc.perform(get("/api/v1/universities")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/member/me")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/member/me")
                .header("X-Member-Frontend-Token", "isolated-member-frontend-token-32-characters"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/boards/overall/next")
                .header("X-Member-Frontend-Token", "isolated-member-frontend-token-32-characters"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/universities/1")).andExpect(status().isServiceUnavailable());
    }
}

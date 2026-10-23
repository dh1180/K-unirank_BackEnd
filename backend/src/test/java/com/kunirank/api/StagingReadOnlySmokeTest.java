package com.kunirank.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Opt-in READ-ONLY smoke test for a restored staging DB.
 * No mutation routes or personal data are read into application logs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.hikari.read-only=true",
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.connection-init-sql=SET default_transaction_read_only = on",
    "MEMBER_AUTH_ENABLED=false",
    "AI_GATEWAY_ENABLED=false"
})
@EnabledIfEnvironmentVariable(named = "KUNIRANK_STAGING_SMOKE", matches = "YES")
class StagingReadOnlySmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeAll
    static void guardLocalTunnel() {
        String url = System.getenv("DB_JDBC_URL");
        if (url == null || !url.matches("^jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{2,5}/[a-zA-Z0-9_-]+$")) {
            throw new IllegalStateException("Use a localhost-only staging tunnel, never a remote DB host");
        }
        if (System.getenv("DB_PASSWORD") == null || System.getenv("DB_PASSWORD").isBlank()) {
            throw new IllegalStateException("Missing staging DB password");
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Test
    void stagedSnapshotHasVerifiedRowCountsAndServerReadOnly() {
        assertEquals(54591L, count("admission_results"));
        assertEquals(149332L, count("admission_metrics"));
        assertEquals(4502L, count("admission_aggregates"));
        assertEquals(484L, count("universities"));
        assertEquals("on", jdbc.queryForObject("SHOW default_transaction_read_only", String.class));
        System.out.println("STAGING_OK: snapshot row counts and server read-only mode");
    }

    @Test
    void springCanReadActualAdmissionsAndComparisonEndpoints() throws Exception {
        mvc.perform(get("/api/v1/universities").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount", greaterThan(0)))
            .andExpect(jsonPath("$.items.length()").value(1));

        Integer year = jdbc.queryForObject("SELECT max(admission_year) FROM admission_results", Integer.class);
        assertNotNull(year);
        mvc.perform(get("/api/v1/admissions/results")
                    .param("year", year.toString())
                    .param("metrics", "all")
                    .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount", greaterThan(0)))
            .andExpect(jsonPath("$.items.length()").value(1));

        long cutCount = jdbc.queryForObject("""
            SELECT count(*) FROM admission_metrics
            WHERE metric_code IN ('STUDENT_GRADE_50_CUT', 'STUDENT_GRADE_70_CUT')
              AND value BETWEEN 1 AND 9
            """, Long.class);
        assertTrue(cutCount > 0);
        mvc.perform(get("/api/v1/admissions/compare")
                    .param("grade", "3.0")
                    .param("track", "student")
                    .param("scope", "all")
                    .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.selectedYear").isNumber())
            .andExpect(jsonPath("$.totalCount", greaterThan(0)))
            .andExpect(jsonPath("$.items.length()").value(1));
        System.out.println("STAGING_OK: university, admission results, grade comparison GET API");
    }

    @Test
    void historicalVotesArePresentWithoutWritingAny() {
        assertTrue(count("comparison_votes") > 0);
        assertTrue(count("university_ratings") > 0);
        System.out.println("STAGING_OK: historical VS data present");
    }
}

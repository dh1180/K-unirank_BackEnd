package com.kunirank.api.ranking;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import java.sql.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @ActiveProfiles("railway-voting")
@TestPropertySource(properties={"VS_SIGNING_SECRET=isolated-test-signing-key-at-least-32-characters","VS_FRONTEND_TOKEN=isolated-test-frontend-token-at-least-32-characters","VS_ALLOWED_ORIGIN=https://frontend.test","VS_COOKIE_SECURE=false"})
class VotingProfileIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine").withInitScript("production-schema-fixture.sql");
    static final String TOKEN="isolated-test-frontend-token-at-least-32-characters";
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        postgres.start();
        try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());var s=c.createStatement()) {
            s.execute("CREATE ROLE voting_test LOGIN PASSWORD 'isolated-role-password'");
            s.execute("GRANT USAGE ON SCHEMA public TO voting_test");
            s.execute("GRANT SELECT ON universities,ranking_boards,university_ratings,vote_sessions,comparison_votes TO voting_test");
            s.execute("GRANT INSERT ON university_ratings,vote_sessions,comparison_votes TO voting_test");
            s.execute("GRANT UPDATE(rating,rating_deviation,volatility,match_count,win_count,loss_count,updated_at) ON university_ratings TO voting_test");
            s.execute("GRANT UPDATE(vote_count,last_seen_at) ON vote_sessions TO voting_test");
            s.execute("GRANT UPDATE(updated_at) ON ranking_boards TO voting_test");
            s.execute("GRANT USAGE ON SEQUENCE university_ratings_rating_id_seq,comparison_votes_vote_id_seq TO voting_test");
            s.execute("INSERT INTO universities(name,is_active,created_at,updated_at) VALUES ('가대학',true,now(),now()),('나대학',true,now(),now())");
            s.execute("INSERT INTO ranking_boards(slug,name,description,is_active,display_order,created_at,updated_at) VALUES ('overall','종합','',true,0,now(),now())");
        }
        registry.add("spring.datasource.url",postgres::getJdbcUrl);
        registry.add("spring.datasource.username",()->"voting_test");
        registry.add("spring.datasource.password",()->"isolated-role-password");
    }
    @Test void dedicatedRolePersistsVotesButCannotModifyUniversityOrDeleteHistory() throws Exception {
        String client="a".repeat(64);
        var next=mvc.perform(get("/api/v1/boards/overall/next").header("X-VS-Frontend-Token",TOKEN).header("X-VS-Client-Key",client))
                .andExpect(status().isOk()).andReturn().getResponse();
        var n=mapper.readTree(next.getContentAsByteArray());
        String cookie=next.getHeader("Set-Cookie").split(";")[0].split("=",2)[1];
        String body=mapper.writeValueAsString(Map.of("university_a",n.get("pair").get(0).get("id").asLong(),"university_b",n.get("pair").get(1).get("id").asLong(),"selected_university",n.get("pair").get(0).get("id").asLong(),"skipped",false,"challenge",n.get("challenge").asText()));
        mvc.perform(post("/api/v1/boards/overall/vote").header("X-VS-Frontend-Token",TOKEN).header("X-VS-Client-Key",client).header("Origin","https://frontend.test").cookie(new Cookie("kunirank_vs",cookie)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.vote_count").value(1));
        try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),"voting_test","isolated-role-password");var s=c.createStatement()) {
            try(var rows=s.executeQuery("SELECT sum(match_count) FROM university_ratings")) {rows.next();assertEquals(2,rows.getLong(1));}
            assertEquals("42501",assertThrows(SQLException.class,()->s.executeUpdate("UPDATE universities SET name='changed'")).getSQLState());
            assertEquals("42501",assertThrows(SQLException.class,()->s.executeUpdate("DELETE FROM comparison_votes")).getSQLState());
        }
    }
    @Test void votingServiceRejectsMissingTrustInvalidOriginAndNonVotingEndpoints() throws Exception {
        mvc.perform(get("/api/v1/boards/overall/next")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/universities")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/v1/boards/overall/vote").header("X-VS-Frontend-Token",TOKEN).header("X-VS-Client-Key","b".repeat(64)).header("Origin","https://other.test").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}

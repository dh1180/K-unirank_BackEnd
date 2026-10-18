package com.kunirank.api.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunirank.api.member.repository.FavoriteUniversityRepository;
import com.kunirank.api.admission.entity.RecruitmentUnit;
import com.kunirank.api.admission.repository.RecruitmentUnitRepository;
import com.kunirank.api.member.security.MemberSessionService;
import com.kunirank.api.university.entity.University;
import com.kunirank.api.university.repository.UniversityRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "MEMBER_AUTH_ENABLED=true",
        "MEMBER_AUTH_SECRET=a-long-development-only-hmac-secret-that-is-actually-more-than-32",
        "MEMBER_COOKIE_SECURE=false"
})
class MemberApiIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String DJANGO_HASH =
            "pbkdf2_sha256$120000$DjangoSaltSampleX9$Si6mgnD8qGfnr783SJ0iG5wEEv2k8jDRydqqkDJKmRM=";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UniversityRepository universities;
    @Autowired RecruitmentUnitRepository units;
    private String username;

    @BeforeEach
    void setup() {
        username = "test-" + java.util.UUID.randomUUID().toString();
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS auth_user (
              id BIGSERIAL PRIMARY KEY,
              username VARCHAR(150) NOT NULL UNIQUE,
              password VARCHAR(128) NOT NULL,
              is_active BOOLEAN NOT NULL DEFAULT TRUE
            )
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS favorite_universities (
              favorite_university_id BIGSERIAL PRIMARY KEY,
              user_id BIGINT NOT NULL,
              university_id BIGINT NOT NULL,
              created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
              UNIQUE (user_id, university_id)
            )
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS favorite_recruitment_units (
              favorite_recruitment_unit_id BIGSERIAL PRIMARY KEY,
              user_id BIGINT NOT NULL,
              recruitment_unit_id BIGINT NOT NULL,
              created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
              UNIQUE(user_id, recruitment_unit_id)
            )
            """);
        jdbc.update("DELETE FROM favorite_recruitment_units");
        jdbc.update("DELETE FROM favorite_universities");
        jdbc.update("DELETE FROM auth_user");
        units.deleteAll();
        universities.deleteAll();
        jdbc.update("INSERT INTO auth_user (username,password,is_active) VALUES (?,?,?)",
                username, DJANGO_HASH, true);
    }

    private Cookie login() throws Exception {
        var login = mvc.perform(post("/api/v1/member/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "username", username,
                        "password", "테스트Password!2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn().getResponse();
        String header = login.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Strict"));
        assertFalse(header.contains("password"));
        return new Cookie("kunirank_member", header.split(";")[0].split("=", 2)[1]);
    }

    @Test
    void existingDjangoUserCanLoginAndReadItsProfile() throws Exception {
        Cookie cookie = login();
        mvc.perform(get("/api/v1/member/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
        mvc.perform(get("/api/v1/member/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongPasswordAndInactiveAccount() throws Exception {
        mvc.perform(post("/api/v1/member/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "username", username, "password", "wrong"))))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE auth_user SET is_active=false WHERE username=?", username);
        mvc.perform(post("/api/v1/member/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "username", username, "password", "테스트Password!2026"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeImmediatelyInvalidatesExistingSignedCookie() throws Exception {
        Cookie cookie = login();
        jdbc.update("UPDATE auth_user SET password=? WHERE username=?", DJANGO_HASH + "new", username);
        mvc.perform(get("/api/v1/member/me").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void favoritesAreScopedByMemberAndAddingTwiceIsIdempotent() throws Exception {
        Cookie cookie = login();
        University u = universities.save(new University("예시대학교", "서울", true));
        mvc.perform(put("/api/v1/member/favorites/universities/{id}", u.getId())
                .cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/member/favorites/universities/{id}", u.getId())
                .cookie(cookie)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/member/favorites/universities").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("예시대학교"));
        mvc.perform(put("/api/v1/member/favorites/universities/99999").cookie(cookie))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/member/favorites/universities/{id}", u.getId()))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/v1/member/favorites/universities/{id}", u.getId())
                .cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/member/favorites/universities").cookie(cookie))
                .andExpect(jsonPath("$.length()").value(0));
    }


    @Test
    void existingFavoriteRecruitmentUnitsCanBeAddedListedAndRemoved() throws Exception {
        Cookie cookie = login();
        University u = universities.save(new University("예시대학교", "서울", true));
        RecruitmentUnit unit = units.save(new RecruitmentUnit(u, "컴퓨터공학과", "공과대학", true));
        RecruitmentUnit disabled = units.save(new RecruitmentUnit(u, "폐지학과", "공과대학", false));
        String path = "/api/v1/member/favorites/units/{id}";

        mvc.perform(put(path, unit.getId()).cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(put(path, unit.getId()).cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/member/favorites/units").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("컴퓨터공학과"))
                .andExpect(jsonPath("$[0].universityName").value("예시대학교"));
        mvc.perform(put(path, disabled.getId()).cookie(cookie)).andExpect(status().isNotFound());
        mvc.perform(put(path, unit.getId())).andExpect(status().isUnauthorized());
        mvc.perform(delete(path, unit.getId()).cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/member/favorites/units").cookie(cookie))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void logoutClearsBrowserCookie() throws Exception {
        Cookie cookie = login();
        var response = mvc.perform(post("/api/v1/member/logout").cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertNotNull(response.getHeader("Set-Cookie"));
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
    }
}

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
              email VARCHAR(254) NOT NULL DEFAULT '', first_name VARCHAR(150) NOT NULL DEFAULT '',last_name VARCHAR(150) NOT NULL DEFAULT '',date_joined TIMESTAMPTZ NOT NULL DEFAULT now(),
              is_active BOOLEAN NOT NULL DEFAULT TRUE,
              is_staff BOOLEAN NOT NULL DEFAULT FALSE,
              is_superuser BOOLEAN NOT NULL DEFAULT FALSE
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
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/member-security.sql"))
                .execute(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/ai-usage.sql"))
                .execute(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        var registrationSql = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/registration.sql"));
        registrationSql.setSeparator(";;");
        registrationSql.execute(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        jdbc.update("DELETE FROM kunirank_member_sessions");
        jdbc.update("DELETE FROM kunirank_member_limits");
        jdbc.update("DELETE FROM favorite_recruitment_units");
        jdbc.update("DELETE FROM favorite_universities");
        jdbc.update("DELETE FROM auth_user");
        jdbc.execute("ALTER TABLE vote_sessions ADD COLUMN IF NOT EXISTS user_id BIGINT");
        jdbc.update("DELETE FROM comparison_votes");
        jdbc.update("DELETE FROM university_ratings");
        jdbc.update("DELETE FROM vote_sessions");
        jdbc.update("DELETE FROM ranking_boards");
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
    void registrationFunctionCannotSetAdminFlagsOrModifyExistingUsers() {
        jdbc.execute("DO $$ BEGIN CREATE ROLE native_registration_test; EXCEPTION WHEN duplicate_object THEN NULL; END $$");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO native_registration_test");
        jdbc.execute("GRANT EXECUTE ON FUNCTION kunirank_register_member(text,text,text) TO native_registration_test");
        String hash=new com.kunirank.api.member.security.DjangoPasswordVerifier().encode("SafeUniqueTest!234".toCharArray());
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try(var statement=connection.createStatement()) {
                statement.execute("SET ROLE native_registration_test");
                try(var call=connection.prepareStatement("SELECT kunirank_register_member(?,?,?)")) {
                    call.setString(1,"restricted-signup");call.setString(2,hash);call.setString(3,"");call.execute();
                }
                assertThrows(java.sql.SQLException.class,()->statement.execute("UPDATE auth_user SET is_superuser=true WHERE username='restricted-signup'"));
            } finally {try(var reset=connection.createStatement()){reset.execute("RESET ROLE");}}
            return null;
        });
        assertFalse(jdbc.queryForObject("SELECT is_superuser OR is_staff FROM auth_user WHERE username='restricted-signup'",Boolean.class));
    }

    @Test
    void onlyReadsOwnHistoryAndCannotTakeAnotherMembersVoteSession() throws Exception {
        Cookie cookie=login();
        Long user=jdbc.queryForObject("SELECT id FROM auth_user WHERE username=?",Long.class,username);
        java.util.UUID owned=java.util.UUID.randomUUID(),other=java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO vote_sessions(session_id,session_key,ip_hash,vote_count,created_at,last_seen_at,user_id) VALUES(?,?,'',0,now(),now(),NULL)",owned,"owned-session-key-123456789012345");
        jdbc.update("INSERT INTO vote_sessions(session_id,session_key,ip_hash,vote_count,created_at,last_seen_at,user_id) VALUES(?,?,'',0,now(),now(),?)",other,"other-session-key-123456789012345",user+1000);
        mvc.perform(post("/api/v1/member/link-vote-session").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"sessionKey\":\"owned-session-key-123456789012345\"}"))
            .andExpect(status().isOk());
        assertEquals(user,jdbc.queryForObject("SELECT user_id FROM vote_sessions WHERE session_id=?",Long.class,owned));
        mvc.perform(post("/api/v1/member/link-vote-session").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"sessionKey\":\"other-session-key-123456789012345\"}"))
            .andExpect(status().isOk());
        assertEquals(user+1000,jdbc.queryForObject("SELECT user_id FROM vote_sessions WHERE session_id=?",Long.class,other));
        mvc.perform(get("/api/v1/member/history").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/member/history")).andExpect(status().isUnauthorized());
    }

    @Test
    void registersNonPrivilegedCompatibleAccountAndRejectsDuplicateOrMismatch() throws Exception {
        var body=Map.of("username","new-student", "email","student@example.org", "password","LongUnique!Member2026", "passwordConfirm","LongUnique!Member2026");
        var result=mvc.perform(post("/api/v1/member/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("new-student")).andExpect(jsonPath("$.admin").value(false)).andReturn().getResponse();
        assertNotNull(result.getHeader("Set-Cookie"));
        assertFalse(jdbc.queryForObject("SELECT is_staff OR is_superuser FROM auth_user WHERE username='new-student'",Boolean.class));
        mvc.perform(post("/api/v1/member/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username","new-student","password","LongUnique!Member2026")))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/member/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/member/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username","new-other","password","LongUnique!Member2026","passwordConfirm","different")))).andExpect(status().isUnprocessableEntity());
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
        mvc.perform(get("/api/v1/member/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminChecksCurrentRoleAndNeverTrustsAnExistingCookieForPermissions() throws Exception {
        mvc.perform(get("/api/v1/admin/overview")).andExpect(status().isUnauthorized());
        Cookie cookie=login();
        mvc.perform(get("/api/v1/admin/overview").cookie(cookie)).andExpect(status().isForbidden());
        jdbc.update("UPDATE auth_user SET is_staff=true WHERE username=?",username);
        mvc.perform(get("/api/v1/admin/overview").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operator").value(username))
                .andExpect(jsonPath("$.stats[0].value").value(1));
        jdbc.update("UPDATE auth_user SET is_staff=false WHERE username=?",username);
        mvc.perform(get("/api/v1/admin/overview").cookie(cookie)).andExpect(status().isForbidden());
        jdbc.update("UPDATE auth_user SET is_superuser=true WHERE username=?",username);
        mvc.perform(get("/api/v1/admin/overview").cookie(cookie)).andExpect(status().isOk());
        jdbc.update("UPDATE auth_user SET is_active=false WHERE username=?",username);
        mvc.perform(get("/api/v1/admin/overview").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminSearchAndQualityAreRoleCheckedAndNeverExposeCredentialFields() throws Exception {
        mvc.perform(get("/api/v1/admin/members")).andExpect(status().isUnauthorized());
        Cookie cookie=login();
        mvc.perform(get("/api/v1/admin/members").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/quality").cookie(cookie)).andExpect(status().isForbidden());
        jdbc.update("UPDATE auth_user SET is_staff=true WHERE username=?",username);
        mvc.perform(get("/api/v1/admin/members").param("q",username).cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].username").value(username))
            .andExpect(jsonPath("$.items[0].password").doesNotExist()).andExpect(jsonPath("$.items[0].email").doesNotExist());
        mvc.perform(get("/api/v1/admin/members").param("q","%' OR 1=1 --").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/admin/members").param("state","invalid").cookie(cookie)).andExpect(status().isBadRequest());
        for(String kind:java.util.List.of("sentinel","shingyeongju","range"))
            mvc.perform(get("/api/v1/admin/quality").param("kind",kind).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/api/v1/admin/quality").param("kind","invalid").cookie(cookie)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/overview").cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.activity.length()").value(7)).andExpect(jsonPath("$.activeSessions").value(1));
    }

    @Test
    void aiUsageRequiresCurrentSessionAndIsIdempotentWithoutPromptStorage() throws Exception {
        var body=Map.of("requestId",java.util.UUID.randomUUID().toString(),"statusCode",200,"elapsedMs",1250);
        mvc.perform(post("/api/v1/member/ai/usage").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isUnauthorized());
        Cookie cookie=login();
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/member/ai/usage").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM kunirank_ai_requests",Integer.class));
        jdbc.update("UPDATE auth_user SET is_staff=true WHERE username=?",username);
        mvc.perform(get("/api/v1/admin/overview").cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.aiUsage.requests").value(1)).andExpect(jsonPath("$.aiUsage.successes").value(1))
            .andExpect(jsonPath("$.recentAiRequests[0].username").value(username));
        mvc.perform(post("/api/v1/member/logout").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/member/ai/usage").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedAndRevokedSessionsCannotReadFavorites() throws Exception {
        Cookie cookie=login();
        mvc.perform(get("/api/v1/member/me").cookie(new Cookie("kunirank_member",cookie.getValue()+"x")))
                .andExpect(status().isUnauthorized());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM kunirank_member_sessions",Integer.class));
        assertNotEquals(cookie.getValue(),jdbc.queryForObject("SELECT token_hash FROM kunirank_member_sessions",String.class));
        var registrationSql = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/registration.sql"));
        registrationSql.setSeparator(";;");
        registrationSql.execute(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        jdbc.update("DELETE FROM kunirank_member_sessions");
        mvc.perform(get("/api/v1/member/favorites/universities").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedPasswordAttemptsAreLimitedInTheSharedStore() throws Exception {
        for(int i=0;i<5;i++) mvc.perform(post("/api/v1/member/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username",username,"password","wrong"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/member/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username",username,"password","테스트Password!2026"))))
                .andExpect(status().isTooManyRequests());
    }
}

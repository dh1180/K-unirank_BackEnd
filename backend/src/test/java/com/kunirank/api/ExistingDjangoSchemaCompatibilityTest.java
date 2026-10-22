package com.kunirank.api;

import com.kunirank.api.admission.entity.*;
import com.kunirank.api.admission.repository.*;
import com.kunirank.api.ranking.entity.*;
import com.kunirank.api.ranking.repository.*;
import com.kunirank.api.university.entity.University;
import com.kunirank.api.university.repository.UniversityRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unlike create-drop tests, this runs Hibernate validate against an existing,
 * production-shaped schema. In particular, PostgreSQL's NOT NULL audit/source
 * columns must be populated by Java writes instead of hidden by generated DDL.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ExistingDjangoSchemaCompatibilityTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("production-schema-fixture.sql");

    @Autowired UniversityRepository universities;
    @Autowired RankingBoardRepository boards;
    @Autowired UniversityRatingRepository ratings;
    @Autowired VoteSessionRepository sessions;
    @Autowired ComparisonVoteRepository votes;
    @Autowired AdmissionSourceRepository sources;
    @Autowired RecruitmentUnitRepository units;
    @Autowired AdmissionResultRepository results;
    @Autowired AdmissionMetricRepository metrics;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void persistsRepresentativeEntitiesWithoutChangingDjangoSchema() {
        University university = universities.saveAndFlush(new University("스키마검증대학교", "서울", true));
        RankingBoard board = boards.saveAndFlush(new RankingBoard("overall", "종합", true));
        UniversityRating rating = ratings.saveAndFlush(new UniversityRating(board, university));
        assertNotNull(rating.getId());

        AdmissionSource source = sources.saveAndFlush(new AdmissionSource(
                university, 2026, "ADIGA", "https://example.edu/source"));
        RecruitmentUnit unit = units.saveAndFlush(new RecruitmentUnit(university, "컴퓨터학과", "", true));
        AdmissionResult result = results.saveAndFlush(new AdmissionResult(
                source, university, unit, 2026, "SUSI", "학생부교과", "일반전형"));
        metrics.saveAndFlush(new AdmissionMetric(result, "STUDENT_GRADE_70_CUT",
                new BigDecimal("2.51"), "등급"));

        VoteSession session = sessions.saveAndFlush(new VoteSession("production-fixture-test-session"));
        votes.saveAndFlush(new ComparisonVote(board, session, university, university, null, true));

        // These columns are NOT NULL in the real dump and absent from the old JPA model.
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM universities WHERE created_at IS NOT NULL AND updated_at IS NOT NULL",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM ranking_boards WHERE description = '' AND updated_at IS NOT NULL",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM university_ratings WHERE updated_at IS NOT NULL",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM admission_sources WHERE document_title = '' AND checksum = '' AND collected_at IS NOT NULL",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM admission_results WHERE recruitment_group = ''",
                Integer.class));

        rating.record(1502, 120, 0.06, true);
        ratings.flush();
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM university_ratings WHERE match_count=1 AND updated_at IS NOT NULL",
                Integer.class));
    }
}

package com.kunirank.api.admission;

import com.kunirank.api.admission.entity.*;
import com.kunirank.api.admission.repository.*;
import com.kunirank.api.university.entity.University;
import com.kunirank.api.university.repository.UniversityRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AdmissionApiIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired UniversityRepository schools;
    @Autowired RecruitmentUnitRepository units;
    @Autowired AdmissionSourceRepository sources;
    @Autowired AdmissionResultRepository results;
    @Autowired AdmissionMetricRepository metrics;

    private University university;
    private AdmissionSource source;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        results.deleteAll();
        sources.deleteAll();
        units.deleteAll();
        schools.deleteAll();
        university = schools.save(new University("새빛대학교", "서울", true));
        source = sources.save(new AdmissionSource(university, 2026, "ADIGA", "https://example.com/adiga"));
    }

    private AdmissionResult add(String name, String category, String selection,
                                String grade50, String grade70) {
        RecruitmentUnit unit = units.save(new RecruitmentUnit(university, name, "", true));
        AdmissionResult item = results.save(new AdmissionResult(
                source, university, unit, 2026, "SUSI", category, selection
        ));
        if (grade50 != null) {
            metrics.save(new AdmissionMetric(item, "STUDENT_GRADE_50_CUT", new BigDecimal(grade50), "등급"));
        }
        if (grade70 != null) {
            metrics.save(new AdmissionMetric(item, "STUDENT_GRADE_70_CUT", new BigDecimal(grade70), "등급"));
        }
        return item;
    }

    @Test
    void excludesOnlyExactDoubleOneCutForNonProfessionalMajors() throws Exception {
        add("정제대상학과", "학생부교과", "일반", "1.00", "1.0");
        add("정상학과", "학생부교과", "일반", "1.0", "1.25");
        add("의예과", "학생부교과", "일반", "1", "1.00");
        add("약학과", "학생부교과", "일반", "1.0", "1.0");
        add("치위생학과", "학생부교과", "일반", "1.0", "1.0");

        mvc.perform(get("/api/v1/admissions/results").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.items[0].universityName").value("새빛대학교"));
        // Source results are not deleted; only the public API excludes them.
        org.junit.jupiter.api.Assertions.assertEquals(5, results.count());
    }

    @Test
    void metricsAllRetainsUnpublishedButNeverRevealsInvalidDoubleOne() throws Exception {
        add("둘다1학과", "학생부교과", "일반", "1", "1");
        add("일부공개학과", "학생부교과", "일반", "1", null);
        AdmissionResult referenceOnly = add("참고값학과", "학생부교과", "일반", null, null);
        metrics.save(new AdmissionMetric(referenceOnly,
                "CSAT_PERCENTILE_REFERENCE_MEAN_70_CUT", new BigDecimal("85"), "백분위"));
        add("지표없음학과", "학생부교과", "일반", null, null);

        mvc.perform(get("/api/v1/admissions/results").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].metrics[0].code").value("STUDENT_GRADE_50_CUT"));

        mvc.perform(get("/api/v1/admissions/results").param("year", "2026")
                        .param("metrics", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.items[?(@.recruitmentUnitName=='참고값학과')].metrics").isArray());
    }

    @Test
    void supportsSourcePhaseTrackAndSearchAndNewestYear() throws Exception {
        add("컴퓨터공학과", "학생부교과", "일반", "2.1", "2.4");
        add("소프트웨어학과", "학생부종합", "종합", "2.5", "2.7");
        mvc.perform(get("/api/v1/admissions/results")
                        .param("kind", "four").param("track", "student").param("q", "컴퓨터"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedYear").value(2026))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].recruitmentUnitName").value("컴퓨터공학과"));

        mvc.perform(get("/api/v1/admissions/results").param("kind", "college"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void unitDetailUsesOriginalIdAndReturns404ForUnknownUnit() throws Exception {
        AdmissionResult item = add("산업공학과", "학생부교과", "일반", "2.2", "2.3");
        mvc.perform(get("/api/v1/admissions/units/" + item.getUnit().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(item.getId()));

        mvc.perform(get("/api/v1/admissions/units/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void universityAdmissionsOnlyReturnsSpecifiedActiveUniversityAndValidCuts() throws Exception {
        add("제외학과", "학생부교과", "일반", "1.0", "1.00");
        AdmissionResult kept = add("유지학과", "학생부교과", "일반", "1.0", "1.8");
        University other = schools.save(new University("다른대학교", "부산", true));
        AdmissionSource otherSource = sources.save(new AdmissionSource(
                other, 2026, "ADIGA", "https://example.com/other"));
        RecruitmentUnit otherUnit = units.save(new RecruitmentUnit(other, "간호학과", "", true));
        AdmissionResult otherResult = results.save(new AdmissionResult(
                otherSource, other, otherUnit, 2026, "SUSI", "학생부교과", "일반"));
        metrics.save(new AdmissionMetric(otherResult,
                "STUDENT_GRADE_70_CUT", new BigDecimal("2.3"), "등급"));

        mvc.perform(get("/api/v1/admissions/universities/{id}", university.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(kept.getId()));

        mvc.perform(get("/api/v1/admissions/universities/{id}", university.getId())
                        .param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));

        mvc.perform(get("/api/v1/admissions/universities/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void universitySearchCombinesFiltersBeforePaginationAndRetainsMetricMode() throws Exception {
        add("컴퓨터공학과", "학생부교과", "일반", "2.1", "2.4");
        add("컴퓨터공학과", "학생부종합", "종합", "2.5", "2.7");
        add("컴퓨터공학과", "학생부교과", "미공개", null, null);
        add("간호학과", "학생부교과", "일반", "2.2", "2.6");

        mvc.perform(get("/api/v1/admissions/universities/{id}", university.getId())
                        .param("q", "컴퓨터").param("phase", "SUSI")
                        .param("track", "student").param("year", "2026").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].recruitmentUnitName").value("컴퓨터공학과"));
        mvc.perform(get("/api/v1/admissions/universities/{id}", university.getId())
                        .param("q", "컴퓨터").param("track", "student")
                        .param("metrics", "all").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/v1/admissions/universities/{id}", university.getId())
                        .param("q", "컴퓨터").param("phase", "JEONGSI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
        mvc.perform(get("/api/v1/admissions/universities/{id}", university.getId())
                        .param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void rejectsInvalidPageInputs() throws Exception {
        mvc.perform(get("/api/v1/admissions/results").param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admissions/results").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inputWildcardsAreEscaped() throws Exception {
        add("데이터사이언스학과", "학생부교과", "일반", "2.1", "2.5");
        mvc.perform(get("/api/v1/admissions/results").param("year", "2026").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }
}

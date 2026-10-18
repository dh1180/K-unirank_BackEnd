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
class GradeComparisonApiIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired UniversityRepository schools;
    @Autowired RecruitmentUnitRepository units;
    @Autowired AdmissionSourceRepository sources;
    @Autowired AdmissionResultRepository results;
    @Autowired AdmissionMetricRepository metrics;

    private University school;
    private AdmissionSource adiga;
    private AdmissionSource universitySource;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        results.deleteAll();
        sources.deleteAll();
        units.deleteAll();
        schools.deleteAll();
        school = schools.save(new University("새빛대학교", "서울", true));
        adiga = sources.save(new AdmissionSource(school, 2026, "ADIGA", "https://example.org/adiga"));
        universitySource = sources.save(new AdmissionSource(
                school, 2026, "UNIVERSITY", "https://example.org/official"));
    }

    private RecruitmentUnit unit(String name) {
        return units.save(new RecruitmentUnit(school, name, "", true));
    }

    private AdmissionResult add(RecruitmentUnit unit, AdmissionSource source, String track,
                                String selection, String cut50, String cut70) {
        AdmissionResult result = results.save(new AdmissionResult(
                source, school, unit, 2026, "SUSI", track, selection
        ));
        if (cut50 != null)
            metrics.save(new AdmissionMetric(result, "STUDENT_GRADE_50_CUT", new BigDecimal(cut50), "등급"));
        if (cut70 != null)
            metrics.save(new AdmissionMetric(result, "STUDENT_GRADE_70_CUT", new BigDecimal(cut70), "등급"));
        return result;
    }

    @Test
    void prefers70CutAndCalculatesChartWithOnlyComparable70Samples() throws Exception {
        add(unit("컴퓨터공학과"), adiga, "학생부교과", "일반", "2.0", "2.6");
        add(unit("산업공학과"), adiga, "학생부교과", "일반", "2.3", "2.8");
        add(unit("화학공학과"), adiga, "학생부교과", "일반", "2.1", null);
        mvc.perform(get("/api/v1/admissions/compare")
                        .param("grade", "2.7").param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedYear").value(2026))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.chartMin").value(2))
                .andExpect(jsonPath("$.chartUniversityCount").value(1))
                .andExpect(jsonPath("$.charts[0].sampleCount").value(2))
                .andExpect(jsonPath("$.charts[0].averageCut").value(2.70))
                .andExpect(jsonPath("$.charts[0].referenceBasis").value("70% 컷"));
    }

    @Test
    void choosesUniversitySourceBeforeNearbyFiltering() throws Exception {
        RecruitmentUnit unit = unit("소프트웨어학부");
        add(unit, adiga, "학생부교과", "일반전형", "2.2", "2.7");
        add(unit, universitySource, "학생부교과", "일반전형", "4.0", "4.1");

        mvc.perform(get("/api/v1/admissions/compare")
                        .param("grade", "2.7").param("chart_min", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));

        mvc.perform(get("/api/v1/admissions/compare")
                        .param("grade", "2.7").param("scope", "all").param("chart_min", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].sourceType").value("UNIVERSITY"))
                .andExpect(jsonPath("$.items[0].referenceCut").value(4.1));
    }

    @Test
    void removesRestrictedAdmissionTracksUnlessSpecialAll() throws Exception {
        add(unit("경영학과"), adiga, "학생부교과", "일반", "3.0", "3.1");
        add(unit("통계학과"), adiga, "학생부교과", "지역인재", "3.0", "3.1");
        mvc.perform(get("/api/v1/admissions/compare")
                        .param("grade", "3.1").param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1));

        mvc.perform(get("/api/v1/admissions/compare")
                        .param("grade", "3.1").param("scope", "all").param("special", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    void excludesExactDoubleOneButProtectsMedicineAndOneSidedCut() throws Exception {
        add(unit("일반학과"), adiga, "학생부교과", "일반", "1", "1.00");
        add(unit("의예과"), adiga, "학생부교과", "일반", "1", "1");
        add(unit("보호학과"), adiga, "학생부교과", "일반", "1", "1.5");
        mvc.perform(get("/api/v1/admissions/compare")
                        .param("grade", "1.0").param("scope", "all").param("chart_min", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));
        org.junit.jupiter.api.Assertions.assertEquals(3, results.count());
    }

    @Test
    void supportsTrackAndRegionAndChartOnlySampleMinimum() throws Exception {
        add(unit("전자공학과"), adiga, "학생부종합", "일반", "3.4", "3.6");
        add(unit("전자소프트웨어학부"), adiga, "학생부종합", "일반", "3.2", null);
        add(unit("기계공학과"), adiga, "학생부교과", "일반", "3.0", "3.0");

        mvc.perform(get("/api/v1/admissions/compare")
                        .param("grade", "3.5").param("track", "holistic")
                        .param("region", "서울").param("q", "전자")
                        .param("scope", "all").param("chart_min", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.charts[0].sampleCount").value(1))
                .andExpect(jsonPath("$.charts[0].referenceBasis").value("70% 컷"));

        mvc.perform(get("/api/v1/admissions/compare")
                        .param("grade", "3.5").param("track", "holistic")
                        .param("scope", "all").param("chart_min", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.chartUniversityCount").value(0));
    }

    @Test
    void validatesGradeAndPagination() throws Exception {
        mvc.perform(get("/api/v1/admissions/compare").param("grade", "9.2"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admissions/compare").param("grade", "nan"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admissions/compare")
                        .param("grade", "3").param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}

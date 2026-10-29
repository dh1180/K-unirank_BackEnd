package com.kunirank.api.ranking;

import com.kunirank.api.university.entity.University;
import com.kunirank.api.university.repository.UniversityRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Transactional
@TestPropertySource(properties="spring.jpa.hibernate.ddl-auto=validate")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class RankingDiscoveryIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine").withInitScript("production-schema-fixture.sql");
    @Autowired JdbcTemplate jdbc;
    @Autowired UniversityRepository universities;
    @Autowired MockMvc mvc;
    long a,b;
    @BeforeEach void prepare() {
        a=universities.saveAndFlush(new University("가대학","서울특별시",true)).getId();
        b=universities.saveAndFlush(new University("나대학","경기도",true)).getId();
    }
    void indicator(long id,int year,String code,double value) {
        jdbc.update("INSERT INTO university_indicators(university_id,year,indicator_code,value,source_url) VALUES (?,?,?,?,?)",id,year,code,value,"https://www.academyinfo.go.kr/");
    }
    @Test void officialRankPreservesOrderYearAndRankAfterSearch() throws Exception {
        indicator(a,2026,"EMPLOYMENT_RATE",90); indicator(b,2026,"EMPLOYMENT_RATE",80);
        indicator(a,2025,"EMPLOYMENT_RATE",70); indicator(b,2025,"EMPLOYMENT_RATE",95);
        indicator(a,2026,"AVG_TUITION",5000000); indicator(b,2026,"AVG_TUITION",3000000);
        mvc.perform(get("/api/v1/rankings/indicators/employment-rate").param("q","나대학"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].rank").value(2)).andExpect(jsonPath("$.selectedYear").value(2026));
        mvc.perform(get("/api/v1/rankings/indicators/employment-rate").param("year","2025"))
            .andExpect(jsonPath("$.items[0].name").value("나대학"));
        mvc.perform(get("/api/v1/rankings/indicators/average-tuition").param("region","경기도"))
            .andExpect(jsonPath("$.items[0].rank").value(1)).andExpect(jsonPath("$.items[0].value").value(3000000));
        mvc.perform(get("/api/v1/rankings/indicators/unknown")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/rankings/indicators/employment-rate").param("q","' OR 1=1 --"))
            .andExpect(jsonPath("$.totalCount").value(0));
        mvc.perform(get("/api/v1/rankings/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7))
            .andExpect(jsonPath("$[0].universityCount").value(2));
    }
    @Test void admissionRankingKeepsGradeAndPercentileCohortsSeparate() throws Exception {
        // Year discovery uses real results; one minimal source/unit/result is sufficient.
        result(a,2,30,"등급","COLLEGE_STUDENT_AVERAGE");
        jdbc.update("INSERT INTO admission_aggregates(university_id,admission_year,admission_phase,selection_category,metric_code,value,sample_count) VALUES (?,2026,'SUSI','학생부교과','STUDENT_GRADE_70_CUT',2.1,5),(?,2026,'SUSI','학생부교과','STUDENT_GRADE_70_CUT',3.2,4),(?,2026,'JEONGSI','수능','CSAT_PERCENTILE_MEAN_70_CUT',96,6)",a,b,b);
        mvc.perform(get("/api/v1/rankings/admissions").param("q","나대학"))
            .andExpect(jsonPath("$.items[0].rank").value(2)).andExpect(jsonPath("$.items[0].value").value(3.2));
        mvc.perform(get("/api/v1/rankings/admissions").param("phase","JEONGSI"))
            .andExpect(jsonPath("$.items[0].value").value(96)).andExpect(jsonPath("$.metric.kind").value("백분위"));
    }
    void result(long university,double value,Integer weight,String unit,String metric) {
        long source=jdbc.queryForObject("INSERT INTO admission_sources(university_id,admission_year,source_type,source_url,document_title,collected_at,checksum) VALUES (?,2026,'PROCOLLEGE','','',now(),'') RETURNING source_id",Long.class,university);
        long major=jdbc.queryForObject("INSERT INTO recruitment_units(university_id,name,college_name,is_active) VALUES (?,'학과','',true) RETURNING recruitment_unit_id",Long.class,university);
        long result=jdbc.queryForObject("INSERT INTO admission_results(university_id,source_id,recruitment_unit_id,admission_year,admission_phase,selection_category,selection_name,recruitment_group,recruitment_count) VALUES (?,?,?,2026,'SUSI','학생부교과','일반전형','',?) RETURNING result_id",Long.class,university,source,major,weight);
        jdbc.update("INSERT INTO admission_metrics(result_id,metric_code,value,unit) VALUES (?,?,?,?)",result,metric,value,unit);
    }
    @Test void collegeRankingUsesRecruitmentWeightsAndDoesNotMixUnits() throws Exception {
        result(a,2,30,"등급","COLLEGE_STUDENT_AVERAGE");
        result(a,6,10,"등급","COLLEGE_STUDENT_AVERAGE");
        result(a,100,100,"점","COLLEGE_STUDENT_AVERAGE");
        result(b,4,null,"등급","COLLEGE_STUDENT_AVERAGE");
        mvc.perform(get("/api/v1/rankings/admissions").param("kind","college"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].value").value(3))
            .andExpect(jsonPath("$.items[0].sampleCount").value(2)).andExpect(jsonPath("$.items[1].value").value(4));
    }
    @Test void universityOverviewUsesOwnLatestDisclosuresAndSeparatesCutUnits() throws Exception {
        indicator(a,2025,"EMPLOYMENT_RATE",70); indicator(a,2026,"EMPLOYMENT_RATE",80);
        indicator(a,2026,"AVG_TUITION",0); indicator(b,2026,"AVG_TUITION",3000000);
        result(a,2,30,"등급","COLLEGE_STUDENT_AVERAGE");
        result(a,6,10,"등급","COLLEGE_STUDENT_AVERAGE");
        result(a,100,100,"점","COLLEGE_STUDENT_AVERAGE");
        result(b,4,null,"등급","COLLEGE_STUDENT_AVERAGE");
        jdbc.update("UPDATE admission_results SET admission_year=2027 WHERE university_id=?",b);
        mvc.perform(get("/api/v1/universities/{id}/overview",a))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indicators.length()").value(1))
            .andExpect(jsonPath("$.indicators[0].year").value(2026))
            .andExpect(jsonPath("$.indicators[0].value").value(80))
            .andExpect(jsonPath("$.admissions.year").value(2026))
            .andExpect(jsonPath("$.admissions.resultCount").value(3))
            .andExpect(jsonPath("$.admissions.ranges.length()").value(1))
            .andExpect(jsonPath("$.admissions.ranges[0].min").value(2))
            .andExpect(jsonPath("$.admissions.ranges[0].max").value(6))
            .andExpect(jsonPath("$.admissions.ranges[0].sampleCount").value(2));
    }
    @Test void universityOverviewHandlesMissingDataWithoutInventingZeroIndicators() throws Exception {
        mvc.perform(get("/api/v1/universities/{id}/overview",a))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indicators").isEmpty())
            .andExpect(jsonPath("$.admissions.year").isEmpty())
            .andExpect(jsonPath("$.admissions.ranges").isEmpty());
        mvc.perform(get("/api/v1/universities/999999/overview")).andExpect(status().isNotFound());
    }
    @Test void sitemapRetainsExistingPublicUrlsAndExcludesInactiveOrEmptyUnits() throws Exception {
        result(a,2,30,"등급","COLLEGE_STUDENT_AVERAGE");
        indicator(a,2026,"EMPLOYMENT_RATE",80);
        jdbc.update("UPDATE universities SET is_active=false WHERE university_id=?",b);
        long empty=jdbc.queryForObject("INSERT INTO recruitment_units(university_id,name,college_name,is_active) VALUES (?,'빈 학과','',true) RETURNING recruitment_unit_id",Long.class,a);
        mvc.perform(get("/api/v1/seo/urls"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.path=='/universities/"+a+"/')]").isNotEmpty())
            .andExpect(jsonPath("$[?(@.path=='/admissions/university/"+a+"/')]").isNotEmpty())
            .andExpect(jsonPath("$[?(@.path=='/universities/"+b+"/')]").isEmpty())
            .andExpect(jsonPath("$[?(@.path=='/admissions/unit/"+empty+"/')]").isEmpty())
            .andExpect(jsonPath("$[?(@.path=='/universities/indicators/employment-rate/')]").isNotEmpty());
    }
}

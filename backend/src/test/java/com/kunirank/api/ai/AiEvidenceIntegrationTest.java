package com.kunirank.api.ai;
import com.kunirank.api.ai.service.AiQuestionService;
import com.kunirank.api.admission.entity.*;
import com.kunirank.api.admission.repository.*;
import com.kunirank.api.university.entity.University;
import com.kunirank.api.university.repository.UniversityRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest @Testcontainers @Transactional
@TestPropertySource(properties="spring.jpa.hibernate.ddl-auto=validate")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AiEvidenceIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine").withInitScript("production-schema-fixture.sql");
    @Autowired AiQuestionService ai;
    @Autowired UniversityRepository schools;
    @Autowired RecruitmentUnitRepository units;
    @Autowired AdmissionSourceRepository sources;
    @Autowired AdmissionResultRepository results;
    @Autowired AdmissionMetricRepository metrics;
    private University school;
    private AdmissionSource universitySource;
    private RecruitmentUnit unit(String name) {return units.save(new RecruitmentUnit(school,name,"",true));}
    private void add(RecruitmentUnit unit,AdmissionSource source,String category,String selection,String c50,String c70) {
        var result=results.save(new AdmissionResult(source,school,unit,2026,"SUSI",category,selection));
        metrics.save(new AdmissionMetric(result,"STUDENT_GRADE_50_CUT",new BigDecimal(c50),"등급"));
        metrics.save(new AdmissionMetric(result,"STUDENT_GRADE_70_CUT",new BigDecimal(c70),"등급"));
    }
    @Test
    void gradeFreeLookupKeepsBothCutsAndSourceEvidenceWithoutInventingGaps() {
        school=schools.save(new University("한국항공대학교","경기",true));
        universitySource=sources.save(new AdmissionSource(school,2026,"UNIVERSITY","https://ibhak.kau.ac.kr/official"));
        add(unit("컴퓨터공학과"),universitySource,"학생부교과","일반","2.0","2.6");
        add(unit("컴퓨터보안학과"),universitySource,"학생부교과","일반","1.0","1.0");
        var reply=ai.ask(new com.kunirank.api.ai.dto.AiQuestion.Request("항공대 2026 컴공 교과 입결 알려줘",5));
        org.junit.jupiter.api.Assertions.assertNull(reply.admissions().grade());
        org.junit.jupiter.api.Assertions.assertEquals(1,reply.admissions().items().size());
        var row=reply.admissions().items().get(0);
        org.junit.jupiter.api.Assertions.assertNull(row.gap());
        org.junit.jupiter.api.Assertions.assertNull(row.absoluteGap());
        org.junit.jupiter.api.Assertions.assertEquals(0,new BigDecimal("2.6").compareTo(row.cut70()));
        org.junit.jupiter.api.Assertions.assertEquals("https://ibhak.kau.ac.kr/official",reply.evidence().get(0).sourceUrl());
        org.junit.jupiter.api.Assertions.assertEquals("lookup",reply.mode());
    }

    @Test
    void schoolComparisonKeepsEachSchoolsRowsAndDoesNotSubstituteRequestedYear() {
        school=schools.save(new University("한국항공대학교","경기",true));
        universitySource=sources.save(new AdmissionSource(school,2026,"UNIVERSITY","https://ibhak.kau.ac.kr/official"));
        add(unit("컴퓨터공학과"),universitySource,"학생부교과","일반","2.0","2.6");
        var reply=ai.ask(new com.kunirank.api.ai.dto.AiQuestion.Request("항공대 가천대 2026 컴공 교과 비교해줘",5));
        org.junit.jupiter.api.Assertions.assertEquals("schools",reply.mode());
        org.junit.jupiter.api.Assertions.assertEquals(2,reply.schools().size());
        for(var schoolReply:reply.schools()) {
            org.junit.jupiter.api.Assertions.assertEquals(2026,schoolReply.admissions().selectedYear());
            schoolReply.admissions().items().forEach(row->org.junit.jupiter.api.Assertions.assertEquals(schoolReply.name(),row.universityName()));
        }
        var missing=ai.ask(new com.kunirank.api.ai.dto.AiQuestion.Request("항공대 2025 컴공 입결 알려줘",5));
        org.junit.jupiter.api.Assertions.assertTrue(missing.admissions().items().isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(2025,missing.admissions().selectedYear());
    }

}

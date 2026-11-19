package com.kunirank.api.ai.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunirank.api.ai.dto.AiQuestion;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class AiConversationPolicyTest {
    final ObjectMapper json=new ObjectMapper();
    final AiConversationPolicy policy=new AiConversationPolicy(new ExplicitQuestionPolicy(json),json);
    AiQuestion.Filters ask(String q,AiQuestion.Context context) {return policy.resolve(new AiQuestion.Request(q,5,context));}
    @Test void gradeIsOptionalAndNotInvented() {
        var f=ask("항공대 컴공 입결 알려줘",null);
        assertNull(f.grade()); assertEquals("all",f.scope()); assertEquals("한국항공대학교",f.university());
    }
    @Test void followupChangesOnlyExplicitConditions() {
        var first=ask("내신 2.8 2026 항공대 서울 경기 컴공 교과 찾아줘",null);
        var next=ask("학종으로 바꿔줘",AiConversationPolicy.context(first));
        assertEquals(first.grade(),next.grade()); assertEquals(first.year(),next.year());
        assertEquals(first.region(),next.region()); assertEquals(first.major(),next.major()); assertEquals("holistic",next.track());
        next=ask("서울만 보여줘",AiConversationPolicy.context(next)); assertEquals("서울",next.region());
        next=ask("전체 지역 전체 학과 등급 없이 조회",AiConversationPolicy.context(next));
        assertNull(next.region()); assertNull(next.major()); assertNull(next.grade());
    }
    @Test void resetDoesNotLeakPreviousUniversityOrGrade() {
        var first=ask("항공대 내신 2.8 컴공",null);
        var next=ask("새로 검색 가천대 간호학과 입결",AiConversationPolicy.context(first));
        assertNull(next.grade()); assertEquals("가천대학교",next.university()); assertFalse(next.major().contains("컴퓨터"));
    }
    @Test void gradeAmbiguityAndUnsupportedConditionsStillFailClosed() {
        for(String q:new String[]{"항공대 내신 2.8 3.5등급", "항공대 5등급제 2등급", "항공대 정시 백분위 95", "국립대만 내신 2.8"})
            assertThrows(ResponseStatusException.class,()->ask(q,null),q);
    }
    @Test void forgedContextIsRejectedBeforeDatabaseAccess() {
        var context=new AiQuestion.Context(new BigDecimal("10"),2026,"student","exclude","near","서울",null,null);
        assertEquals(422,assertThrows(ResponseStatusException.class,()->ask("컴공 찾아줘",context)).getStatusCode().value());
    }
    @Test void documentQuestionUsesPriorSchoolButExplicitYearWins() {
        var first=ask("항공대 2026 컴공 입결",null);
        var next=ask("2027 수능최저는?",AiConversationPolicy.context(first));
        assertEquals("한국항공대학교",next.university()); assertEquals(2027,next.year()); assertNull(next.grade());
        assertTrue(policy.documents("최저는?"));
    }
    @Test void officialDocumentsHaveRealPageCitationsAndNeverSubstituteYearOrSchool() {
        var guides=new OfficialGuideSearch(json);
        var result=guides.answer(ask("항공대 2027 수능최저 알려줘",null),"항공대 2027 수능최저 알려줘");
        assertFalse(result.passages().isEmpty());
        result.passages().forEach(p->{assertEquals(2027,p.year());assertTrue(p.page()>=43 && p.page()<=51);assertTrue(p.sourceUrl().startsWith("https://ibhak.kau.ac.kr/"));assertTrue(p.sourceUrl().endsWith("#page="+p.page()));});
        var specific=guides.answer(ask("항공대 2027 학교장추천 수능최저",null),"항공대 2027 학교장추천 수능최저");
        assertFalse(specific.passages().isEmpty());
        specific.passages().forEach(p->assertTrue(p.page()>=47 && p.page()<=51));
        assertTrue(guides.answer(ask("항공대 2026 수능최저 알려줘",null),"최저").passages().isEmpty());
        assertTrue(guides.answer(ask("건국대 2027 면접 알려줘",null),"면접").passages().isEmpty());
    }
}

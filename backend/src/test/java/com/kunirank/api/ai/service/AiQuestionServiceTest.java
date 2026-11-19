package com.kunirank.api.ai.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunirank.api.admission.service.GradeComparisonService;
import com.kunirank.api.admission.dto.GradeComparisonResponse;
import com.kunirank.api.ai.dto.AiQuestion;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiQuestionServiceTest {
    private final ObjectMapper json=new ObjectMapper();
    private final AiConversationPolicy policy=new AiConversationPolicy(new ExplicitQuestionPolicy(json),json);
    @Test void knownUniversityDoesNotSpendModelRequestsAndKeepsExactConstraints() {
        var parser=mock(OpenRouterIntentParser.class); var comparisons=mock(GradeComparisonService.class);
        var evidence=mock(AiEvidenceService.class); var guides=mock(OfficialGuideSearch.class);
        var result=empty(new BigDecimal("2.5"),2026);
        when(comparisons.compare(any(),any(),any(),any(),any(),any(),any(),any(),anyInt(),anyInt(),any())).thenReturn(result);
        var response=new AiQuestionService(comparisons,parser,policy,evidence,guides)
                .ask(new AiQuestion.Request("내신 2.5 항공대 컴공 교과 찾아줘",null));
        assertEquals("한국항공대학교",response.context().university());
        verifyNoInteractions(parser);
        verify(comparisons).compare(new BigDecimal("2.5"),null,"student","exclude","near",2,
                "컴퓨터,소프트웨어,컴퓨팅",null,0,5,"한국항공대학교");
        assertTrue(response.evidencePolicy().contains("Spring Boot 조회 결과"));
    }
    @Test void missingRequestedYearNeverSilentlyUsesNewerResults() {
        var comparisons=mock(GradeComparisonService.class);
        when(comparisons.compare(any(),any(),any(),any(),any(),any(),any(),any(),anyInt(),anyInt(),any())).thenReturn(empty(null,2026));
        var response=new AiQuestionService(comparisons,mock(OpenRouterIntentParser.class),policy,mock(AiEvidenceService.class),mock(OfficialGuideSearch.class))
                .ask(new AiQuestion.Request("항공대 2025 컴공 입결 알려줘",5));
        assertEquals(2025,response.admissions().selectedYear()); assertEquals(0,response.admissions().totalCount());
        assertNull(response.filters().grade()); assertEquals("lookup",response.mode());
    }
    static GradeComparisonResponse empty(BigDecimal grade,Integer year) {
        return new GradeComparisonResponse(grade,year,List.of(2026),"student","exclude","all",2,0,0,0,5,false,List.of(),List.of());
    }
}

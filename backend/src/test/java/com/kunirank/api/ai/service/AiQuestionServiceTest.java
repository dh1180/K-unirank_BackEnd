package com.kunirank.api.ai.service;

import com.kunirank.api.admission.service.GradeComparisonService;
import com.kunirank.api.ai.dto.AiQuestion;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiQuestionServiceTest {
    @Test
    void usesIntegratedParserConstraintsForTheExistingAdmissionQuery() {
        var parser = mock(OpenRouterIntentParser.class);
        var comparisons = mock(GradeComparisonService.class);
        String question = "내신 2.5 항공대 컴공 교과 찾아줘";
        var filters = new AiQuestion.Filters(new BigDecimal("2.5"), null, "student", "exclude", "near",
                null, "컴퓨터,소프트웨어,컴퓨팅", "한국항공대학교", List.of(), 5);
        when(parser.extract(question, 5)).thenReturn(filters);
        var response = new AiQuestionService(comparisons, parser).ask(new AiQuestion.Request(question, null));
        assertSame(filters, response.filters());
        verify(comparisons).compare(filters.grade(), null, "student", "exclude", "near", 2,
                filters.major(), null, 0, 5, "한국항공대학교");
        assertTrue(response.evidencePolicy().contains("Spring Boot 조회 결과"));
    }
}

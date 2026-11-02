package com.kunirank.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunirank.api.ai.service.AiQuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class AiParserContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsOnlyTrustedTypedParserOutput() throws Exception {
        var parsed = AiQuestionService.parseFilters(mapper.readTree("""
          {"grade":"2.50","year":2026,"track":"student",
           "special":"exclude","scope":"near","region":"서울",
           "major":"컴퓨터","limit":5}
          """));
        assertEquals(new BigDecimal("2.50"), parsed.grade());
        assertEquals("컴퓨터", parsed.major());
        assertEquals(5, parsed.limit());
    }

    @Test
    void validatesNewConstraintsAndPreservesClarification() throws Exception {
        String valid = """
          {"grade":"3.20","year":null,"track":"student","special":"exclude","scope":"near",
           "region":"서울,경기,인천","major":"컴퓨터,소프트웨어","university":"한국항공대학교",
           "notes":["컴공 관련 학과를 함께 검색했습니다."],"limit":5}
          """;
        var filters = AiQuestionService.parseFilters(mapper.readTree(valid));
        assertEquals("한국항공대학교", filters.university());
        assertEquals(1, filters.notes().size());
        var group = AiQuestionService.parseFilters(mapper.readTree(valid.replace("한국항공대학교",
                "중앙대학교,경희대학교,한국외국어대학교,서울시립대학교")));
        assertEquals(4, group.university().split(",").length);
        assertThrows(ResponseStatusException.class, () -> AiQuestionService.parseFilters(
                mapper.readTree(valid.replace("서울,경기,인천", "수도권추측"))));
        assertEquals("정시 대신 수시 내신을 입력해 주세요.", AiQuestionService.clarificationDetail(
                mapper.readTree("{\"detail\":\"정시 대신 수시 내신을 입력해 주세요.\"}")));
    }

    @Test
    void rejectsForgedGradesAndArbitraryEnumsOrUnlimitedSize() throws Exception {
        for (String json : new String[]{
            "{\"grade\":\"0.5\",\"track\":\"student\",\"special\":\"exclude\",\"scope\":\"near\",\"limit\":5}",
            "{\"grade\":\"2.0\",\"track\":\"DROP TABLE users\",\"special\":\"exclude\",\"scope\":\"near\",\"limit\":5}",
            "{\"grade\":\"2.0\",\"track\":\"student\",\"special\":\"exclude\",\"scope\":\"near\",\"limit\":999}"
        }) {
            assertThrows(ResponseStatusException.class,
                    () -> AiQuestionService.parseFilters(mapper.readTree(json)));
        }
    }
}

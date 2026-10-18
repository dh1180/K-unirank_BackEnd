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

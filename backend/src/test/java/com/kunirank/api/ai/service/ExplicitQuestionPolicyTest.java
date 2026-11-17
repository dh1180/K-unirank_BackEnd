package com.kunirank.api.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunirank.api.ai.dto.AiQuestion;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ExplicitQuestionPolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ExplicitQuestionPolicy policy = new ExplicitQuestionPolicy(json);

    @TestFactory
    Stream<DynamicTest> matchesOriginalPythonPolicy() throws Exception {
        try (var input = getClass().getResourceAsStream("/ai-policy-parity.json")) {
            return json.readTree(input).valueStream().map(row -> DynamicTest.dynamicTest(row.path("question").asText(), () -> {
                String question = row.path("question").asText();
                if (row.has("error")) {
                    var error = assertThrows(ResponseStatusException.class, () -> policy.read(question));
                    assertEquals(422, error.getStatusCode().value());
                    assertEquals(row.path("error").asText(), error.getReason());
                } else {
                    var expected = json.treeToValue(row.get("filters"), AiQuestion.Filters.class);
                    assertEquals(expected, policy.resolve(policy.read(question), json.createObjectNode(), question, 5));
                }
            })).toList().stream();
        }
    }

    @Test
    void explicitConstraintsCannotBeReplacedByModelGuesses() throws Exception {
        String question = "내신 3.2 부산 지거국 컴공 교과 찾아줘";
        var explicit = policy.read(question);
        var resolved = policy.resolve(explicit, json.readTree("""
                {"grade":1,"year":2025,"region":"서울","university":"가천대학교","major":"간호","track":"holistic"}
                """), question, 4);
        assertEquals("부산", resolved.region());
        assertEquals(9, resolved.university().split(",").length);
        assertFalse(resolved.university().contains("서울대학교"));
        assertEquals("컴퓨터,소프트웨어,컴퓨팅", resolved.major());
        assertEquals("student", resolved.track());
        assertNull(resolved.year());
        assertEquals(4, resolved.limit());
    }

    @Test
    void modelCannotTurnGroupIntoMajorOrInventUnmentionedMajor() throws Exception {
        String question = "건동홍 2.3등급으로 갈만한 학과 찾아줘";
        var explicit = policy.read(question);
        for (String major : new String[] {"건동홍", "건국대학교", "갈만한 학과"}) {
            assertNull(policy.resolve(explicit, json.createObjectNode().put("major", major), question, 5).major());
        }
        assertThrows(ResponseStatusException.class,
                () -> policy.resolve(explicit, json.createObjectNode().put("major", "간호"), question, 5));
    }
}

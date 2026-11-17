package com.kunirank.api.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenRouterIntentParserTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ExplicitQuestionPolicy policy = new ExplicitQuestionPolicy(json);
    private final HttpClient http = mock(HttpClient.class);

    private OpenRouterIntentParser parser(String key, String model, int limit) {
        return new OpenRouterIntentParser(json, policy, key, model, limit, 2, 30, http, Clock.systemUTC());
    }

    private void reply(String content) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(json.writeValueAsString(java.util.Map.of("choices",
                java.util.List.of(java.util.Map.of("message", java.util.Map.of("content", content))))));
        when(http.send(any(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
    }

    @Test
    void usesFixedEndpointFreeProviderAndExistingSchemaWithNoPaidFallback() throws Exception {
        reply("""
                {"grade":1,"year":2025,"track":"holistic","special":"all","scope":"all","region":"부산","major":"컴퓨터"}
                """);
        var filters = parser("isolated-test-key", "openrouter/free", 40).extract("내신 3.2 수도권 컴공 교과 찾아줘", 5);
        assertEquals("서울,경기,인천", filters.region());
        assertNull(filters.year());
        assertEquals("student", filters.track());
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("https://openrouter.ai/api/v1/chat/completions", request.getValue().uri().toString());
        assertEquals("Bearer isolated-test-key", request.getValue().headers().firstValue("Authorization").orElseThrow());
        var bytes = new java.io.ByteArrayOutputStream();
        request.getValue().bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk); }
            public void onError(Throwable error) { fail(error); }
            public void onComplete() {}
        });
        var body = json.readTree(bytes.toByteArray());
        assertEquals(0, body.at("/provider/max_price/prompt").asInt(-1));
        assertEquals(0, body.at("/provider/max_price/completion").asInt(-1));
        assertTrue(body.at("/provider/require_parameters").asBoolean());
        assertTrue(body.at("/response_format/json_schema/strict").asBoolean());
        assertEquals(768, body.path("max_tokens").asInt());
        assertFalse(body.has("models"));
        assertEquals("내신 3.2 수도권 컴공 교과 찾아줘", body.path("messages").get(body.path("messages").size() - 1).path("content").asText());
    }

    @Test
    void refusesMissingKeyPaidModelAndAmbiguousQuestionsBeforeAnyProviderCall() {
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> parser("", "openrouter/free", 40).extract("내신 3.0 컴공 찾아줘", 5)).getStatusCode().value());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> parser("test", "paid/model", 40).extract("내신 3.0 컴공 찾아줘", 5)).getStatusCode().value());
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> parser("test", "openrouter/free", 40).extract("정시 백분위 88 컴공 찾아줘", 5)).getStatusCode().value());
        verifyNoInteractions(http);
    }

    @Test
    void failuresKeepKnownUniversityConstraintsAndConsumeDailyBudget() throws Exception {
        when(http.send(any(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(new IOException("offline"));
        var parser = parser("test", "openrouter/free", 1);
        var fallback = parser.extract("지거국 3.0등급으로 갈만한 학과 찾아줘", 5);
        assertEquals(9, fallback.university().split(",").length);
        assertNull(fallback.major());
        assertEquals(429, assertThrows(ResponseStatusException.class,
                () -> parser.extract("지거국 3.0등급으로 갈만한 학과 찾아줘", 5)).getStatusCode().value());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> parser("test", "openrouter/free", 40).extract("내신 3.0 조건 검색해줘", 5)).getStatusCode().value());
    }

    @Test
    void rejectsUnexpectedModelFields() throws Exception {
        reply("""
                {"grade":3,"year":null,"track":"student","special":"exclude","scope":"near","region":null,"major":null,"sql":"DROP TABLE users"}
                """);
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> parser("test", "openrouter/free", 40).extract("내신 3.0 조건 검색해줘", 5)).getStatusCode().value());
    }

    @Test
    void malformedProviderFiltersStillUseOnlyExplicitKnownSubjects() throws Exception {
        for (String content : new String[] {"{}", "null", "not json", """
                {"grade":1,"major":"다른학과","sql":"unexpected"}
                """}) {
            reply(content);
            var parsed = parser("test", "openrouter/free", 40)
                    .extract("내신 2.5 한국항공대 컴공 교과 찾아줘", 5);
            assertEquals("한국항공대학교", parsed.university());
            assertEquals("컴퓨터,소프트웨어,컴퓨팅", parsed.major());
            assertEquals(new java.math.BigDecimal("2.5"), parsed.grade());
            assertEquals("student", parsed.track());
        }
    }

    @Test
    void budgetReleasesConcurrentSlotsAndResetsOnlyDailyCountAtUtcBoundary() {
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-17T23:59:59Z"));
        var budget = new AiRequestBudget(clock);
        budget.enter(1, 1);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-18T00:00:00Z"));
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> budget.enter(1, 1)).getStatusCode().value());
        budget.leave();
        budget.enter(1, 1);
        budget.leave();
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> budget.enter(1, 1)).getStatusCode().value());
    }
}

package com.kunirank.api.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunirank.api.ai.dto.AiQuestion;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OpenRouterIntentParser {
    private static final URI ENDPOINT = URI.create("https://openrouter.ai/api/v1/chat/completions");
    private static final Set<String> FIELDS = Set.of("grade", "year", "track", "special", "scope", "region", "major");
    private final ObjectMapper json;
    private final ExplicitQuestionPolicy policy;
    private final HttpClient http;
    private final String key;
    private final String model;
    private final int dailyLimit;
    private final int concurrency;
    private final Duration timeout;
    private final ObjectNode template;
    private final AiRequestBudget budget;

    @Autowired
    public OpenRouterIntentParser(ObjectMapper json, ExplicitQuestionPolicy policy,
            @Value("${OPENROUTER_API_KEY:}") String key,
            @Value("${LLM_MODEL:openrouter/free}") String model,
            @Value("${DAILY_REQUEST_LIMIT:40}") int dailyLimit,
            @Value("${MAX_AI_CONCURRENCY:2}") int concurrency,
            @Value("${LLM_TIMEOUT_SECONDS:30}") int timeoutSeconds) {
        this(json, policy, key, model, dailyLimit, concurrency, timeoutSeconds,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NEVER).build(), Clock.systemUTC());
    }

    OpenRouterIntentParser(ObjectMapper json, ExplicitQuestionPolicy policy, String key, String model,
                           int dailyLimit, int concurrency, int timeoutSeconds, HttpClient http, Clock clock) {
        this.json = json;
        this.policy = policy;
        this.key = key;
        this.model = model;
        this.dailyLimit = dailyLimit;
        this.concurrency = concurrency;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = http;
        this.budget = new AiRequestBudget(clock);
        if (dailyLimit < 1 || dailyLimit > 1000 || concurrency < 1 || concurrency > 100
                || timeoutSeconds < 2 || timeoutSeconds > 30) throw new IllegalArgumentException("AI 요청 제한 설정을 확인해 주세요.");
        try (var input = getClass().getResourceAsStream("/ai/openrouter-request.json")) {
            template = (ObjectNode) json.readTree(Objects.requireNonNull(input));
        } catch (IOException ex) {
            throw new IllegalStateException("AI 검색 조건 설정을 읽을 수 없습니다.", ex);
        }
    }

    public AiQuestion.Filters extract(String question, int limit) {
        if (!(model.equals("openrouter/free") || model.endsWith(":free")))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "무료 모델만 사용할 수 있습니다.");
        if (key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "LLM API가 설정되지 않았습니다. 정형 검색 기능을 이용해 주세요.");
        var explicit = policy.read(question);
        budget.enter(dailyLimit, concurrency);
        try {
            ObjectNode body = template.deepCopy();
            body.put("model", model);
            body.withArray("messages").addObject().put("role", "user").put("content", question);
            var request = HttpRequest.newBuilder(ENDPOINT).timeout(timeout)
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("HTTP-Referer", "https://k-unirank.com")
                    .header("X-OpenRouter-Title", "K-unirank")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IOException("Provider unavailable");
            JsonNode message = json.readTree(response.body()).path("choices").path(0).path("message");
            if (!message.path("refusal").isMissingNode() && !message.path("refusal").isNull()
                    && !message.path("refusal").asText().isBlank())
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LLM이 질문 처리를 거부했습니다.");
            JsonNode parsed = json.readTree(message.path("content").asText());
            Set<String> fields = new HashSet<>();
            if (parsed != null) parsed.fieldNames().forEachRemaining(fields::add);
            if (parsed == null || !parsed.isObject() || !FIELDS.equals(fields)
                    || !(parsed.path("major").isNull() || parsed.path("major").isTextual()
                        && parsed.path("major").asText().length() <= 100))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LLM 검색 조건 형식이 잘못되었습니다.");
            return validated(policy.resolve(explicit, parsed, question, limit));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 요청이 중단되었습니다.");
        } catch (IOException | IllegalArgumentException ex) {
            if (explicit.hasKnownSubject()) return validated(policy.resolve(explicit, json.createObjectNode(), question, limit));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "질문을 해석하지 못했습니다. 관심 학과나 대학 이름을 함께 입력해 주세요.");
        } finally {
            budget.leave();
        }
    }

    private AiQuestion.Filters validated(AiQuestion.Filters filters) {
        return AiQuestionService.parseFilters(json.valueToTree(filters));
    }
}

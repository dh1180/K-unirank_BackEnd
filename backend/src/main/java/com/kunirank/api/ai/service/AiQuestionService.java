package com.kunirank.api.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunirank.api.admission.service.GradeComparisonService;
import com.kunirank.api.ai.dto.AiQuestion;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spring owns PostgreSQL reads. The Raspberry Pi only parses filters over
 * authenticated HTTPS; it never needs inbound access to Railway private DB.
 */
@Service
public class AiQuestionService {
    private final GradeComparisonService comparisons;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final Semaphore permits = new Semaphore(3);
    private final String parserUrl;
    private final String serviceToken;
    private final String cfClientId;
    private final String cfClientSecret;

    public AiQuestionService(GradeComparisonService comparisons, ObjectMapper json,
                             @Value("${AI_PARSER_URL:}") String parserUrl,
                             @Value("${AI_INTERNAL_TOKEN:}") String serviceToken,
                             @Value("${AI_CF_CLIENT_ID:}") String cfClientId,
                             @Value("${AI_CF_CLIENT_SECRET:}") String cfClientSecret) {
        this.comparisons = comparisons;
        this.json = json;
        this.parserUrl = parserUrl;
        this.serviceToken = serviceToken;
        this.cfClientId = cfClientId;
        this.cfClientSecret = cfClientSecret;
    }

    public AiQuestion.Response ask(AiQuestion.Request request) {
        if (serviceToken.length() < 32 || parserUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 연동이 설정되지 않았습니다.");
        }
        URI target;
        try {
            target = URI.create(parserUrl);
            if (!"https".equals(target.getScheme()) || target.getHost() == null
                    || !target.getPath().equals("/api/v1/ai/parse")
                    || target.getRawUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 연결 설정이 잘못되었습니다.");
        }
        if (!permits.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 요청이 많습니다.");
        }
        try {
            var body = json.writeValueAsString(new AiQuestion.Request(
                    request.question(), request.limit() == null ? 5 : request.limit()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(9))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Internal-Service-Token", serviceToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (!cfClientId.isEmpty() && !cfClientSecret.isEmpty()) {
                builder.header("CF-Access-Client-Id", cfClientId)
                        .header("CF-Access-Client-Secret", cfClientSecret);
            }
            HttpResponse<String> result = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (result.statusCode() == 422) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "내신 평균등급을 포함하여 질문해 주세요.");
            }
            if (result.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 해석 서비스가 응답하지 않습니다.");
            }
            JsonNode root = json.readTree(result.body());
            AiQuestion.Filters filters = parseFilters(root);
            var admissions = comparisons.compare(filters.grade(), filters.year(), filters.track(),
                    filters.special(), filters.scope(), 2,
                    filters.major(), filters.region(), 0, filters.limit());
            return new AiQuestion.Response(filters, admissions,
                    "입결 숫자·모집단위는 Spring Boot 조회 결과만 사용하며 합격을 보장하지 않습니다.");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 서비스 연결에 실패했습니다.");
        } finally {
            permits.release();
        }
    }

    // Validate the Pi/LLM response again at the trust boundary.
    public static AiQuestion.Filters parseFilters(JsonNode root) {
        try {
            BigDecimal grade = new BigDecimal(root.path("grade").asText());
            if (grade.compareTo(BigDecimal.ONE) < 0 || grade.compareTo(new BigDecimal("9")) > 0
                    || grade.scale() > 2) throw new IllegalArgumentException();
            Integer year = root.path("year").isNull() ? null : root.path("year").asInt();
            if (year != null && (year < 2000 || year > 2100)) throw new IllegalArgumentException();
            String track = root.path("track").asText();
            String special = root.path("special").asText();
            String scope = root.path("scope").asText();
            if (!Set.of("student", "holistic").contains(track)
                    || !Set.of("exclude", "all").contains(special)
                    || !Set.of("near", "all").contains(scope)) throw new IllegalArgumentException();
            String region = root.path("region").isNull() ? null : root.path("region").asText();
            String major = root.path("major").isNull() ? null : root.path("major").asText();
            if (region != null && region.length() > 60 || major != null && major.length() > 100)
                throw new IllegalArgumentException();
            int limit = root.path("limit").asInt();
            if (limit < 1 || limit > 10) throw new IllegalArgumentException();
            return new AiQuestion.Filters(grade, year, track, special, scope, region, major, limit);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI가 유효하지 않은 검색 조건을 반환했습니다.");
        }
    }
}

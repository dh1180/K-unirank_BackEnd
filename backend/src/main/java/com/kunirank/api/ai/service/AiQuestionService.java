package com.kunirank.api.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunirank.api.admission.service.GradeComparisonService;
import com.kunirank.api.ai.dto.AiQuestion;
import java.math.BigDecimal;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spring parses questions through OpenRouter and composes answers from PostgreSQL evidence.
 */
@Service
public class AiQuestionService {
    private final GradeComparisonService comparisons;
    private final OpenRouterIntentParser parser;
    private final Semaphore permits = new Semaphore(3);

    public AiQuestionService(GradeComparisonService comparisons, OpenRouterIntentParser parser) {
        this.comparisons = comparisons;
        this.parser = parser;
    }

    public AiQuestion.Response ask(AiQuestion.Request request) {
        if (!permits.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 요청이 많습니다.");
        }
        try {
            AiQuestion.Filters filters = parser.extract(request.question(), request.limit() == null ? 5 : request.limit());
            var admissions = comparisons.compare(filters.grade(), filters.year(), filters.track(),
                    filters.special(), filters.scope(), 2,
                    filters.major(), filters.region(), 0, filters.limit(), filters.university());
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

    public static String clarificationDetail(JsonNode root) {
        JsonNode detail = root.path("detail");
        return detail.isTextual() && !detail.asText().isBlank() && detail.asText().length() <= 200
                ? detail.asText() : "내신 평균등급을 포함하여 질문해 주세요.";
    }

    // Validate resolved constraints before passing them to database queries.
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
            String university = root.path("university").isMissingNode() || root.path("university").isNull()
                    ? null : root.path("university").asText();
            Set<String> validRegions = Set.of("서울", "경기", "인천", "부산", "대구", "광주", "대전", "울산", "세종", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");
            if (region != null && !region.isBlank() && !validRegions.containsAll(Arrays.asList(region.split(",", -1))))
                throw new IllegalArgumentException();
            if (major != null && major.split(",", -1).length > 5
                    || university != null && (university.length() > 160 || university.split(",", -1).length > 10))
                throw new IllegalArgumentException();
            List<String> notes = new ArrayList<>();
            if (root.has("notes")) {
                if (!root.path("notes").isArray() || root.path("notes").size() > 5) throw new IllegalArgumentException();
                for (JsonNode note : root.path("notes")) {
                    if (!note.isTextual() || note.asText().length() > 180) throw new IllegalArgumentException();
                    notes.add(note.asText());
                }
            }
            if (region != null && region.length() > 60 || major != null && major.length() > 100)
                throw new IllegalArgumentException();
            int limit = root.path("limit").asInt();
            if (limit < 1 || limit > 10) throw new IllegalArgumentException();
            return new AiQuestion.Filters(grade, year, track, special, scope, region, major, university, List.copyOf(notes), limit);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI가 유효하지 않은 검색 조건을 반환했습니다.");
        }
    }
}

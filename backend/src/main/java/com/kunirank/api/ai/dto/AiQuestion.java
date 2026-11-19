package com.kunirank.api.ai.dto;

import com.kunirank.api.admission.dto.GradeComparisonResponse;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import com.kunirank.api.university.service.UniversityOverviewService;
import java.math.BigDecimal;
import java.util.List;

public final class AiQuestion {
    private AiQuestion() {}

    public record Request(@NotBlank @Size(min = 2, max = 500) String question,
                          @Min(1) @Max(10) Integer limit, @Valid Context context) {
        public Request(String question, Integer limit) { this(question, limit, null); }
    }

    public record Context(@DecimalMin("1") @DecimalMax("9") @Digits(integer=1, fraction=2) BigDecimal grade,
            @Min(2000) @Max(2100) Integer year,
            @Pattern(regexp="student|holistic") String track,
            @Pattern(regexp="exclude|all") String special,
            @Pattern(regexp="near|all") String scope,
            @Size(max=60) String region, @Size(max=100) String major, @Size(max=160) String university) {}

    public record Filters(BigDecimal grade, Integer year, String track, String special,
                          String scope, String region, String major, String university, List<String> notes, int limit) {}

    public record Response(Filters filters, GradeComparisonResponse admissions,
                           String evidencePolicy, String mode, String summary, Context context,
            List<Evidence> evidence, List<School> schools, List<Passage> passages) {
        public Response(Filters filters, GradeComparisonResponse admissions, String evidencePolicy) {
            this(filters, admissions, evidencePolicy, "comparison", null, null, List.of(), List.of(), List.of());
        }
    }
    public record Evidence(Long resultId, String sourceUrl, Integer recruitmentCount,
            BigDecimal competitionRate, String explanation) {}
    public record School(String name, GradeComparisonResponse admissions, UniversityOverviewService.Overview overview) {}
    public record Passage(String university, int year, String title, int page, String text, String sourceUrl) {}
}

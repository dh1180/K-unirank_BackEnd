package com.kunirank.api.ai.dto;

import com.kunirank.api.admission.dto.GradeComparisonResponse;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class AiQuestion {
    private AiQuestion() {}

    public record Request(@NotBlank @Size(min = 8, max = 500) String question,
                          @Min(1) @Max(10) Integer limit) {}

    public record Filters(BigDecimal grade, Integer year, String track, String special,
                          String scope, String region, String major, int limit) {}

    public record Response(Filters filters, GradeComparisonResponse admissions,
                           String evidencePolicy) {}
}

package com.kunirank.api.admission.dto;

import java.math.BigDecimal;
import java.util.List;

public record GradeComparisonResponse(
        BigDecimal grade, Integer selectedYear, List<Integer> availableYears,
        String track, String special, String scope, int chartMin,
        int totalCount, int chartUniversityCount, int page, int size,
        boolean hasNext, List<GradeComparisonRow> items, List<GradeComparisonChart> charts
) {
}

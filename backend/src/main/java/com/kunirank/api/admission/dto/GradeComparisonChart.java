package com.kunirank.api.admission.dto;

import java.math.BigDecimal;

public record GradeComparisonChart(
        Long universityId, String universityName,
        BigDecimal averageCut, int sampleCount, String referenceBasis,
        String positionLabel, String positionTone, BigDecimal absoluteGap
) {
}

package com.kunirank.api.admission.dto;

import java.math.BigDecimal;

public record GradeComparisonRow(
        Long resultId, Long universityId, String universityName,
        Long recruitmentUnitId, String recruitmentUnitName,
        String selectionCategory, String selectionName,
        BigDecimal cut50, BigDecimal cut70, BigDecimal referenceCut,
        String referenceBasis, String sourceType, String sourceName,
        String positionLabel, String positionTone, BigDecimal gap, BigDecimal absoluteGap
) {
}

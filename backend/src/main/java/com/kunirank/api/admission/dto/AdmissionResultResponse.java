package com.kunirank.api.admission.dto;

import com.kunirank.api.admission.entity.AdmissionResult;
import java.math.BigDecimal;
import java.util.List;

public record AdmissionResultResponse(
        Long id, int admissionYear, Long universityId, String universityName,
        Long recruitmentUnitId, String recruitmentUnitName,
        String phase, String selectionCategory, String selectionName,
        String sourceType, String sourceUrl, Integer recruitmentCount,
        BigDecimal competitionRate, List<AdmissionMetricResponse> metrics
) {
    public static AdmissionResultResponse from(AdmissionResult result, List<AdmissionMetricResponse> metrics) {
        return new AdmissionResultResponse(
                result.getId(), result.getAdmissionYear(), result.getUniversity().getId(),
                result.getUniversity().getName(), result.getUnit().getId(),
                result.getUnit().getName(), result.getPhase(), result.getSelectionCategory(),
                result.getSelectionName(), result.getSource().getSourceType(),
                result.getSource().getSourceUrl(), result.getRecruitmentCount(),
                result.getCompetitionRate(), metrics
        );
    }
}

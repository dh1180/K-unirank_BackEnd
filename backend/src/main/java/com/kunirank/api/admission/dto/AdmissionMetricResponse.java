package com.kunirank.api.admission.dto;

import com.kunirank.api.admission.entity.AdmissionMetric;
import java.math.BigDecimal;

public record AdmissionMetricResponse(String code, String unit, BigDecimal value) {
    public static AdmissionMetricResponse from(AdmissionMetric metric) {
        return new AdmissionMetricResponse(metric.getMetricCode(), metric.getUnit(), metric.getValue());
    }
}

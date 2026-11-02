package com.kunirank.api.admission.policy;

import com.kunirank.api.admission.entity.AdmissionMetric;
import java.math.BigDecimal;

/** University-specific aggregation exclusions, preserving the collected disclosure. */
public final class UniversityMetricQuality {
    private UniversityMetricQuality() {}
    public static boolean include(String university,String code,String unit,BigDecimal value) {
        if(!AdmissionMetricQuality.valid(code,unit,value)) return false;
        return !("신경주대학교".equals(university) && (code.contains("PERCENTILE") || "백분위".equals(unit))
            && value.compareTo(BigDecimal.valueOf(99))==0);
    }
    public static boolean include(AdmissionMetric m) {
        return include(m.getResult().getUniversity().getName(),m.getMetricCode(),m.getUnit(),m.getValue());
    }
}

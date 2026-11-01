package com.kunirank.api.admission.policy;

import com.kunirank.api.admission.entity.AdmissionMetric;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.util.Locale;

/** Presentation quarantine; never changes source rows. Keep Java and SQL criteria equivalent. */
public final class AdmissionMetricQuality {
    private AdmissionMetricQuality() {}
    public static boolean valid(String code,String unit,BigDecimal value) {
        if(code==null || value==null || code.toUpperCase(Locale.ROOT).contains("REFERENCE_MEAN")) return false;
        boolean percentile=code.contains("PERCENTILE") || "백분위".equals(unit);
        boolean grade=(code.contains("GRADE") || "등급".equals(unit)) && !code.contains("STDDEV");
        if(percentile && (value.signum()<=0 || value.compareTo(BigDecimal.valueOf(100))>0)) return false;
        if(grade && (value.compareTo(BigDecimal.ONE)<0 || value.compareTo(BigDecimal.valueOf(9))>0)) return false;
        if(code.startsWith("CSAT_PERCENTILE_MEAN") && (value.compareTo(BigDecimal.ONE)<=0 ||
            (value.compareTo(new BigDecimal("99.985"))>=0 && value.compareTo(new BigDecimal("99.995"))<0))) return false;
        return true;
    }
    public static boolean valid(AdmissionMetric metric) {return valid(metric.getMetricCode(),metric.getUnit(),metric.getValue());}
    public static Predicate predicate(Root<AdmissionMetric> metric,CriteriaBuilder cb) {
        Expression<String> code=metric.get("metricCode"),unit=metric.get("unit");
        Expression<BigDecimal> value=metric.get("value");
        Predicate percentile=cb.or(cb.like(code,"%PERCENTILE%"),cb.equal(unit,"백분위"));
        Predicate grade=cb.and(cb.notLike(code,"%STDDEV%"),cb.or(cb.like(code,"%GRADE%"),cb.equal(unit,"등급")));
        Predicate badMean=cb.and(cb.like(code,"CSAT_PERCENTILE_MEAN%"),cb.or(cb.lessThanOrEqualTo(value,BigDecimal.ONE),
            cb.and(cb.greaterThanOrEqualTo(value,new BigDecimal("99.985")),cb.lessThan(value,new BigDecimal("99.995")))));
        return cb.and(cb.notLike(cb.upper(code),"%REFERENCE_MEAN%"),cb.not(badMean),
            cb.or(cb.not(percentile),cb.and(cb.greaterThan(value,BigDecimal.ZERO),cb.lessThanOrEqualTo(value,BigDecimal.valueOf(100)))),
            cb.or(cb.not(grade),cb.between(value,BigDecimal.ONE,BigDecimal.valueOf(9))));
    }
}

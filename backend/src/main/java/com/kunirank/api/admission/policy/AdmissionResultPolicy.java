package com.kunirank.api.admission.policy;

import com.kunirank.api.admission.entity.AdmissionMetric;
import com.kunirank.api.admission.entity.AdmissionResult;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Policies equivalent to the Django admission filter and grade_cut_quality service.
 * A suspect result is hidden only if BOTH student-grade cuts are exactly 1.0,
 * excluding medical, dental, Korean-medical, pharmacy and veterinary majors.
 * Filtering never mutates the original PostgreSQL rows.
 */
public final class AdmissionResultPolicy {
    private AdmissionResultPolicy() {}

    private static final String[] PROTECTED = {
            "의예", "의학과", "의학부", "의학전공", "의학계열", "의과대학",
            "치의예", "치의학", "치과대학",
            "한의예", "한의학", "한의과대학",
            "약학", "제약학과", "제약학부",
            "수의예", "수의학", "수의과대학"
    };

    public static Specification<AdmissionResult> visible() {
        return (root, query, cb) -> {
            Predicate protectedMajor = cb.disjunction();
            Expression<String> name = cb.lower(root.get("unit").get("name"));
            Expression<String> college = cb.lower(root.get("unit").get("collegeName"));
            for (String term : PROTECTED) {
                protectedMajor = cb.or(protectedMajor,
                        cb.like(name, "%" + term + "%"),
                        cb.like(college, "%" + term + "%"));
            }
            Predicate bothOne = cb.and(
                    metricExists(root, query, cb, "STUDENT_GRADE_50_CUT", true),
                    metricExists(root, query, cb, "STUDENT_GRADE_70_CUT", true)
            );
            return cb.not(cb.and(bothOne, cb.not(protectedMajor)));
        };
    }

    public static Specification<AdmissionResult> hasPublicMetric() {
        return (root, query, cb) -> {
            Subquery<Long> exists = query.subquery(Long.class);
            Root<AdmissionMetric> m = exists.from(AdmissionMetric.class);
            exists.select(m.get("id")).where(
                    cb.equal(m.get("result").get("id"), root.get("id")),
                    cb.notLike(cb.upper(m.get("metricCode")), "%REFERENCE_MEAN%")
            );
            return cb.exists(exists);
        };
    }

    private static Predicate metricExists(Root<AdmissionResult> root, CriteriaQuery<?> query,
                                          CriteriaBuilder cb, String code, boolean equalsOne) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<AdmissionMetric> metric = sub.from(AdmissionMetric.class);
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.equal(metric.get("result").get("id"), root.get("id")));
        conditions.add(cb.equal(metric.get("metricCode"), code));
        if (equalsOne) {
            conditions.add(cb.equal(metric.get("value"), new BigDecimal("1.0")));
        }
        sub.select(metric.get("id")).where(conditions.toArray(Predicate[]::new));
        return cb.exists(sub);
    }

    public static Specification<AdmissionResult> inYear(int year) {
        return (root, query, cb) -> cb.equal(root.get("admissionYear"), year);
    }

    public static Specification<AdmissionResult> inUnit(long unitId) {
        return (root, query, cb) -> cb.equal(root.get("unit").get("id"), unitId);
    }

    public static Specification<AdmissionResult> searchable(String raw) {
        return (root, query, cb) -> {
            if (raw == null || raw.isBlank()) return cb.conjunction();
            String escaped = raw.strip().toLowerCase(java.util.Locale.ROOT)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            String token = "%" + escaped + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("university").get("name")), token, '\\'),
                    cb.like(cb.lower(root.get("unit").get("name")), token, '\\'),
                    cb.like(cb.lower(root.get("selectionName")), token, '\\'),
                    cb.like(cb.lower(root.get("selectionCategory")), token, '\\')
            );
        };
    }

    public static Specification<AdmissionResult> filtered(String kind, String phase, String track) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("university").get("active")));
            predicates.add(cb.isTrue(root.get("unit").get("active")));

            if ("four".equals(kind)) {
                predicates.add(root.get("source").get("sourceType").in("ADIGA", "UNIVERSITY"));
            } else if ("college".equals(kind)) {
                predicates.add(cb.equal(root.get("source").get("sourceType"), "PROCOLLEGE"));
            }

            String effectivePhase = phase;
            if ((effectivePhase == null || effectivePhase.isBlank())) {
                if (List.of("student", "holistic", "essay").contains(track)) effectivePhase = "SUSI";
                if ("csat".equals(track)) effectivePhase = "JEONGSI";
            }
            if ("SUSI".equals(effectivePhase) || "JEONGSI".equals(effectivePhase)) {
                predicates.add(cb.equal(root.get("phase"), effectivePhase));
            }

            String cat = "%" + switch (track == null ? "" : track) {
                case "student" -> "교과";
                case "holistic" -> "종합";
                case "csat" -> "수능";
                case "essay" -> "논술";
                case "practical" -> "실기";
                default -> "";
            } + "%";
            if (!"%%".equals(cat)) {
                if ("practical".equals(track)) {
                    predicates.add(cb.or(
                            cb.like(root.get("selectionCategory"), "%실기%"),
                            cb.like(root.get("selectionCategory"), "%실적%"),
                            cb.like(root.get("selectionName"), "%실기%"),
                            cb.like(root.get("selectionName"), "%실적%")
                    ));
                } else {
                    predicates.add(cb.or(
                            cb.like(root.get("selectionCategory"), cat),
                            cb.like(root.get("selectionName"), cat)
                    ));
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

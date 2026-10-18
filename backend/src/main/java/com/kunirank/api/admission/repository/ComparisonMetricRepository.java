package com.kunirank.api.admission.repository;

import com.kunirank.api.admission.entity.AdmissionMetric;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Fetch only grade-cut metrics for eligible sources/active universities.
 * Filtering of restricted tracks and source deduplication happens AFTER all
 * metrics are loaded for a result, matching the Django implementation.
 */
public interface ComparisonMetricRepository extends Repository<AdmissionMetric, Long> {
    @Query("""
       select m from AdmissionMetric m
       join fetch m.result r
       join fetch r.university u
       join fetch r.unit unit
       join fetch r.source s
       where r.admissionYear = :year and r.phase = 'SUSI'
         and r.selectionCategory = :category
         and s.sourceType in ('ADIGA', 'UNIVERSITY')
         and u.active = true and unit.active = true
         and m.metricCode in ('STUDENT_GRADE_50_CUT', 'STUDENT_GRADE_70_CUT')
         and m.value between 1 and 9
       order by r.id asc, m.metricCode asc
       """)
    List<AdmissionMetric> findComparisonMetrics(@Param("year") int year,
                                                @Param("category") String category);

    @Query("""
       select distinct r.admissionYear from AdmissionMetric m
       join m.result r join r.source s join r.university u join r.unit unit
       where r.phase = 'SUSI'
         and s.sourceType in ('ADIGA', 'UNIVERSITY')
         and u.active = true and unit.active = true
         and m.metricCode in ('STUDENT_GRADE_50_CUT', 'STUDENT_GRADE_70_CUT')
         and m.value between 1 and 9
       order by r.admissionYear desc
       """)
    List<Integer> availableYears();
}

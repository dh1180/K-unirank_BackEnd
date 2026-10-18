package com.kunirank.api.admission.repository;

import com.kunirank.api.admission.entity.AdmissionMetric;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionMetricRepository extends JpaRepository<AdmissionMetric, Long> {
    List<AdmissionMetric> findByResultIdInOrderByResultIdAscMetricCodeAsc(Collection<Long> resultIds);
}

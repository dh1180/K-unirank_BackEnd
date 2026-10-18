package com.kunirank.api.admission.repository;

import com.kunirank.api.admission.entity.AdmissionResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AdmissionResultRepository extends JpaRepository<AdmissionResult, Long>,
        JpaSpecificationExecutor<AdmissionResult> {
    @Override
    @EntityGraph(attributePaths = {"university", "unit", "source"})
    org.springframework.data.domain.Page<AdmissionResult> findAll(
            org.springframework.data.jpa.domain.Specification<AdmissionResult> spec,
            org.springframework.data.domain.Pageable pageable
    );
}

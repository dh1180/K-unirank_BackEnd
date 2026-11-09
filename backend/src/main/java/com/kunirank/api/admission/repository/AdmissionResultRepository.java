package com.kunirank.api.admission.repository;

import com.kunirank.api.admission.entity.AdmissionResult;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AdmissionResultRepository extends JpaRepository<AdmissionResult, Long>,
        JpaSpecificationExecutor<AdmissionResult> {
    default Optional<Integer> latestYear(Specification<AdmissionResult> spec) {
        return findBy(spec, query -> query.sortBy(Sort.by(Sort.Direction.DESC, "admissionYear")).first())
                .map(AdmissionResult::getAdmissionYear);
    }

    @Override
    @EntityGraph(attributePaths = {"university", "unit", "source"})
    org.springframework.data.domain.Page<AdmissionResult> findAll(
            org.springframework.data.jpa.domain.Specification<AdmissionResult> spec,
            org.springframework.data.domain.Pageable pageable
    );
}

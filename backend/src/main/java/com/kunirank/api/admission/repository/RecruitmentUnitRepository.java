package com.kunirank.api.admission.repository;

import com.kunirank.api.admission.entity.RecruitmentUnit;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitmentUnitRepository extends JpaRepository<RecruitmentUnit, Long> {
    Optional<RecruitmentUnit> findByIdAndActiveTrueAndUniversityActiveTrue(Long id);
    List<RecruitmentUnit> findByUniversityIdAndActiveTrueOrderByNameAscIdAsc(Long universityId);
}

package com.kunirank.api.university.repository;

import com.kunirank.api.university.entity.University;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UniversityRepository
        extends JpaRepository<University, Long>, JpaSpecificationExecutor<University> {
    List<University> findByActiveTrueOrderByNameAsc();
}

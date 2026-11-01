package com.kunirank.api.university.repository;

import com.kunirank.api.university.entity.University;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UniversityRepository
        extends JpaRepository<University, Long>, JpaSpecificationExecutor<University> {
    List<University> findByActiveTrueOrderByNameAsc();
    @Query("select distinct u.region from University u where u.active=true and u.region is not null and trim(u.region)<>'' order by u.region")
    List<String> findActiveRegions();
}

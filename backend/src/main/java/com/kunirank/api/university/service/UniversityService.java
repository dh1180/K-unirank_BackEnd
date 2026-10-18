package com.kunirank.api.university.service;

import com.kunirank.api.university.dto.UniversityPageResponse;
import com.kunirank.api.university.dto.UniversitySummaryResponse;
import com.kunirank.api.university.entity.University;
import com.kunirank.api.university.repository.UniversityRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UniversityService {

    private final UniversityRepository universityRepository;

    public UniversityService(UniversityRepository universityRepository) {
        this.universityRepository = universityRepository;
    }

    @Transactional(readOnly = true)
    public UniversityPageResponse findActiveUniversities(
            String query, String region, int page, int size
    ) {
        Specification<University> filters = (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isTrue(root.get("active")));

            if (query != null && !query.isBlank()) {
                String escaped = query.strip().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(builder.like(
                        builder.lower(root.get("name")), "%" + escaped + "%", '\\'
                ));
            }
            if (region != null && !region.isBlank()) {
                predicates.add(builder.equal(root.get("region"), region.strip()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        PageRequest pageable = PageRequest.of(
                page, size, Sort.by("name").ascending().and(Sort.by("id").ascending())
        );
        Page<UniversitySummaryResponse> result = universityRepository
                .findAll(filters, pageable)
                .map(UniversitySummaryResponse::from);
        return UniversityPageResponse.from(result);
    }
}

package com.kunirank.api.university.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record UniversityPageResponse(
        List<UniversitySummaryResponse> items,
        int page,
        int size,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static UniversityPageResponse from(Page<UniversitySummaryResponse> result) {
        return new UniversityPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }
}

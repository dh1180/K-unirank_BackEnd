package com.kunirank.api.university.dto;

import com.kunirank.api.university.entity.University;

public record UniversitySummaryResponse(
        Long id,
        String name,
        String shortName,
        String region,
        String logoPath
) {
    public static UniversitySummaryResponse from(University university) {
        return new UniversitySummaryResponse(
                university.getId(),
                university.getName(),
                university.getShortName(),
                university.getRegion(),
                university.getLogoPath()
        );
    }
}

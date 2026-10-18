package com.kunirank.api.university.dto;

import com.kunirank.api.admission.entity.RecruitmentUnit;
import com.kunirank.api.university.entity.University;
import java.util.List;

public record UniversityDetailResponse(
        Long id, String name, String shortName, String region, String address,
        String logoPath, String homepageUrl, String universityType,
        String establishmentType, List<UnitSummary> units
) {
    public record UnitSummary(Long id, String name, String collegeName) {
        public static UnitSummary from(RecruitmentUnit unit) {
            return new UnitSummary(unit.getId(), unit.getName(), unit.getCollegeName());
        }
    }

    public static UniversityDetailResponse from(University university, List<UnitSummary> units) {
        return new UniversityDetailResponse(
                university.getId(), university.getName(), university.getShortName(),
                university.getRegion(), university.getAddress(), university.getLogoPath(),
                university.getHomepageUrl(), university.getUniversityType(),
                university.getEstablishmentType(), units
        );
    }
}

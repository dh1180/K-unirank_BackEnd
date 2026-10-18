package com.kunirank.api.admission.service;

import com.kunirank.api.admission.dto.*;
import com.kunirank.api.admission.entity.*;
import com.kunirank.api.admission.policy.AdmissionResultPolicy;
import com.kunirank.api.admission.repository.*;
import com.kunirank.api.university.repository.UniversityRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdmissionQueryService {
    private final AdmissionResultRepository results;
    private final AdmissionMetricRepository metrics;
    private final RecruitmentUnitRepository units;
    private final UniversityRepository universities;

    public AdmissionQueryService(AdmissionResultRepository results,
                                 AdmissionMetricRepository metrics,
                                 RecruitmentUnitRepository units,
                                 UniversityRepository universities) {
        this.results = results;
        this.metrics = metrics;
        this.units = units;
        this.universities = universities;
    }

    @Transactional(readOnly = true)
    public AdmissionPageResponse search(Integer year, String q, String kind, String phase,
                                        String track, String metricsMode, int page, int size) {
        Specification<AdmissionResult> base = Specification.where(AdmissionResultPolicy.visible())
                .and(AdmissionResultPolicy.filtered(kind, phase, track))
                .and(AdmissionResultPolicy.searchable(q));
        boolean publicOnly = !"all".equalsIgnoreCase(metricsMode);
        if (publicOnly) base = base.and(AdmissionResultPolicy.hasPublicMetric());
        Integer selected = year;
        if (selected == null) {
            // Match Django's default: newest visible admission year.
            Page<AdmissionResult> latest = results.findAll(
                    base, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "admissionYear")
                            .and(Sort.by("id")))
            );
            selected = latest.isEmpty() ? null : latest.getContent().get(0).getAdmissionYear();
        }
        return page(base, selected, page, size);
    }

    @Transactional(readOnly = true)
    public AdmissionPageResponse forUniversity(long universityId, Integer year,
                                               String metricsMode, int page, int size) {
        universities.findById(universityId)
                .filter(com.kunirank.api.university.entity.University::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대학을 찾을 수 없습니다."));
        Specification<AdmissionResult> base = Specification.where(AdmissionResultPolicy.visible())
                .and((root, query, cb) -> cb.equal(root.get("university").get("id"), universityId))
                .and((root, query, cb) -> cb.isTrue(root.get("unit").get("active")));
        if (!"all".equalsIgnoreCase(metricsMode)) {
            base = base.and(AdmissionResultPolicy.hasPublicMetric());
        }
        return page(base, year, page, size);
    }

    @Transactional(readOnly = true)
    public AdmissionPageResponse forUnit(long unitId, Integer year, String metricsMode,
                                         int page, int size) {
        units.findByIdAndActiveTrueAndUniversityActiveTrue(unitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "모집단위를 찾을 수 없습니다."));
        Specification<AdmissionResult> base = Specification.where(AdmissionResultPolicy.visible())
                .and(AdmissionResultPolicy.inUnit(unitId));
        if (!"all".equalsIgnoreCase(metricsMode)) {
            base = base.and(AdmissionResultPolicy.hasPublicMetric());
        }
        return page(base, year, page, size);
    }

    private AdmissionPageResponse page(Specification<AdmissionResult> base, Integer year,
                                       int page, int size) {
        if (year != null) base = base.and(AdmissionResultPolicy.inYear(year));
        Page<AdmissionResult> selected = results.findAll(base, PageRequest.of(
                page, size, Sort.by("university.name", "phase", "selectionCategory",
                        "selectionName", "unit.name", "id")
        ));
        List<AdmissionResult> content = selected.getContent();
        Map<Long, List<AdmissionMetricResponse>> byResult = Collections.emptyMap();
        if (!content.isEmpty()) {
            byResult = metrics.findByResultIdInOrderByResultIdAscMetricCodeAsc(
                    content.stream().map(AdmissionResult::getId).toList()
            ).stream()
              .filter(metric -> !metric.getMetricCode().toUpperCase(Locale.ROOT).contains("REFERENCE_MEAN"))
              .collect(Collectors.groupingBy(
                      metric -> metric.getResult().getId(),
                      Collectors.mapping(AdmissionMetricResponse::from, Collectors.toList())
              ));
        }
        Map<Long, List<AdmissionMetricResponse>> grouped = byResult;
        List<AdmissionResultResponse> items = content.stream()
                .map(r -> AdmissionResultResponse.from(r,
                        grouped.getOrDefault(r.getId(), List.of())))
                .toList();
        return new AdmissionPageResponse(year, items, page, size, selected.getTotalElements(),
                selected.getTotalPages(), selected.hasNext());
    }
}

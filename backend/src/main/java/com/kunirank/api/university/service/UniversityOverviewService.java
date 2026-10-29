package com.kunirank.api.university.service;

import com.kunirank.api.admission.entity.AdmissionMetric;
import com.kunirank.api.admission.entity.AdmissionResult;
import com.kunirank.api.admission.policy.AdmissionResultPolicy;
import com.kunirank.api.admission.repository.AdmissionMetricRepository;
import com.kunirank.api.admission.repository.AdmissionResultRepository;
import com.kunirank.api.ranking.discovery.RankingDiscoveryService;
import com.kunirank.api.university.repository.UniversityRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class UniversityOverviewService {
    public record Indicator(String slug, String label, String kind, int year,
                            BigDecimal value, String sourceUrl) {}
    public record CutRange(String phase, String category, String code, String unit,
                           BigDecimal min, BigDecimal max, int sampleCount) {}
    public record Admissions(Integer year, long resultCount, long susiCount, long jeongsiCount,
                             List<CutRange> ranges) {}
    public record Overview(List<Indicator> indicators, Admissions admissions) {}
    private final JdbcTemplate jdbc;
    private final UniversityRepository universities;
    private final AdmissionResultRepository results;
    private final AdmissionMetricRepository metrics;

    public UniversityOverviewService(JdbcTemplate jdbc, UniversityRepository universities,
                                     AdmissionResultRepository results, AdmissionMetricRepository metrics) {
        this.jdbc = jdbc; this.universities = universities; this.results = results; this.metrics = metrics;
    }

    public Overview overview(long id) {
        universities.findById(id).filter(u -> u.isActive()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "대학을 찾을 수 없습니다."));
        // Latest positive official disclosure for each indicator, scoped to this university.
        Map<String, Indicator> official = new HashMap<>();
        jdbc.query("""
            SELECT DISTINCT ON (indicator_code) indicator_code, year, value, source_url
            FROM university_indicators
            WHERE university_id=? AND source='ACADEMYINFO' AND value>0
            ORDER BY indicator_code, year DESC, indicator_id DESC
            """, rs -> {
            String code = rs.getString("indicator_code");
            var spec = RankingDiscoveryService.INDICATORS.stream()
                    .filter(m -> m.code().equals(code)).findFirst();
            if (spec.isPresent()) {
                var m = spec.get();
                official.put(m.code(), new Indicator(m.slug(), m.label(), m.kind(),
                        rs.getInt("year"), rs.getBigDecimal("value"), rs.getString("source_url")));
            }
        }, id);
        List<Indicator> indicators = RankingDiscoveryService.INDICATORS.stream()
                .map(m -> official.get(m.code())).filter(Objects::nonNull).toList();

        Specification<AdmissionResult> base = AdmissionResultPolicy.visible()
                .and(AdmissionResultPolicy.filtered(null, null, null))
                .and(AdmissionResultPolicy.hasPublicMetric())
                .and((root, query, cb) -> cb.equal(root.get("university").get("id"), id));
        var latest = results.findAll(base, PageRequest.of(0, 1,
                Sort.by(Sort.Direction.DESC, "admissionYear").and(Sort.by("id"))));
        if (latest.isEmpty()) return new Overview(indicators, new Admissions(null, 0, 0, 0, List.of()));
        int year = latest.getContent().getFirst().getAdmissionYear();
        var published = results.findAll(base.and(AdmissionResultPolicy.inYear(year)));
        Map<String, List<AdmissionMetric>> grouped = new TreeMap<>();
        for (var metric : metrics.findByResultIdInOrderByResultIdAscMetricCodeAsc(
                published.stream().map(AdmissionResult::getId).toList())) {
            if (!isComparableCut(metric)) continue;
            var r = metric.getResult();
            String key = r.getPhase() + "\u001f" + r.getSelectionCategory() + "\u001f" + metric.getMetricCode();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(metric);
        }
        var ranges = grouped.values().stream().map(group -> {
            var first = group.getFirst(); var r = first.getResult();
            var values = group.stream().map(AdmissionMetric::getValue).toList();
            return new CutRange(r.getPhase(), r.getSelectionCategory(), first.getMetricCode(), first.getUnit(),
                    Collections.min(values), Collections.max(values), group.size());
        }).toList();
        return new Overview(indicators, new Admissions(year, published.size(),
                published.stream().filter(r -> "SUSI".equals(r.getPhase())).count(),
                published.stream().filter(r -> "JEONGSI".equals(r.getPhase())).count(), ranges));
    }

    private boolean isComparableCut(AdmissionMetric m) {
        String code = m.getMetricCode(); BigDecimal value = m.getValue();
        if (Set.of("STUDENT_GRADE_70_CUT", "COLLEGE_STUDENT_AVERAGE").contains(code)) {
            return "등급".equals(m.getUnit()) && value.compareTo(BigDecimal.ONE)>=0
                    && value.compareTo(BigDecimal.valueOf(9))<=0;
        }
        if (Set.of("CSAT_PERCENTILE_MEAN_70_CUT", "COLLEGE_CSAT_AVERAGE").contains(code)) {
            return "백분위".equals(m.getUnit()) && value.signum()>0
                    && value.compareTo(BigDecimal.valueOf(100))<=0;
        }
        return false;
    }
}

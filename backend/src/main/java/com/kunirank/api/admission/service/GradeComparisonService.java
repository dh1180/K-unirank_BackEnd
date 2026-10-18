package com.kunirank.api.admission.service;

import com.kunirank.api.admission.dto.*;
import com.kunirank.api.admission.entity.AdmissionMetric;
import com.kunirank.api.admission.entity.AdmissionResult;
import com.kunirank.api.admission.repository.ComparisonMetricRepository;
import com.kunirank.api.university.entity.University;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradeComparisonService {
    private static final BigDecimal MIN_GRADE = new BigDecimal("1.00");
    private static final BigDecimal MAX_GRADE = new BigDecimal("9.00");
    private static final BigDecimal HALF_RANGE = new BigDecimal("0.50");
    private static final BigDecimal NEAR = new BigDecimal("0.10");

    private static final List<String> RESTRICTED_SELECTION_TERMS = List.of(
            "농어촌", "농·어촌", "특성화고", "특성화 고", "특성화고교",
            "기초생활", "기초수급", "차상위", "저소득", "고른기회",
            "기회균형", "사회통합", "사회배려", "국가보훈",
            "보훈대상", "특수교육", "장애인", "서해5도", "북한이탈",
            "재직자", "만학도", "평생학습", "성인학습",
            "재외국민", "외국인", "다문화", "지역인재"
    );
    private static final List<String> PROTECTED_MAJOR_TERMS = List.of(
            "의예", "의학과", "의학부", "의학전공", "의학계열", "의과대학",
            "치의예", "치의학", "치과대학",
            "한의예", "한의학", "한의과대학",
            "약학", "제약학과", "제약학부",
            "수의예", "수의학", "수의과대학"
    );
    private static final Set<String> VALID_REGIONS = Set.of(
            "서울", "경기", "인천", "부산", "대구", "광주", "대전", "울산", "세종",
            "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"
    );
    private static final Map<String, List<String>> REGION_TOKENS = Map.ofEntries(
            Map.entry("광주", List.of("광주", "전남광주")),
            Map.entry("충북", List.of("충청북", "충북")),
            Map.entry("충남", List.of("충청남", "충남")),
            Map.entry("전북", List.of("전북", "전라북")),
            Map.entry("전남", List.of("전남", "전라남")),
            Map.entry("경북", List.of("경상북", "경북")),
            Map.entry("경남", List.of("경상남", "경남"))
    );

    private final ComparisonMetricRepository comparisonMetrics;

    public GradeComparisonService(ComparisonMetricRepository comparisonMetrics) {
        this.comparisonMetrics = comparisonMetrics;
    }

    @Transactional(readOnly = true)
    public GradeComparisonResponse compare(BigDecimal inputGrade, Integer requestedYear,
            String rawTrack, String rawSpecial, String rawScope, Integer requestedChartMin,
            String rawQuery, String rawRegion, int page, int size) {
        BigDecimal grade = inputGrade.setScale(2, RoundingMode.HALF_EVEN);
        String track = "holistic".equalsIgnoreCase(rawTrack) ? "holistic" : "student";
        String category = "student".equals(track) ? "학생부교과" : "학생부종합";
        String special = "all".equals(rawSpecial) ? "all" : "exclude";
        String scope = "all".equals(rawScope) ? "all" : "near";
        int chartMin = requestedChartMin != null && Set.of(1, 2, 3, 5).contains(requestedChartMin)
                ? requestedChartMin : 2;
        String region = rawRegion != null && VALID_REGIONS.contains(rawRegion.strip())
                ? rawRegion.strip() : "";
        List<String> terms = rawQuery == null ? List.of()
                : Arrays.stream(rawQuery.strip().split(","))
                    .map(String::strip).filter(t -> !t.isEmpty()).limit(5).toList();

        List<Integer> years = comparisonMetrics.availableYears();
        Integer selectedYear = requestedYear != null && years.contains(requestedYear)
                ? requestedYear : (years.isEmpty() ? null : years.get(0));
        if (selectedYear == null) {
            return new GradeComparisonResponse(grade, null, years, track, special, scope,
                    chartMin, 0, 0, page, size, false, List.of(), List.of());
        }

        // A single result may have both 50% and 70% cut metrics.
        // Source priority must be evaluated BEFORE the near-range filter.
        Map<Long, RawResult> rawResults = new LinkedHashMap<>();
        for (AdmissionMetric metric : comparisonMetrics.findComparisonMetrics(selectedYear, category)) {
            AdmissionResult result = metric.getResult();
            if ("exclude".equals(special) && RESTRICTED_SELECTION_TERMS.stream()
                    .anyMatch(result.getSelectionName()::contains)) continue;
            if (!terms.isEmpty() && terms.stream().noneMatch(term ->
                    result.getUnit().getName().toLowerCase(Locale.ROOT)
                            .contains(term.toLowerCase(Locale.ROOT)))) continue;
            if (!region.isEmpty() && !matchesRegion(result.getUniversity(), region)) continue;

            RawResult row = rawResults.computeIfAbsent(result.getId(), id -> new RawResult(result));
            if ("STUDENT_GRADE_70_CUT".equals(metric.getMetricCode())) {
                row.cut70 = metric.getValue();
            } else if ("STUDENT_GRADE_50_CUT".equals(metric.getMetricCode())) {
                row.cut50 = metric.getValue();
            }
        }

        Map<Key, GradeComparisonRow> deduplicated = new LinkedHashMap<>();
        for (RawResult raw : rawResults.values()) {
            AdmissionResult result = raw.result;
            // Both 1.0 values represent an outlier except the five protected professions.
            if (raw.cut50 != null && raw.cut70 != null
                    && raw.cut50.compareTo(BigDecimal.ONE) == 0
                    && raw.cut70.compareTo(BigDecimal.ONE) == 0
                    && !protectedMajor(result)) continue;

            BigDecimal reference = raw.cut70 != null ? raw.cut70 : raw.cut50;
            if (reference == null) continue;
            BigDecimal gap = grade.subtract(reference).setScale(2, RoundingMode.HALF_EVEN);
            GradeComparisonRow row = new GradeComparisonRow(
                    result.getId(), result.getUniversity().getId(), result.getUniversity().getName(),
                    result.getUnit().getId(), result.getUnit().getName(), result.getSelectionCategory(),
                    result.getSelectionName().isBlank() ? "전형명 미확인" : result.getSelectionName(),
                    raw.cut50, raw.cut70, reference,
                    raw.cut70 != null ? "70% 컷" : "50% 컷",
                    result.getSource().getSourceType(),
                    "UNIVERSITY".equals(result.getSource().getSourceType()) ? "대학 입학처" : "대입정보포털 어디가",
                    positionLabel(gap), positionTone(gap), gap, gap.abs()
            );
            Key key = new Key(row.universityId(), row.recruitmentUnitId(),
                    row.selectionCategory(), result.getSelectionName().strip().toLowerCase(Locale.ROOT));
            GradeComparisonRow current = deduplicated.get(key);
            if (current == null || strongerSource(row, current)) {
                deduplicated.put(key, row);
            }
        }

        BigDecimal low = grade.subtract(HALF_RANGE).max(MIN_GRADE);
        BigDecimal high = grade.add(HALF_RANGE).min(MAX_GRADE);
        List<GradeComparisonRow> rows = deduplicated.values().stream()
                .filter(row -> "all".equals(scope) ||
                        (row.referenceCut().compareTo(low) >= 0 && row.referenceCut().compareTo(high) <= 0))
                .sorted(Comparator.comparing(GradeComparisonRow::absoluteGap)
                        .thenComparing(GradeComparisonRow::referenceCut)
                        .thenComparing(GradeComparisonRow::universityName)
                        .thenComparing(GradeComparisonRow::recruitmentUnitName)
                        .thenComparing(GradeComparisonRow::selectionName))
                .toList();
        int totalCount = rows.size();

        Map<Long, List<GradeComparisonRow>> groups = rows.stream()
                .collect(Collectors.groupingBy(GradeComparisonRow::universityId,
                        LinkedHashMap::new, Collectors.toList()));
        List<GradeComparisonChart> charts = new ArrayList<>();
        for (List<GradeComparisonRow> group : groups.values()) {
            List<GradeComparisonRow> cuts70 = group.stream().filter(r -> r.cut70() != null).toList();
            List<GradeComparisonRow> comparable = cuts70.isEmpty() ? group : cuts70;
            if (comparable.size() < chartMin) continue;
            BigDecimal average = comparable.stream().map(GradeComparisonRow::referenceCut)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(comparable.size()), 2, RoundingMode.HALF_EVEN);
            BigDecimal gap = grade.subtract(average).setScale(2, RoundingMode.HALF_EVEN);
            GradeComparisonRow representative = group.get(0);
            charts.add(new GradeComparisonChart(representative.universityId(),
                    representative.universityName(), average, comparable.size(),
                    cuts70.isEmpty() ? "50% 컷" : "70% 컷",
                    positionLabel(gap), positionTone(gap), gap.abs()));
        }
        charts.sort(Comparator.comparing(GradeComparisonChart::absoluteGap)
                .thenComparing(GradeComparisonChart::averageCut)
                .thenComparing(GradeComparisonChart::universityName));
        int chartCount = charts.size();
        List<GradeComparisonChart> visibleCharts = charts.subList(0, Math.min(16, charts.size()));
        int start = (int) Math.min((long) page * size, (long) totalCount);
        int end = Math.min(totalCount, start + size);
        return new GradeComparisonResponse(grade, selectedYear, years, track, special,
                scope, chartMin, totalCount, chartCount, page, size,
                end < totalCount, rows.subList(start, end), visibleCharts);
    }

    private static boolean matchesRegion(University university, String region) {
        String actual = String.valueOf(university.getRegion()) + " " +
                String.valueOf(university.getAddress());
        return REGION_TOKENS.getOrDefault(region, List.of(region)).stream()
                .anyMatch(actual::contains);
    }

    private static boolean protectedMajor(AdmissionResult r) {
        String label = r.getUnit().getName() + " " + r.getUnit().getCollegeName();
        return PROTECTED_MAJOR_TERMS.stream().anyMatch(label::contains);
    }

    private static int sourcePriority(String source) {
        return switch (source) {
            case "UNIVERSITY" -> 2;
            case "ADIGA" -> 1;
            default -> 0;
        };
    }

    private static int countCuts(GradeComparisonRow row) {
        return (row.cut50() == null ? 0 : 1) + (row.cut70() == null ? 0 : 1);
    }

    private static boolean strongerSource(GradeComparisonRow next, GradeComparisonRow previous) {
        int bySource = Integer.compare(sourcePriority(next.sourceType()), sourcePriority(previous.sourceType()));
        return bySource > 0 || (bySource == 0 && countCuts(next) > countCuts(previous));
    }

    private static String positionTone(BigDecimal gap) {
        if (gap.abs().compareTo(NEAR) <= 0) return "near";
        return gap.signum() < 0 ? "ahead" : "behind";
    }

    private static String positionLabel(BigDecimal gap) {
        if (gap.abs().compareTo(NEAR) <= 0) return "컷과 비슷";
        return gap.abs().setScale(2).toPlainString() + "등급 " +
                (gap.signum() < 0 ? "앞섬" : "뒤");
    }

    private record Key(Long universityId, Long unitId, String category, String selection) {}

    private static final class RawResult {
        final AdmissionResult result;
        BigDecimal cut50;
        BigDecimal cut70;

        RawResult(AdmissionResult result) {
            this.result = result;
        }
    }
}

package com.kunirank.api.ranking.discovery;

import com.kunirank.api.admission.entity.AdmissionMetric;
import com.kunirank.api.admission.entity.AdmissionResult;
import com.kunirank.api.admission.policy.AdmissionResultPolicy;
import com.kunirank.api.admission.repository.AdmissionMetricRepository;
import com.kunirank.api.admission.repository.AdmissionResultRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class RankingDiscoveryService {
    public record Metric(String slug, String code, String label, String category, String kind,
                         boolean ascending, String description) {}
    public record Card(Metric metric, Integer year, int universityCount) {}
    public record Row(int rank, long universityId, String name, String logoPath, String region,
                      BigDecimal value, int sampleCount, String sourceUrl, int year) {
        Row ranked(int rank) { return new Row(rank, universityId, name, logoPath, region, value, sampleCount, sourceUrl, year); }
    }
    public record Page(Metric metric, Integer selectedYear, List<Integer> availableYears,
                       List<String> regions, int totalCount, int page, int size, boolean hasNext, List<Row> items) {}
    public static final List<Metric> INDICATORS = List.of(
        new Metric("employment-rate","EMPLOYMENT_RATE","취업률","취업","percent",false,"졸업생 취업 현황"),
        new Metric("average-tuition","AVG_TUITION","평균 등록금","비용","money",true,"연간 평균 등록금"),
        new Metric("scholarship-per-student","SCHOLARSHIP_PER_STUDENT","학생 1인당 장학금","장학","money",false,"재학생 1인당 장학금"),
        new Metric("dormitory-capacity-rate","DORMITORY_CAPACITY_RATE","기숙사 수용률","생활","percent",false,"재학생 대비 기숙사 수용 가능 비율"),
        new Metric("education-cost-per-student","EDUCATION_COST_PER_STUDENT","학생 1인당 교육비","교육","money",false,"학생 1인당 교육 투자 규모"),
        new Metric("students-per-faculty","STUDENTS_PER_FULLTIME_FACULTY","전임교원 1인당 학생 수","교육","person",true,"전임교원 1명이 담당하는 학생 수"),
        new Metric("faculty-securing-rate","FULLTIME_FACULTY_SECURING_RATE","전임교원 확보율","교육","percent",false,"법정 정원 대비 전임교원 확보 수준")
    );
    private final JdbcTemplate jdbc;
    private final AdmissionResultRepository results;
    private final AdmissionMetricRepository metrics;

    public RankingDiscoveryService(JdbcTemplate jdbc, AdmissionResultRepository results, AdmissionMetricRepository metrics) {
        this.jdbc=jdbc; this.results=results; this.metrics=metrics;
    }
    public List<Card> catalog() {
        return INDICATORS.stream().map(spec -> {
            List<Integer> years=indicatorYears(spec.code());
            Integer year=years.isEmpty()?null:years.get(0);
            int count=year==null?0:jdbc.queryForObject("""
                SELECT count(DISTINCT i.university_id) FROM university_indicators i
                JOIN universities u ON u.university_id=i.university_id
                WHERE i.indicator_code=? AND i.source='ACADEMYINFO' AND i.value>0 AND u.is_active AND i.year=?
                """, Integer.class,spec.code(),year);
            return new Card(spec,year,count);
        }).toList();
    }
    private List<Integer> indicatorYears(String code) {
        return jdbc.queryForList("""
            SELECT DISTINCT i.year FROM university_indicators i JOIN universities u ON u.university_id=i.university_id
            WHERE i.indicator_code=? AND i.source='ACADEMYINFO' AND i.value>0 AND u.is_active ORDER BY i.year DESC
            """,Integer.class,code);
    }
    private Integer selected(Integer requested,List<Integer> years) {
        return requested!=null && years.contains(requested)?requested:years.isEmpty()?null:years.get(0);
    }
    public Page indicator(String slug,Integer year,String query,String region,int page,int size) {
        Metric spec=INDICATORS.stream().filter(m->m.slug().equals(slug)).findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"지원하지 않는 지표"));
        List<Integer> years=indicatorYears(spec.code());
        Integer chosen=selected(year,years);
        List<Row> rows=chosen==null?List.of():jdbc.query("""
            SELECT u.university_id,u.name,u.logo_path,u.region,i.value,i.source_url,i.year
            FROM university_indicators i JOIN universities u ON u.university_id=i.university_id
            WHERE i.indicator_code=? AND i.source='ACADEMYINFO' AND i.value>0 AND u.is_active AND i.year=?
            """,(rs,n)->new Row(0,rs.getLong("university_id"),rs.getString("name"),rs.getString("logo_path"),
                rs.getString("region"),rs.getBigDecimal("value"),0,rs.getString("source_url"),rs.getInt("year")),
            spec.code(),chosen);
        return paginate(spec,chosen,years,rows,query,region,page,size);
    }
    public Page admissions(String rawKind,String rawPhase,Integer year,String query,String region,int page,int size) {
        boolean college="college".equals(rawKind), susi=!"JEONGSI".equals(rawPhase);
        String phase=susi?"SUSI":"JEONGSI";
        String code=college?(susi?"COLLEGE_STUDENT_AVERAGE":"COLLEGE_CSAT_AVERAGE"):(susi?"STUDENT_GRADE_70_CUT":"CSAT_PERCENTILE_MEAN_70_CUT");
        String unit=susi?"등급":"백분위";
        Metric spec=new Metric("admissions",code,college?(susi?"학생부 합격자 평균":"수능 합격자 평균"):(susi?"학생부교과 70% 컷":"공식 평균 백분위 70% 컷"),
            college?"전문대":"4년제",unit,susi,college?"모집인원이 있는 표본의 가중평균, 없는 경우 단순평균":"기존 대학 단위 입결 집계값");
        List<Integer> years=jdbc.queryForList("SELECT DISTINCT admission_year FROM admission_results ORDER BY admission_year DESC",Integer.class);
        Integer chosen=selected(year,years);
        List<Row> rows=new ArrayList<>();
        if(chosen!=null && !college) {
            rows=jdbc.query("""
                SELECT u.university_id,u.name,u.logo_path,u.region,a.value,a.sample_count,a.admission_year
                FROM admission_aggregates a JOIN universities u ON u.university_id=a.university_id
                WHERE a.admission_year=? AND a.admission_phase=? AND a.selection_category=? AND a.metric_code=?
                """,(rs,n)->new Row(0,rs.getLong("university_id"),rs.getString("name"),rs.getString("logo_path"),
                    rs.getString("region"),rs.getBigDecimal("value"),rs.getInt("sample_count"),"",rs.getInt("admission_year")),
                chosen,phase,susi?"학생부교과":"수능",code);
        } else if(chosen!=null) {
            // Reuse the existing invalid-double-one and active/source filters before aggregation.
            List<AdmissionResult> eligible=results.findAll(AdmissionResultPolicy.visible()
                .and(AdmissionResultPolicy.inYear(chosen)).and(AdmissionResultPolicy.filtered("college",phase,null)));
            Map<Long,AdmissionResult> byId=new HashMap<>();
            eligible.forEach(r->byId.put(r.getId(),r));
            Map<Long,List<AdmissionMetric>> groups=new HashMap<>();
            if(!byId.isEmpty()) for(var metric:metrics.findByResultIdInOrderByResultIdAscMetricCodeAsc(byId.keySet())) {
                boolean validUnit=unit.equals(metric.getUnit()) || (!susi && (metric.getUnit()==null||metric.getUnit().isEmpty()) && metric.getValue().compareTo(BigDecimal.valueOf(9))>0 && metric.getValue().compareTo(BigDecimal.valueOf(100))<=0);
                if(code.equals(metric.getMetricCode()) && validUnit) groups.computeIfAbsent(metric.getResult().getUniversity().getId(),ignored->new ArrayList<>()).add(metric);
            }
            for(var group:groups.values()) {
                var weighted=group.stream().filter(m->m.getResult().getRecruitmentCount()!=null && m.getResult().getRecruitmentCount()>0).toList();
                var used=weighted.isEmpty()?group:weighted;
                BigDecimal numerator=BigDecimal.ZERO,denominator=BigDecimal.ZERO;
                for(var metric:used) {
                    BigDecimal weight=weighted.isEmpty()?BigDecimal.ONE:BigDecimal.valueOf(metric.getResult().getRecruitmentCount());
                    numerator=numerator.add(metric.getValue().multiply(weight)); denominator=denominator.add(weight);
                }
                var u=used.get(0).getResult().getUniversity();
                rows.add(new Row(0,u.getId(),u.getName(),u.getLogoPath(),u.getRegion(),numerator.divide(denominator,MathContext.DECIMAL128),used.size(),"",chosen));
            }
        }
        return paginate(spec,chosen,years,rows,query,region,page,size);
    }
    private Page paginate(Metric metric,Integer year,List<Integer> years,List<Row> source,String query,String region,int page,int size) {
        Comparator<Row> order=Comparator.comparing(Row::value);
        if(!metric.ascending()) order=order.reversed();
        List<Row> sorted=source.stream().sorted(order.thenComparing(Row::name).thenComparingLong(Row::universityId)).toList();
        List<String> regions=sorted.stream().map(Row::region).filter(Objects::nonNull).filter(r->!r.isBlank()).distinct().sorted().toList();
        String q=query==null?"":query.strip().toLowerCase(Locale.ROOT);
        List<Row> filtered=new ArrayList<>();
        for(int i=0;i<sorted.size();i++) {
            Row row=sorted.get(i).ranked(i+1);
            if((q.isBlank()||row.name().toLowerCase(Locale.ROOT).contains(q)) && (region==null||region.isBlank()||region.equals(row.region()))) filtered.add(row);
        }
        int start=(int)Math.min((long)Math.max(0,page)*size,filtered.size()),end=Math.min(start+size,filtered.size());
        return new Page(metric,year,years,regions,filtered.size(),page,size,end<filtered.size(),filtered.subList(start,end));
    }
}

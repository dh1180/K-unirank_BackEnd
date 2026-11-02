package com.kunirank.api.admission.service;

import com.kunirank.api.admission.policy.AdmissionMetricQuality;
import com.kunirank.api.admission.policy.AdmissionResultPolicy;
import com.kunirank.api.admission.policy.UniversityMetricQuality;
import java.math.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repairs contaminated aggregates in memory without modifying collected source data. */
@Service @Transactional(readOnly=true)
public class CleanAdmissionAggregates {
    public record Key(long universityId,String phase,String category,String code) {}
    public record Aggregate(Key key,BigDecimal value,int sampleCount) {}
    private record Stored(Key key,String method,BigDecimal value,int count,String university) {}
    private record Sample(BigDecimal value,Integer weight,String unit) {}
    private final JdbcTemplate jdbc;
    public CleanAdmissionAggregates(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public List<Aggregate> load(int year,Long universityId,String phase,String category,String code) {
        List<Object> args=new ArrayList<>(List.of(year));
        String where=".admission_year=?";
        // The two bounded queries share filters; metric_code belongs to metrics in the raw query.
        String aggregateWhere="a"+where,rawWhere="r"+where;
        if(universityId!=null) {aggregateWhere+=" AND a.university_id=?";rawWhere+=" AND r.university_id=?";args.add(universityId);}
        if(phase!=null) {aggregateWhere+=" AND a.admission_phase=?";rawWhere+=" AND r.admission_phase=?";args.add(phase);}
        if(category!=null) {aggregateWhere+=" AND a.selection_category=?";rawWhere+=" AND r.selection_category=?";args.add(category);}
        if(code!=null) {aggregateWhere+=" AND a.metric_code=?";rawWhere+=" AND m.metric_code=?";args.add(code);}
        var stored=jdbc.query("SELECT a.university_id,a.admission_phase,a.selection_category,a.metric_code,a.aggregation_method,a.value,a.sample_count,u.name FROM admission_aggregates a JOIN universities u ON u.university_id=a.university_id WHERE "+aggregateWhere+
            " ORDER BY CASE WHEN a.aggregation_method='WEIGHTED_BY_RECRUITMENT' THEN 0 ELSE 1 END,a.aggregate_id",
            (rs,n)->new Stored(new Key(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)),rs.getString(5),rs.getBigDecimal(6),rs.getInt(7),rs.getString(8)),args.toArray());
        if(stored.isEmpty()) return List.of();
        Set<Key> keys=new HashSet<>();stored.forEach(s->keys.add(s.key()));
        Map<Key,List<Sample>> groups=new HashMap<>();
        List<Object> rawArgs=new ArrayList<>(args);
        var protectedChecks=new ArrayList<String>();
        for(String marker:AdmissionResultPolicy.protectedMajorMarkers()) {protectedChecks.add("(ru.name LIKE ? OR ru.college_name LIKE ?)");rawArgs.add("%"+marker+"%");rawArgs.add("%"+marker+"%");}
        String visible=" AND (("+String.join(" OR ",protectedChecks)+") OR NOT (EXISTS (SELECT 1 FROM admission_metrics q50 WHERE q50.result_id=r.result_id AND q50.metric_code='STUDENT_GRADE_50_CUT' AND q50.value=1) AND EXISTS (SELECT 1 FROM admission_metrics q70 WHERE q70.result_id=r.result_id AND q70.metric_code='STUDENT_GRADE_70_CUT' AND q70.value=1)))";
        jdbc.query("SELECT r.university_id,r.admission_phase,r.selection_category,m.metric_code,m.value,r.recruitment_count,m.unit FROM admission_results r JOIN recruitment_units ru ON ru.recruitment_unit_id=r.recruitment_unit_id JOIN admission_metrics m ON m.result_id=r.result_id WHERE "+rawWhere+visible,
            rs->{var key=new Key(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4));
                if(keys.contains(key)) groups.computeIfAbsent(key,k->new ArrayList<>()).add(new Sample(rs.getBigDecimal(5),(Integer)rs.getObject(6),rs.getString(7)));},rawArgs.toArray());
        Map<Key,Aggregate> clean=new LinkedHashMap<>();
        for(var row:stored) {
            if(clean.containsKey(row.key())) continue;
            var samples=groups.getOrDefault(row.key(),List.of());
            String metric=row.key().code();
            String unit=metric.contains("PERCENTILE")?"백분위":metric.contains("GRADE")?"등급":"점";
            boolean contaminated=samples.stream().anyMatch(s->!UniversityMetricQuality.include(row.university(),metric,s.unit(),s.value()));
            BigDecimal value=row.value();int count=row.count();
            if(contaminated || !UniversityMetricQuality.include(row.university(),metric,unit,value)) {
                var valid=samples.stream().filter(s->UniversityMetricQuality.include(row.university(),metric,s.unit(),s.value())).toList();
                var weighted=valid.stream().filter(s->s.weight()!=null && s.weight()>0).toList();
                boolean useWeights="WEIGHTED_BY_RECRUITMENT".equals(row.method()) && !weighted.isEmpty();
                var used=useWeights?weighted:valid;
                if(used.isEmpty()) continue;
                BigDecimal sum=BigDecimal.ZERO,weights=BigDecimal.ZERO;
                for(var sample:used) {BigDecimal w=useWeights?BigDecimal.valueOf(sample.weight()):BigDecimal.ONE;sum=sum.add(sample.value().multiply(w));weights=weights.add(w);}
                value=sum.divide(weights,MathContext.DECIMAL128);count=used.size();
            }
            if(UniversityMetricQuality.include(row.university(),metric,unit,value)) clean.put(row.key(),new Aggregate(row.key(),value,count));
        }
        return List.copyOf(clean.values());
    }
}

package com.kunirank.api.university.service;

import com.kunirank.api.admission.policy.AdmissionResultPolicy;
import com.kunirank.api.admission.repository.AdmissionResultRepository;
import com.kunirank.api.admission.service.CleanAdmissionAggregates;
import com.kunirank.api.university.repository.UniversityRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Same latest-year aggregate preference as the original VS admission_hover. */
@Service
@Transactional(readOnly=true)
public class AdmissionReferenceService {
    public record Cut(String label,String unit,BigDecimal value50,BigDecimal value70) {}
    public record Reference(Integer year,List<Cut> cuts) {}
    private record Aggregate(String phase,String category,String code,BigDecimal value) {}
    private final UniversityRepository universities;
    private final AdmissionResultRepository results;
    private final CleanAdmissionAggregates aggregates;
    public AdmissionReferenceService(UniversityRepository universities,AdmissionResultRepository results,CleanAdmissionAggregates aggregates) {
        this.universities=universities;this.results=results;this.aggregates=aggregates;
    }
    @Cacheable(cacheNames = "admission-reference", sync = true)
    public Reference reference(long id) {
        universities.findById(id).filter(u->u.isActive()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var latest=results.latestYear(AdmissionResultPolicy.visible()
                .and((root,q,cb)->cb.equal(root.get("university").get("id"),id)));
        if(latest.isEmpty()) return new Reference(null,List.of());
        int year=latest.get();
        var rows=aggregates.load(year,id,null,null,null).stream().map(a->new Aggregate(a.key().phase(),a.key().category(),a.key().code(),a.value())).toList();
        List<Cut> cuts=new ArrayList<>();
        add(cuts,rows,"SUSI","학생부교과","STUDENT_GRADE","교과 등급","등급","CONVERTED_SCORE","교과 환산","점");
        add(cuts,rows,"JEONGSI","수능","CSAT_PERCENTILE_MEAN","수능 평백","백분위","CSAT_CONVERTED_SCORE","수능 환산","점");
        return new Reference(year,List.copyOf(cuts));
    }
    private void add(List<Cut> cuts,List<Aggregate> rows,String phase,String category,String preferred,String label,String unit,String fallback,String fallbackLabel,String fallbackUnit) {
        Map<String,BigDecimal> values=new HashMap<>();
        rows.stream().filter(r->phase.equals(r.phase()) && category.equals(r.category())).forEach(r->values.putIfAbsent(r.code(),r.value()));
        String prefix=preferred;
        if(!values.containsKey(prefix+"_50_CUT") && !values.containsKey(prefix+"_70_CUT")) {prefix=fallback;label=fallbackLabel;unit=fallbackUnit;}
        BigDecimal a=values.get(prefix+"_50_CUT"),b=values.get(prefix+"_70_CUT");
        if(a!=null || b!=null) cuts.add(new Cut(label,unit,a,b));
    }
}

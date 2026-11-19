package com.kunirank.api.ai.service;

import com.kunirank.api.ai.dto.AiQuestion;
import com.kunirank.api.admission.dto.*;
import com.kunirank.api.admission.repository.AdmissionResultRepository;
import com.kunirank.api.university.repository.UniversityRepository;
import com.kunirank.api.university.service.UniversityOverviewService;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class AiEvidenceService {
    private final AdmissionResultRepository results;
    private final UniversityRepository universities;
    private final UniversityOverviewService overviews;
    public AiEvidenceService(AdmissionResultRepository results, UniversityRepository universities, UniversityOverviewService overviews) {
        this.results=results; this.universities=universities; this.overviews=overviews;
    }
    public List<AiQuestion.Evidence> explain(List<GradeComparisonRow> rows, BigDecimal grade, Integer year) {
        if(rows.isEmpty()) return List.of();
        var ids=rows.stream().map(GradeComparisonRow::resultId).distinct().toList();
        var byId=new HashMap<Long,com.kunirank.api.admission.entity.AdmissionResult>();
        results.findAllById(ids).forEach(r->byId.put(r.getId(),r));
        return rows.stream().map(row->{
            var result=byId.get(row.resultId());
            String explanation=year+"학년도 "+row.selectionCategory()+" · "+row.selectionName()+"의 공개 "+row.referenceBasis()+"을 기준으로 조회했습니다.";
            if(grade!=null) explanation+=" 내 등급과의 숫자 차이는 "+grade.subtract(row.referenceCut()).abs().setScale(2,java.math.RoundingMode.HALF_EVEN)+"등급입니다. 합격 확률을 계산한 값은 아닙니다.";
            return new AiQuestion.Evidence(row.resultId(),result==null?null:result.getSource().getSourceUrl(),
                result==null?null:result.getRecruitmentCount(),result==null?null:result.getCompetitionRate(),explanation);
        }).toList();
    }
    public UniversityOverviewService.Overview overview(String name) {
        return universities.findAll((root,query,cb)->cb.and(cb.isTrue(root.get("active")),cb.equal(root.get("name"),name)))
                .stream().findFirst().map(u->overviews.overview(u.getId())).orElse(null);
    }
}

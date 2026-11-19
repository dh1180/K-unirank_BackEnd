package com.kunirank.api.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunirank.api.ai.dto.AiQuestion;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Carries only bounded search conditions, never a transcript or model instructions. */
@Component
public class AiConversationPolicy {
    private final ExplicitQuestionPolicy policy;
    private final ObjectMapper json;
    public AiConversationPolicy(ExplicitQuestionPolicy policy, ObjectMapper json) { this.policy=policy; this.json=json; }
    public boolean documents(String q) {
        return q.matches("(?s).*(모집요강|최저|수능.?최저|최저학력|면접|지원.?자격|전형.?방법|제출.?서류|원서.?접수|전형.?일정|반영.?방법).*" );
    }
    public AiQuestion.Filters resolve(AiQuestion.Request request) {
        String q=request.question();
        var context=q.matches("(?s).*(처음부터|새로 검색|조건 초기화).*" ) ? null : request.context();
        // Guide queries go to the curated document index, never to grade/eligibility inference.
        String parse=documents(q) ? q.replaceAll("수능.?최저|최저학력|면접|지원.?자격|전형.?방법|제출.?서류|원서.?접수|전형.?일정|반영.?방법", "") : q;
        var explicit=policy.read(parse, false);
        BigDecimal grade=explicit.grade()!=null ? explicit.grade() : context==null ? null : context.grade();
        if(q.matches("(?s).*(등급 없이|내신 없이|단순 조회).*")) grade=null;
        Integer year=explicit.year()!=null ? explicit.year() : context==null ? null : context.year();
        if(q.matches("(?s).*최신.*")) year=null;
        String track=q.matches("(?s).*(교과|종합|학종).*" ) || context==null ? explicit.track() : context.track();
        String special=q.matches("(?s).*(특별전형|농어촌|지역인재|기회균|특성화고).*" ) || context==null ? explicit.special() : context.special();
        String scope=q.matches("(?s).*(전체.*등급|등급.*전체).*" ) || context==null ? explicit.scope() : context.scope();
        String region=explicit.region()!=null ? explicit.region() : context==null ? null : context.region();
        String major=explicit.major()!=null ? explicit.major() : context==null ? null : context.major();
        String university=explicit.university()!=null ? explicit.university() : context==null ? null : context.university();
        if(q.matches("(?s).*(전국|전체 지역).*")) region=null;
        if(q.matches("(?s).*(전체 학과|모든 학과).*")) major=null;
        if(q.matches("(?s).*(전체 대학|모든 대학).*")) university=null;
        if(grade==null) scope="all";
        var filters=new AiQuestion.Filters(grade,year,track==null?"student":track,special==null?"exclude":special,
                scope==null?"near":scope,region,major,university,explicit.notes(),request.limit()==null?5:request.limit());
        // Reuse the parser boundary for every field, including client-supplied context.
        var validated=json.valueToTree(filters);
        ((com.fasterxml.jackson.databind.node.ObjectNode)validated).put("grade",grade==null?BigDecimal.ONE:grade);
        try { AiQuestionService.parseFilters(validated); }
        catch(ResponseStatusException ex) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"이전 검색 조건을 확인하거나 새 질문을 시작해 주세요."); }
        if(grade==null && major==null && university==null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"조회할 대학이나 학과를 알려주세요. 예: 항공대 컴공 교과 입결 알려줘");
        return filters;
    }
    public static AiQuestion.Context context(AiQuestion.Filters f) {
        return new AiQuestion.Context(f.grade(),f.year(),f.track(),f.special(),f.scope(),f.region(),f.major(),f.university());
    }
}

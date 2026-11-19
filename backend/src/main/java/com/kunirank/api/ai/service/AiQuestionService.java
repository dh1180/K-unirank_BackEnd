package com.kunirank.api.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunirank.api.admission.service.GradeComparisonService;
import com.kunirank.api.ai.dto.AiQuestion;
import java.math.BigDecimal;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spring parses questions through OpenRouter and composes answers from PostgreSQL evidence.
 */
@Service
public class AiQuestionService {
    private final GradeComparisonService comparisons;
    private final OpenRouterIntentParser parser;
    private final AiConversationPolicy conversation;
    private final AiEvidenceService evidence;
    private final OfficialGuideSearch guides;
    private final Semaphore permits = new Semaphore(3);

    public AiQuestionService(GradeComparisonService comparisons, OpenRouterIntentParser parser, AiConversationPolicy conversation, AiEvidenceService evidence, OfficialGuideSearch guides) {
        this.comparisons = comparisons;
        this.parser = parser;
        this.conversation=conversation; this.evidence=evidence; this.guides=guides;
    }

    public AiQuestion.Response ask(AiQuestion.Request request) {
        if (!permits.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 요청이 많습니다.");
        }
        try {
            AiQuestion.Filters filters=conversation.resolve(request);
            boolean document=conversation.documents(request.question());
            // Known aliases and follow-ups are deterministic. The model only assists an unfamiliar first-turn major.
            if (!document && request.context()==null && filters.grade()!=null && filters.major()==null && filters.university()==null)
                filters=parser.extract(request.question(),filters.limit());
            if(document) return guides.answer(filters,request.question());
            var admissions=lookup(filters,filters.university());
            boolean comparing=filters.university()!=null && filters.university().contains(",")
                    && request.question().matches("(?is).*(비교|차이|vs|어디가).*" );
            var schools=new ArrayList<AiQuestion.School>();
            var explanations=new ArrayList<AiQuestion.Evidence>();
            if(comparing) {
                String[] names=filters.university().split(",");
                if(names.length>4) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"대학 비교는 한 번에 4개까지 가능합니다. 비교할 대학을 지정해 주세요.");
                // Keep a single admission year across schools; no silent per-school year substitutions.
                var aligned=new AiQuestion.Filters(filters.grade(),admissions.selectedYear(),filters.track(),filters.special(),
                        filters.scope(),filters.region(),filters.major(),filters.university(),filters.notes(),filters.limit());
                for(String name:names) {
                    var schoolAdmissions=lookup(aligned,name);
                    schools.add(new AiQuestion.School(name,schoolAdmissions,evidence.overview(name)));
                    explanations.addAll(evidence.explain(schoolAdmissions.items(),filters.grade(),schoolAdmissions.selectedYear()));
                }
            } else explanations.addAll(evidence.explain(admissions.items(),filters.grade(),admissions.selectedYear()));
            var resolved=new AiQuestion.Filters(filters.grade(),admissions.selectedYear(),filters.track(),filters.special(),filters.scope(),
                    filters.region(),filters.major(),filters.university(),filters.notes(),filters.limit());
            String summary=comparing ? "같은 학년도와 검색 조건의 공개 자료를 대학별로 비교합니다. 전형별 반영 방법이 달라 단순 우열이나 합격 가능성을 뜻하지 않습니다."
                    : filters.grade()==null ? "내신 등급 없이 공개 입결을 조회했습니다. 50%·70% 컷을 함께 확인하세요."
                    : "입력한 조건과 공개 컷의 등급 차이로 정렬했습니다. 각 결과에서 선정 근거와 공식 출처를 확인할 수 있습니다.";
            return new AiQuestion.Response(filters,admissions,
                    "입결 숫자·모집단위는 Spring Boot 조회 결과만 사용하며 합격을 보장하지 않습니다.",
                    comparing?"schools":filters.grade()==null?"lookup":"comparison",summary,AiConversationPolicy.context(resolved),
                    List.copyOf(explanations),List.copyOf(schools),List.of());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 서비스 연결에 실패했습니다.");
        } finally {
            permits.release();
        }
    }

    private com.kunirank.api.admission.dto.GradeComparisonResponse lookup(AiQuestion.Filters filters,String university) {
        var result=comparisons.compare(filters.grade(),filters.year(),filters.track(),filters.special(),filters.scope(),2,
                filters.major(),filters.region(),0,filters.limit(),university);
        if(filters.year()!=null && !filters.year().equals(result.selectedYear()))
            return new com.kunirank.api.admission.dto.GradeComparisonResponse(filters.grade(),filters.year(),result.availableYears(),
                    filters.track(),filters.special(),filters.scope(),2,0,0,0,filters.limit(),false,List.of(),List.of());
        return result;
    }

    public static String clarificationDetail(JsonNode root) {
        JsonNode detail = root.path("detail");
        return detail.isTextual() && !detail.asText().isBlank() && detail.asText().length() <= 200
                ? detail.asText() : "내신 평균등급을 포함하여 질문해 주세요.";
    }

    // Validate resolved constraints before passing them to database queries.
    public static AiQuestion.Filters parseFilters(JsonNode root) {
        try {
            BigDecimal grade = new BigDecimal(root.path("grade").asText());
            if (grade.compareTo(BigDecimal.ONE) < 0 || grade.compareTo(new BigDecimal("9")) > 0
                    || grade.scale() > 2) throw new IllegalArgumentException();
            Integer year = root.path("year").isNull() ? null : root.path("year").asInt();
            if (year != null && (year < 2000 || year > 2100)) throw new IllegalArgumentException();
            String track = root.path("track").asText();
            String special = root.path("special").asText();
            String scope = root.path("scope").asText();
            if (!Set.of("student", "holistic").contains(track)
                    || !Set.of("exclude", "all").contains(special)
                    || !Set.of("near", "all").contains(scope)) throw new IllegalArgumentException();
            String region = root.path("region").isNull() ? null : root.path("region").asText();
            String major = root.path("major").isNull() ? null : root.path("major").asText();
            String university = root.path("university").isMissingNode() || root.path("university").isNull()
                    ? null : root.path("university").asText();
            Set<String> validRegions = Set.of("서울", "경기", "인천", "부산", "대구", "광주", "대전", "울산", "세종", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");
            if (region != null && !region.isBlank() && !validRegions.containsAll(Arrays.asList(region.split(",", -1))))
                throw new IllegalArgumentException();
            if (major != null && major.split(",", -1).length > 5
                    || university != null && (university.length() > 160 || university.split(",", -1).length > 10))
                throw new IllegalArgumentException();
            List<String> notes = new ArrayList<>();
            if (root.has("notes")) {
                if (!root.path("notes").isArray() || root.path("notes").size() > 5) throw new IllegalArgumentException();
                for (JsonNode note : root.path("notes")) {
                    if (!note.isTextual() || note.asText().length() > 180) throw new IllegalArgumentException();
                    notes.add(note.asText());
                }
            }
            if (region != null && region.length() > 60 || major != null && major.length() > 100)
                throw new IllegalArgumentException();
            int limit = root.path("limit").asInt();
            if (limit < 1 || limit > 10) throw new IllegalArgumentException();
            return new AiQuestion.Filters(grade, year, track, special, scope, region, major, university, List.copyOf(notes), limit);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI가 유효하지 않은 검색 조건을 반환했습니다.");
        }
    }
}

package com.kunirank.api.ai.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kunirank.api.ai.dto.AiQuestion;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Curated offline PDF evidence. No user URLs, generated eligibility claims or extra server. */
@Service
public class OfficialGuideSearch {
    record Page(int page,String text) {}
    record Document(String university,int year,String title,String sourceUrl,String sha256,int pageCount,List<Page> pages) {}
    record Hit(Page page,int score) {}
    private final List<Document> documents;
    public OfficialGuideSearch(ObjectMapper json) {
        try(var input=getClass().getResourceAsStream("/ai/official-guides.json")) {
            documents=List.copyOf(json.readValue(Objects.requireNonNull(input),new TypeReference<List<Document>>(){}));
        } catch(java.io.IOException ex) {throw new IllegalStateException("공식 모집요강 색인을 읽을 수 없습니다.",ex);}
    }
    public AiQuestion.Response answer(AiQuestion.Filters f,String question) {
        if(f.university()==null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"모집요강을 확인할 대학과 학년도를 알려주세요. 예: 항공대 2027 수능최저 알려줘");
        var names=List.of(f.university().split(","));
        if(names.size()>4) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"모집요강은 대학 4개까지 한 번에 확인할 수 있습니다.");
        Integer year=f.year()!=null?f.year():documents.stream().filter(d->names.contains(d.university())).map(Document::year).max(Integer::compareTo).orElse(null);
        var selected=documents.stream().filter(d->names.contains(d.university())&&Objects.equals(d.year(),year)).toList();
        List<String> topics=new ArrayList<>();
        for(String topic:List.of("최저","면접","지원자격","제출서류","원서접수","반영방법","전형방법"))
            if(question.replace(" ","").contains(topic)) topics.add(topic);
        if(question.contains("일정")) topics.add("원서접수");
        if(topics.isEmpty()) topics.add("전형");
        var selections=List.of("학교장추천","교과성적우수자","미래인재","고른기회","학생부우수자","지역균형","가천바람개비","가천의약학","농어촌")
                .stream().filter(question.replace(" ","")::contains).toList();
        List<AiQuestion.Passage> passages=new ArrayList<>();
        for(Document doc:selected) {
            var hits=doc.pages().stream().map(page->{
                String text=page.text().replaceAll("\\s", "");
                int score=topics.stream().mapToInt(t->occurrences(text,t)*4).sum();
                if(!selections.isEmpty() && selections.stream().noneMatch(text::contains)) score=0;
                if(score>0) {
                    if(text.contains(f.track().equals("holistic")?"학생부종합":"학생부교과")) score+=5;
                    if(f.major()!=null) for(String term:f.major().split(",")) if(text.contains(term)) score+=3;
                    if(text.contains("목차")) score=0;
                }
                return new Hit(page,score);
            }).filter(h->h.score()>0).sorted(Comparator.comparingInt(Hit::score).reversed().thenComparingInt(h->h.page().page())).limit(3).toList();
            for(Hit hit:hits) {
                String text=hit.page().text(); int start=0;
                for(String topic:topics) {int found=text.indexOf(topic);if(found>=0){start=Math.max(0,found-180);break;}}
                int end=Math.min(text.length(),start+1000);
                passages.add(new AiQuestion.Passage(doc.university(),doc.year(),doc.title(),hit.page().page(),
                        (start>0?"…":"")+text.substring(start,end)+(end<text.length()?"…":""),doc.sourceUrl()+"#page="+hit.page().page()));
            }
        }
        var absent=names.stream().filter(n->selected.stream().noneMatch(d->d.university().equals(n))).toList();
        String summary=passages.isEmpty()?"해당 대학·학년도·질문의 공식 문서 근거가 색인에 없습니다. 지원 자격이나 최저 충족 여부를 추정하지 않습니다.":
                "공식 모집요강에서 관련 페이지를 찾았습니다. 아래 내용은 원문 발췌입니다. 전형별 예외와 표는 연결된 PDF에서 확인하세요. 페이지 번호는 PDF 파일 기준입니다.";
        if(!absent.isEmpty()) summary+=" 문서 미등록: "+String.join("·",absent)+".";
        var resolved=new AiQuestion.Filters(f.grade(),year,f.track(),f.special(),f.scope(),f.region(),f.major(),f.university(),f.notes(),f.limit());
        return new AiQuestion.Response(resolved,null,"공식 문서의 발췌만 제공하며 지원 자격을 판정하지 않습니다.","documents",summary,
                AiConversationPolicy.context(resolved),List.of(),List.of(),List.copyOf(passages));
    }
    private static int occurrences(String text,String term) {
        int count=0,at=0; while((at=text.indexOf(term,at))>=0){count++;at+=term.length();} return Math.min(count,8);
    }
}

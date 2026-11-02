package com.kunirank.api.ranking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalResultService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public PersonalResultService(JdbcTemplate jdbc,ObjectMapper json) {this.jdbc=jdbc;this.json=json;}
    public Map<String,Object> read(UUID id) {
        var rows=jdbc.queryForList("SELECT result_id,vote_count,result_json::text AS payload,created_at FROM personal_results WHERE result_id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"개인 결과를 찾을 수 없습니다.");
        var row=rows.get(0);
        try {return Map.of("id",id,"voteCount",row.get("vote_count"),"payload",json.readTree((String)row.get("payload")),"created",row.get("created_at"));}
        catch(java.io.IOException ex){throw new IllegalStateException("결과 형식 오류");}
    }
    private static class Score {
        double value=1500; int wins=0,losses=0; long id; String name;
        Score(long id,String name){this.id=id;this.name=name;}
    }
    @Transactional
    public Map<String,Object> create(String cookie) {
        if(cookie==null || !cookie.matches("[A-Za-z0-9_-]{20,64}"))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"투표를 먼저 진행해 주세요.");
        var sessionRows=jdbc.queryForList("SELECT session_id FROM vote_sessions WHERE session_key=? ORDER BY last_seen_at DESC LIMIT 1 FOR UPDATE",cookie);
        if(sessionRows.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"투표 세션을 찾을 수 없습니다.");
        UUID session=(UUID)sessionRows.get(0).get("session_id");
        var boardRows=jdbc.queryForList("SELECT board_id,name FROM ranking_boards WHERE slug='overall' AND is_active=true");
        if(boardRows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        long board=((Number)boardRows.get(0).get("board_id")).longValue();
        var previous=jdbc.queryForList("SELECT result_id FROM personal_results WHERE session_id=? AND board_id=? AND created_at> CURRENT_TIMESTAMP-INTERVAL '30 seconds' ORDER BY created_at DESC LIMIT 1",session,board);
        if(!previous.isEmpty())return read((UUID)previous.get(0).get("result_id"));
        var votes=jdbc.queryForList("""
          SELECT v.university_a_id AS a,v.university_b_id AS b,v.selected_university_id AS selected,a.name AS name_a,b.name AS name_b
          FROM comparison_votes v JOIN universities a ON a.university_id=v.university_a_id JOIN universities b ON b.university_id=v.university_b_id
          WHERE v.session_id=? AND v.board_id=? AND NOT v.skipped ORDER BY v.created_at,v.vote_id LIMIT 100001
          """,session,board);
        if(votes.size()<5)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"개인 결과를 만들려면 5번 이상 선택해 주세요.");
        if(votes.size()>100000)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"투표 기록이 많아 결과를 바로 만들 수 없습니다. 운영자에게 문의해 주세요.");
        Map<Long,Score> scores=new LinkedHashMap<>();
        for(var vote:votes){
            long aid=((Number)vote.get("a")).longValue(),bid=((Number)vote.get("b")).longValue(),selected=((Number)vote.get("selected")).longValue();
            Score a=scores.computeIfAbsent(aid,id->new Score(id,(String)vote.get("name_a")));
            Score b=scores.computeIfAbsent(bid,id->new Score(id,(String)vote.get("name_b")));
            double expected=1/(1+Math.pow(10,(b.value-a.value)/400)),delta=32*((selected==aid?1:0)-expected);
            a.value+=delta;b.value-=delta;
            if(selected==aid){a.wins++;b.losses++;}else{b.wins++;a.losses++;}
        }
        List<Map<String,Object>> top=new ArrayList<>();
        scores.values().stream().sorted(Comparator.comparingDouble((Score s)->s.value).reversed()).limit(10).forEach(s->
            top.add(Map.of("rank",top.size()+1,"university_id",s.id,"name",s.name,"score",Math.rint(s.value*10)/10,"wins",s.wins,"losses",s.losses)));
        Map<String,Object> payload=Map.of("board",Map.of("slug","overall","name",boardRows.get(0).get("name")),"total_votes",votes.size(),"top10",top);
        UUID id=UUID.randomUUID();
        try{jdbc.update("INSERT INTO personal_results(result_id,session_id,board_id,vote_count,result_json,created_at) VALUES(?,?,?,?,?::jsonb,CURRENT_TIMESTAMP)",id,session,board,votes.size(),json.writeValueAsString(payload));}
        catch(java.io.IOException ex){throw new IllegalStateException(ex);}
        return read(id);
    }
}

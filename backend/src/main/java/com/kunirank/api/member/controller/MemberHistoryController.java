package com.kunirank.api.member.controller;

import com.kunirank.api.member.security.MemberSessionService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/member")
public class MemberHistoryController {
    private final JdbcTemplate jdbc;
    private final MemberSessionService sessions;
    public MemberHistoryController(JdbcTemplate jdbc,MemberSessionService sessions) {this.jdbc=jdbc;this.sessions=sessions;}
    @GetMapping("/history")
    public Map<String,Object> history(@CookieValue(name="kunirank_member",required=false) String cookie,
                                      @RequestParam(defaultValue="0") int page) {
        long user=sessions.requireMember(cookie).id();
        int offset=Math.min(Math.max(page,0),100000)*20;
        var counts=jdbc.queryForMap("""
          SELECT count(*) AS total, count(*) FILTER(WHERE v.skipped) AS skipped
          FROM comparison_votes v JOIN vote_sessions s ON s.session_id=v.session_id WHERE s.user_id=?
          """,user);
        var items=jdbc.queryForList("""
          SELECT v.vote_id AS id,v.created_at AS created,v.skipped,b.name AS board,
                 a.name AS university_a,c.name AS university_b,u.name AS selected
          FROM comparison_votes v JOIN vote_sessions s ON s.session_id=v.session_id
          JOIN ranking_boards b ON b.board_id=v.board_id JOIN universities a ON a.university_id=v.university_a_id
          JOIN universities c ON c.university_id=v.university_b_id
          LEFT JOIN universities u ON u.university_id=v.selected_university_id
          WHERE s.user_id=? ORDER BY v.created_at DESC,v.vote_id DESC LIMIT 21 OFFSET ?
          """,user,offset);
        return Map.of("total",counts.get("total"),"skipped",counts.get("skipped"),"items",items.subList(0,Math.min(20,items.size())),
                "page",offset/20,"hasNext",items.size()>20);
    }
    public record VoteLink(String sessionKey) {}
    @PostMapping("/link-vote-session")
    public void link(@CookieValue(name="kunirank_member",required=false) String cookie,@RequestBody VoteLink input) {
        long user=sessions.requireMember(cookie).id();
        if(input.sessionKey()==null || !input.sessionKey().matches("[A-Za-z0-9_-]{20,64}"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        jdbc.update("UPDATE vote_sessions SET user_id=? WHERE session_key=? AND (user_id IS NULL OR user_id=?)",user,input.sessionKey(),user);
    }
}

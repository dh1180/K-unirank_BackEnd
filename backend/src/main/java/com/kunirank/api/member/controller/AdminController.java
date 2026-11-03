package com.kunirank.api.member.controller;

import com.kunirank.api.member.security.MemberSessionService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin")
public class AdminController {
    public record Stat(String label, long value) {}
    public record Overview(String operator, Instant generatedAt, Integer latestAdmissionYear,
                           List<Stat> stats, long suspiciousPercentiles, long shingyeongjuExcluded,
                           long votesToday, long skipsToday, List<Map<String,Object>> activity,
                           Map<String,Object> aiUsage, List<Map<String,Object>> recentAiRequests,
                           long activeSessions) {}
    private final MemberSessionService sessions;
    private final JdbcTemplate jdbc;
    public AdminController(MemberSessionService sessions,JdbcTemplate jdbc) {this.sessions=sessions;this.jdbc=jdbc;}
    private long count(String sql) {return jdbc.queryForObject(sql,Long.class);}
    @GetMapping("/overview")
    public Overview overview(@CookieValue(name="kunirank_member",required=false) String token) {
        var admin=sessions.requireAdmin(token); // Check current role before any aggregate query.
        return new Overview(admin.username(),Instant.now(),jdbc.queryForObject("SELECT max(admission_year) FROM admission_results",Integer.class),
            List.of(new Stat("전체 회원",count("SELECT count(*) FROM auth_user")),
                new Stat("활성 대학",count("SELECT count(*) FROM universities WHERE is_active")),
                new Stat("공개 입시결과",count("SELECT count(*) FROM admission_results")),
                new Stat("입결 지표",count("SELECT count(*) FROM admission_metrics")),
                new Stat("선호 투표",count("SELECT count(*) FROM comparison_votes WHERE NOT skipped")),
                new Stat("관심 대학 등록",count("SELECT count(*) FROM favorite_universities"))),
            count("SELECT count(*) FROM admission_metrics WHERE metric_code LIKE '%PERCENTILE_MEAN%' AND value>=99.985 AND value<99.995"),
            count("""
              SELECT count(*) FROM admission_metrics m JOIN admission_results r ON r.result_id=m.result_id
              JOIN universities u ON u.university_id=r.university_id
              WHERE u.name='신경주대학교' AND m.value=99 AND (m.metric_code LIKE '%PERCENTILE%' OR m.unit='백분위')
              """),
            count("SELECT count(*) FROM comparison_votes WHERE NOT skipped AND created_at>=date_trunc('day',CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul'"),
            count("SELECT count(*) FROM comparison_votes WHERE skipped AND created_at>=date_trunc('day',CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul'"),
            jdbc.queryForList("""
              WITH days AS (SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date-i AS day FROM generate_series(0,6) i),
              votes AS (SELECT (created_at AT TIME ZONE 'Asia/Seoul')::date AS day,count(*) FILTER(WHERE NOT skipped) votes,count(*) FILTER(WHERE skipped) skips
                FROM comparison_votes WHERE created_at>=CURRENT_TIMESTAMP-INTERVAL '8 days' GROUP BY 1),
              members AS (SELECT (date_joined AT TIME ZONE 'Asia/Seoul')::date AS day,count(*) members FROM auth_user
                WHERE date_joined>=CURRENT_TIMESTAMP-INTERVAL '8 days' GROUP BY 1),
              ai AS (SELECT (created_at AT TIME ZONE 'Asia/Seoul')::date AS day,count(*) requests FROM kunirank_ai_requests
                WHERE created_at>=CURRENT_TIMESTAMP-INTERVAL '8 days' GROUP BY 1)
              SELECT d.day::text AS day,coalesce(v.votes,0) votes,coalesce(v.skips,0) skips,coalesce(m.members,0) members,coalesce(a.requests,0) ai
              FROM days d LEFT JOIN votes v USING(day) LEFT JOIN members m USING(day) LEFT JOIN ai a USING(day) ORDER BY d.day
              """),
            jdbc.queryForMap("""
              SELECT count(*) requests,count(*) FILTER(WHERE status_code<300) successes,
                count(DISTINCT user_id) users,coalesce(round(avg(elapsed_ms)),0) average_ms,
                min(created_at)::text first_recorded_at FROM kunirank_ai_requests
              WHERE created_at>=date_trunc('day',CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul'
              """),
            jdbc.queryForList("""
              SELECT u.username,r.status_code,r.elapsed_ms,r.created_at::text created_at FROM kunirank_ai_requests r
              JOIN auth_user u ON u.id=r.user_id ORDER BY r.created_at DESC,r.request_id DESC LIMIT 10
              """),
            count("SELECT count(*) FROM kunirank_member_sessions WHERE expires_at>CURRENT_TIMESTAMP"));
    }

    @GetMapping("/members")
    public Map<String,Object> members(@CookieValue(name="kunirank_member",required=false) String cookie,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="all") String state,
            @RequestParam(defaultValue="0") int page) {
        sessions.requireAdmin(cookie);
        if(q.length()>80 || page<0 || page>10000 || !List.of("all","active","inactive","admin").contains(state))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        String where="username ILIKE ? ESCAPE '!'"+switch(state) {
            case "active" -> " AND is_active"; case "inactive" -> " AND NOT is_active";
            case "admin" -> " AND (is_staff OR is_superuser)"; default -> "";};
        String term="%"+q.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        Long total=jdbc.queryForObject("SELECT count(*) FROM auth_user WHERE "+where,Long.class,term);
        var items=jdbc.queryForList("SELECT id,username,is_active,(is_staff OR is_superuser) admin,date_joined::text joined FROM auth_user WHERE "+where+" ORDER BY date_joined DESC,id DESC LIMIT 20 OFFSET ?",term,page*20);
        return Map.of("items",items,"total",total,"page",page,"hasNext",(page+1L)*20<total);
    }

    @GetMapping("/quality")
    public Map<String,Object> quality(@CookieValue(name="kunirank_member",required=false) String cookie,
            @RequestParam(defaultValue="sentinel") String kind,@RequestParam(defaultValue="0") int page) {
        sessions.requireAdmin(cookie);
        if(page<0 || page>10000) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        String where=switch(kind) {
            case "sentinel" -> "m.metric_code LIKE '%PERCENTILE_MEAN%' AND m.value>=99.985 AND m.value<99.995";
            case "shingyeongju" -> "u.name='신경주대학교' AND m.value=99 AND (m.metric_code LIKE '%PERCENTILE%' OR m.unit='백분위')";
            case "range" -> "((m.unit='백분위' AND (m.value<0 OR m.value>100)) OR (m.unit='등급' AND (m.value<1 OR m.value>9)))";
            default -> throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);};
        String from=" FROM admission_metrics m JOIN admission_results r ON r.result_id=m.result_id JOIN universities u ON u.university_id=r.university_id JOIN recruitment_units n ON n.recruitment_unit_id=r.recruitment_unit_id WHERE "+where;
        Long total=jdbc.queryForObject("SELECT count(*)"+from,Long.class);
        var items=jdbc.queryForList("SELECT m.metric_id,m.metric_code,m.value,m.unit,u.university_id,u.name university,n.name major,r.admission_year"+from+" ORDER BY r.admission_year DESC,m.metric_id DESC LIMIT 12 OFFSET ?",page*12);
        return Map.of("items",items,"total",total,"page",page,"hasNext",(page+1L)*12<total);
    }
}

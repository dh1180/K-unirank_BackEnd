package com.kunirank.api.member.controller;

import com.kunirank.api.member.security.MemberSessionService;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin")
public class AdminController {
    public record Stat(String label, long value) {}
    public record Overview(String operator, Instant generatedAt, Integer latestAdmissionYear,
                           List<Stat> stats, long suspiciousPercentiles, long shingyeongjuExcluded,
                           long votesToday, long skipsToday) {}
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
            count("SELECT count(*) FROM comparison_votes WHERE skipped AND created_at>=date_trunc('day',CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul'"));
    }
}

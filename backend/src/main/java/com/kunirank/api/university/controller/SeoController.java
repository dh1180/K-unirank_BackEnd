package com.kunirank.api.university.controller;

import com.kunirank.api.ranking.discovery.RankingDiscoveryService;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seo")
public class SeoController {
    public record SitemapUrl(String path, Instant lastModified, String changeFrequency, double priority) {}
    private final JdbcTemplate jdbc;
    public SeoController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping("/urls")
    @Transactional(readOnly = true)
    public List<SitemapUrl> urls() {
        List<SitemapUrl> urls = new ArrayList<>();
        for (String path : List.of("/", "/universities/", "/rankings/", "/ranking/", "/admissions/compare/", "/admissions/ranking/")) {
            urls.add(new SitemapUrl(path, null, "daily", path.equals("/") ? 1.0 : 0.9));
        }
        urls.addAll(jdbc.query("""
            SELECT '/universities/' || university_id || '/' AS path, updated_at AS lastmod, 0.9 AS priority
            FROM universities WHERE is_active
            UNION ALL
            SELECT '/admissions/university/' || u.university_id || '/', COALESCE(MAX(s.collected_at),u.updated_at), 0.95
            FROM universities u JOIN admission_results r ON r.university_id=u.university_id
            JOIN admission_sources s ON s.source_id=r.source_id
            WHERE u.is_active GROUP BY u.university_id,u.updated_at
            UNION ALL
            SELECT '/admissions/unit/' || ru.recruitment_unit_id || '/', MAX(s.collected_at), 0.85
            FROM recruitment_units ru JOIN universities u ON u.university_id=ru.university_id
            JOIN admission_results r ON r.recruitment_unit_id=ru.recruitment_unit_id
            JOIN admission_sources s ON s.source_id=r.source_id
            WHERE ru.is_active AND u.is_active GROUP BY ru.recruitment_unit_id
            ORDER BY path
            """, (rs,n) -> new SitemapUrl(rs.getString("path"),
                    rs.getTimestamp("lastmod") == null ? null : rs.getTimestamp("lastmod").toInstant(), "weekly", rs.getDouble("priority"))));
        Set<String> available = new HashSet<>(jdbc.queryForList("""
            SELECT DISTINCT i.indicator_code FROM university_indicators i
            JOIN universities u ON u.university_id=i.university_id
            WHERE i.source='ACADEMYINFO' AND i.value>0 AND u.is_active
            """, String.class));
        RankingDiscoveryService.INDICATORS.stream().filter(m -> available.contains(m.code())).forEach(m ->
                urls.add(new SitemapUrl("/universities/indicators/" + m.slug() + "/", null, "weekly", 0.85)));
        return urls;
    }
}

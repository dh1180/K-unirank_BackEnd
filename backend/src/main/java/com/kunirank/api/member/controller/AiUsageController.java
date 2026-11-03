package com.kunirank.api.member.controller;

import com.kunirank.api.member.security.MemberSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/member/ai/usage")
public class AiUsageController {
    private final MemberSessionService sessions;
    private final JdbcTemplate jdbc;
    public AiUsageController(MemberSessionService sessions,JdbcTemplate jdbc) {this.sessions=sessions;this.jdbc=jdbc;}
    public record Input(@NotNull UUID requestId,@Min(200) @Max(599) int statusCode,
                        @Min(0) @Max(120000) int elapsedMs) {}
    @PostMapping
    public void record(@CookieValue(name="kunirank_member",required=false) String cookie,
                       @Valid @RequestBody Input input) {
        long user=sessions.requireMember(cookie).id();
        jdbc.update("INSERT INTO kunirank_ai_requests(request_id,user_id,status_code,elapsed_ms) VALUES (?,?,?,?) ON CONFLICT(request_id) DO NOTHING",
            input.requestId(),user,input.statusCode(),input.elapsedMs());
    }
}

package com.kunirank.api.ai.service;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Per-process UTC daily budget. Failed provider calls consume a request as before. */
final class AiRequestBudget {
    private final Clock clock;
    private LocalDate day;
    private int requests;
    private int active;

    AiRequestBudget(Clock clock) { this.clock = clock; }

    synchronized void enter(int dailyLimit, int concurrency) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) { day = today; requests = 0; }
        if (requests >= dailyLimit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "오늘의 무료 AI 검색 한도에 도달했습니다. 일반 검색을 이용해 주세요.");
        if (active >= concurrency) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "AI 검색이 진행 중입니다. 잠시 후 다시 시도해 주세요.");
        requests++;
        active++;
    }

    synchronized void leave() { active--; }
}

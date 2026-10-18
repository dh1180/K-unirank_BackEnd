package com.kunirank.api.ranking.controller;

import com.kunirank.api.ranking.dto.RankingResponse;
import com.kunirank.api.ranking.dto.VsResponse;
import com.kunirank.api.ranking.service.VsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.kunirank.api.ranking.service.RankingQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/boards")
public class RankingController {
    private final RankingQueryService rankingService;
    private final VsService vsService;

    public RankingController(RankingQueryService rankingService, VsService vsService) {
        this.rankingService = rankingService;
        this.vsService = vsService;
    }

    @GetMapping("/{slug}/next")
    public VsResponse.Pair next(@PathVariable String slug,
                                @CookieValue(name = "kunirank_vs", required = false) String session,
                                HttpServletResponse response) {
        return vsService.next(slug, session, response);
    }

    @PostMapping("/{slug}/vote")
    @ResponseStatus(HttpStatus.CREATED)
    public VsResponse.VoteReceipt vote(@PathVariable String slug,
                                       @CookieValue(name = "kunirank_vs", required = false) String session,
                                       @RequestHeader(name = "Origin", required = false) String origin,
                                       @Valid @RequestBody VsResponse.VoteRequest request) {
        return vsService.vote(slug, session, origin, request);
    }

    @GetMapping({"/{slug}/ranking", "/{slug}/ranking/"})
    public RankingResponse ranking(@PathVariable String slug,
                                   @RequestParam(defaultValue = "50") int limit) {
        return rankingService.ranking(slug, limit);
    }
}

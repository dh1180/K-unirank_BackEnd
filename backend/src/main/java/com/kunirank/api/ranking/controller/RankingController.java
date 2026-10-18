package com.kunirank.api.ranking.controller;

import com.kunirank.api.ranking.dto.RankingResponse;
import com.kunirank.api.ranking.service.RankingQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/boards")
public class RankingController {
    private final RankingQueryService rankingService;

    public RankingController(RankingQueryService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping({"/{slug}/ranking", "/{slug}/ranking/"})
    public RankingResponse ranking(@PathVariable String slug,
                                   @RequestParam(defaultValue = "50") int limit) {
        return rankingService.ranking(slug, limit);
    }
}

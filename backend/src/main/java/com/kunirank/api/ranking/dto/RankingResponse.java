package com.kunirank.api.ranking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RankingResponse(Board board, List<UniversityRank> ranking) {
    public record Board(String slug, String name) {}

    public record UniversityRank(
            int rank,
            @JsonProperty("university_id") Long universityId,
            String name,
            double rating,
            @JsonProperty("match_count") long matchCount,
            @JsonProperty("win_count") long winCount,
            @JsonProperty("loss_count") long lossCount
    ) {}
}

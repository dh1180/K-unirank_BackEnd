package com.kunirank.api.ranking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class VsResponse {
    private VsResponse() {}

    public record Board(String slug, String name) {}
    public record School(Long id, String name,
                         @JsonProperty("logo_path") String logoPath, String region) {}
    public record Pair(Board board, List<School> pair, String challenge, long voteCount) {}
    public record VoteRequest(
            @NotNull @JsonProperty("university_a") Long universityA,
            @NotNull @JsonProperty("university_b") Long universityB,
            @JsonProperty("selected_university") Long selectedUniversity,
            boolean skipped,
            @NotNull String challenge
    ) {}
    public record VoteReceipt(@JsonProperty("vote_id") Long voteId,
                              @JsonProperty("vote_count") long voteCount) {}
}

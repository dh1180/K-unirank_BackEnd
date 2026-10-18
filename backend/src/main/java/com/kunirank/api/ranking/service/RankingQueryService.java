package com.kunirank.api.ranking.service;

import com.kunirank.api.ranking.dto.RankingResponse;
import com.kunirank.api.ranking.entity.RankingBoard;
import com.kunirank.api.ranking.repository.RankingBoardRepository;
import com.kunirank.api.ranking.repository.UniversityRatingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RankingQueryService {
    private final RankingBoardRepository boards;
    private final UniversityRatingRepository ratings;

    public RankingQueryService(RankingBoardRepository boards, UniversityRatingRepository ratings) {
        this.boards = boards;
        this.ratings = ratings;
    }

    @Transactional(readOnly = true)
    public RankingResponse ranking(String boardSlug, int requestedLimit) {
        // The existing Django API clamps 1 <= limit <= 200.
        int limit = Math.min(200, Math.max(1, requestedLimit));
        RankingBoard board = boards.findBySlugAndActiveTrue(boardSlug)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "활성 랭킹 보드를 찾을 수 없습니다."));

        List<RankingResponse.UniversityRank> rows = new ArrayList<>();
        for (var rating : ratings.findActiveRatings(board, PageRequest.of(0, limit))) {
            double rounded = BigDecimal.valueOf(rating.getRating())
                    .setScale(2, RoundingMode.HALF_EVEN).doubleValue();
            rows.add(new RankingResponse.UniversityRank(
                    rows.size() + 1,
                    rating.getUniversity().getId(),
                    rating.getUniversity().getName(),
                    rounded,
                    rating.getMatchCount(),
                    rating.getWinCount(),
                    rating.getLossCount()
            ));
        }
        return new RankingResponse(new RankingResponse.Board(board.getSlug(), board.getName()), rows);
    }
}

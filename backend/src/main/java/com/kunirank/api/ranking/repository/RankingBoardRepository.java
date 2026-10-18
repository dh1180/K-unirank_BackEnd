package com.kunirank.api.ranking.repository;

import com.kunirank.api.ranking.entity.RankingBoard;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingBoardRepository extends JpaRepository<RankingBoard, Long> {
    Optional<RankingBoard> findBySlugAndActiveTrue(String slug);
}

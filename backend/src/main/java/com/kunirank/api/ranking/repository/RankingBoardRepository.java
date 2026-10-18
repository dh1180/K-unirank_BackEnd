package com.kunirank.api.ranking.repository;

import com.kunirank.api.ranking.entity.RankingBoard;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingBoardRepository extends JpaRepository<RankingBoard, Long> {
    Optional<RankingBoard> findBySlugAndActiveTrue(String slug);

    // Serialize votes on a board, including creation of previously absent ratings.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from RankingBoard b where b.slug = :slug and b.active = true")
    Optional<RankingBoard> lockActive(@Param("slug") String slug);
}

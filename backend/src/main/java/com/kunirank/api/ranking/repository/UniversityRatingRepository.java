package com.kunirank.api.ranking.repository;

import com.kunirank.api.ranking.entity.RankingBoard;
import com.kunirank.api.ranking.entity.UniversityRating;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UniversityRatingRepository extends JpaRepository<UniversityRating, Long> {
    @Query("""
       select rating from UniversityRating rating
       join fetch rating.university university
       where rating.board = :board and university.active = true
         and rating.matchCount >= 0
       order by rating.rating desc, university.name asc
       """)
    List<UniversityRating> findActiveRatings(@Param("board") RankingBoard board, Pageable pageable);
}

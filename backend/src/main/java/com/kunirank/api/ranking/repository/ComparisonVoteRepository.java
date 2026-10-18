package com.kunirank.api.ranking.repository;

import com.kunirank.api.ranking.entity.ComparisonVote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComparisonVoteRepository extends JpaRepository<ComparisonVote, Long> {
    @Query("""
      select v from ComparisonVote v
        join fetch v.universityA join fetch v.universityB
      where v.session.id = :session and v.board.id = :board
      order by v.createdAt desc, v.id desc
      """)
    List<ComparisonVote> recent(@Param("session") UUID session,
                                @Param("board") Long board, Pageable pageable);

    @Query("""
      select count(v) from ComparisonVote v
      where v.session.id = :session and v.board.id = :board
        and v.skipped = false
        and ((v.universityA.id = :a and v.universityB.id = :b)
          or (v.universityA.id = :b and v.universityB.id = :a))
      """)
    long previous(@Param("session") UUID session, @Param("board") Long board,
                  @Param("a") Long a, @Param("b") Long b);
}

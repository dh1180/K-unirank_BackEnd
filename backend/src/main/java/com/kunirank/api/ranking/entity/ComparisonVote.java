package com.kunirank.api.ranking.entity;

import com.kunirank.api.university.entity.University;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "comparison_votes")
public class ComparisonVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private RankingBoard board;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private VoteSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_a_id", nullable = false)
    private University universityA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_b_id", nullable = false)
    private University universityB;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_university_id")
    private University selectedUniversity;

    @Column(name = "skipped", nullable = false)
    private boolean skipped;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ComparisonVote() {}

    public ComparisonVote(RankingBoard board, VoteSession session, University a,
                          University b, University selected, boolean skipped) {
        this.board = board;
        this.session = session;
        this.universityA = a;
        this.universityB = b;
        this.selectedUniversity = selected;
        this.skipped = skipped;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public University getUniversityA() { return universityA; }
    public University getUniversityB() { return universityB; }
    public boolean isSkipped() { return skipped; }
}

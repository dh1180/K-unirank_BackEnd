package com.kunirank.api.ranking.entity;

import com.kunirank.api.university.entity.University;
import jakarta.persistence.*;

@Entity
@Table(name = "university_ratings")
public class UniversityRating {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rating_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private RankingBoard board;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(name = "rating", nullable = false)
    private double rating = 1500.0;

    @Column(name = "rating_deviation", nullable = false)
    private double ratingDeviation = 350.0;

    @Column(name = "volatility", nullable = false)
    private double volatility = 0.06;

    @Column(name = "match_count", nullable = false)
    private long matchCount;

    @Column(name = "win_count", nullable = false)
    private long winCount;

    @Column(name = "loss_count", nullable = false)
    private long lossCount;

    protected UniversityRating() {}

    public UniversityRating(RankingBoard board, University university, double rating,
                            long matchCount, long winCount, long lossCount) {
        this.board = board;
        this.university = university;
        this.rating = rating;
        this.matchCount = matchCount;
        this.winCount = winCount;
        this.lossCount = lossCount;
    }

    public UniversityRating(RankingBoard board, University university) {
        this.board = board;
        this.university = university;
    }

    public void record(double nextRating, double nextRd, double nextVolatility, boolean win) {
        this.rating = nextRating;
        this.ratingDeviation = nextRd;
        this.volatility = nextVolatility;
        this.matchCount++;
        if (win) this.winCount++; else this.lossCount++;
    }

    public Long getId() { return id; }
    public RankingBoard getBoard() { return board; }
    public University getUniversity() { return university; }
    public double getRating() { return rating; }
    public double getRatingDeviation() { return ratingDeviation; }
    public double getVolatility() { return volatility; }
    public long getMatchCount() { return matchCount; }
    public long getWinCount() { return winCount; }
    public long getLossCount() { return lossCount; }
}

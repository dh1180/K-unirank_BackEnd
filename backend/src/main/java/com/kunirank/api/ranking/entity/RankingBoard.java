package com.kunirank.api.ranking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ranking_boards")
public class RankingBoard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_id")
    private Long id;

    @Column(name = "slug", nullable = false, length = 50, unique = true)
    private String slug;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected RankingBoard() {}

    public RankingBoard(String slug, String name, boolean active) {
        this.slug = slug;
        this.name = name;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}

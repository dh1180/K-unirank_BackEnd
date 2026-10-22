package com.kunirank.api.ranking.entity;

import jakarta.persistence.*;
import java.time.Instant;

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

    @Column(name = "description", nullable = false, length = 255)
    private String description = "";

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() { updatedAt = Instant.now(); }

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

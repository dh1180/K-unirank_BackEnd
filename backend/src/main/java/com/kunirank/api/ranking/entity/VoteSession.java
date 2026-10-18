package com.kunirank.api.ranking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vote_sessions")
public class VoteSession {
    @Id
    @Column(name = "session_id", nullable = false)
    private UUID id;

    @Column(name = "session_key", nullable = false, length = 64)
    private String sessionKey;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash = "";

    @Column(name = "vote_count", nullable = false)
    private long voteCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected VoteSession() {}

    public VoteSession(String sessionKey) {
        this.id = UUID.randomUUID();
        this.sessionKey = sessionKey;
        this.createdAt = Instant.now();
        this.lastSeenAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getSessionKey() { return sessionKey; }
    public long getVoteCount() { return voteCount; }
    public void increment() { voteCount++; lastSeenAt = Instant.now(); }
}

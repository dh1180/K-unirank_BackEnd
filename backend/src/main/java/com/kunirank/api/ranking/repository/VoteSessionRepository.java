package com.kunirank.api.ranking.repository;

import com.kunirank.api.ranking.entity.VoteSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteSessionRepository extends JpaRepository<VoteSession, UUID> {
    Optional<VoteSession> findFirstBySessionKeyOrderByLastSeenAtDesc(String sessionKey);
}

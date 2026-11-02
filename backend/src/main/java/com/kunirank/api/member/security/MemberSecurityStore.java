package com.kunirank.api.member.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Server revocation and shared attempt limits. Plain session tokens are never persisted. */
@Repository
public class MemberSecurityStore {
    private final JdbcTemplate jdbc;
    public MemberSecurityStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public boolean attempt(String bucket, int maximum, int seconds) {
        Integer count = jdbc.queryForObject("""
            INSERT INTO kunirank_member_limits(bucket_key,attempts,expires_at)
            VALUES (?,1,CURRENT_TIMESTAMP + make_interval(secs => ?))
            ON CONFLICT(bucket_key) DO UPDATE SET
              attempts=CASE WHEN kunirank_member_limits.expires_at<=CURRENT_TIMESTAMP
                       THEN 1 ELSE kunirank_member_limits.attempts+1 END,
              expires_at=CASE WHEN kunirank_member_limits.expires_at<=CURRENT_TIMESTAMP
                         THEN EXCLUDED.expires_at ELSE kunirank_member_limits.expires_at END
            RETURNING attempts
            """, Integer.class, hash(bucket), seconds);
        return count != null && count <= maximum;
    }
    public void create(String token, long userId, long expires) {
        jdbc.update("DELETE FROM kunirank_member_sessions WHERE expires_at<=CURRENT_TIMESTAMP");
        jdbc.update("DELETE FROM kunirank_member_limits WHERE expires_at<=CURRENT_TIMESTAMP");
        jdbc.update("INSERT INTO kunirank_member_sessions(token_hash,user_id,expires_at) VALUES (?,?,?)",
                hash(token), userId, Timestamp.from(Instant.ofEpochSecond(expires)));
    }
    public boolean valid(String token, long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM kunirank_member_sessions
              WHERE token_hash=? AND user_id=? AND expires_at>CURRENT_TIMESTAMP)
            """, Boolean.class, hash(token), userId));
    }
    public void revoke(String token) {
        if (token != null && token.length()<=1024)
            jdbc.update("DELETE FROM kunirank_member_sessions WHERE token_hash=?", hash(token));
    }
}

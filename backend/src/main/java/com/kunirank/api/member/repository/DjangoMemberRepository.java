package com.kunirank.api.member.repository;

import com.kunirank.api.member.model.MemberAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Direct read-only queries avoid assumptions about Django auth_user's legacy
 * AutoField (integer) versus BigAutoField (bigint) primary-key migrations.
 */
@Repository
public class DjangoMemberRepository {
    private final JdbcTemplate jdbc;

    public DjangoMemberRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<MemberAccount> byUsername(String username) {
        return first(jdbc.query(
                "SELECT id, username, password, is_active FROM auth_user WHERE username = ?",
                (rs, n) -> new MemberAccount(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password"), rs.getBoolean("is_active")), username));
    }

    public Optional<MemberAccount> byId(long id) {
        return first(jdbc.query(
                "SELECT id, username, password, is_active FROM auth_user WHERE id = ?",
                (rs, n) -> new MemberAccount(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password"), rs.getBoolean("is_active")), id));
    }

    private static <T> Optional<T> first(List<T> result) {
        return result.stream().findFirst();
    }
}

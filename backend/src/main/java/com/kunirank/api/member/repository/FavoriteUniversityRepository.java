package com.kunirank.api.member.repository;

import com.kunirank.api.university.entity.University;
import com.kunirank.api.university.repository.UniversityRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class FavoriteUniversityRepository {
    public record Favorite(long id, String name, String region) {}

    private final JdbcTemplate jdbc;
    private final UniversityRepository universities;

    public FavoriteUniversityRepository(JdbcTemplate jdbc, UniversityRepository universities) {
        this.jdbc = jdbc;
        this.universities = universities;
    }

    @Transactional(readOnly = true)
    public List<Favorite> list(long memberId) {
        return jdbc.query("""
             SELECT u.university_id, u.name, u.region
             FROM favorite_universities f JOIN universities u
               ON u.university_id = f.university_id
             WHERE f.user_id = ? AND u.is_active = true
             ORDER BY f.created_at DESC, f.favorite_university_id DESC
             """, (rs, index) -> new Favorite(rs.getLong(1), rs.getString(2), rs.getString(3)), memberId);
    }

    @Transactional
    public void add(long memberId, long universityId) {
        if (universities.findById(universityId).filter(University::isActive).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "대학을 찾을 수 없습니다.");
        }
        jdbc.update("""
            INSERT INTO favorite_universities (user_id,university_id,created_at)
            VALUES (?,?,CURRENT_TIMESTAMP)
            ON CONFLICT (user_id,university_id) DO NOTHING
            """, memberId, universityId);
    }

    @Transactional
    public void remove(long memberId, long universityId) {
        jdbc.update("DELETE FROM favorite_universities WHERE user_id=? AND university_id=?",
                memberId, universityId);
    }
}

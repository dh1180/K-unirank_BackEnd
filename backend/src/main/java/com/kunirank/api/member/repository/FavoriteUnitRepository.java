package com.kunirank.api.member.repository;

import com.kunirank.api.admission.repository.RecruitmentUnitRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Existing Django favorite_recruitment_units schema, without a new migration. */
@Repository
public class FavoriteUnitRepository {
    public record Favorite(long id, String name, String collegeName,
                           long universityId, String universityName) {}

    private final JdbcTemplate jdbc;
    private final RecruitmentUnitRepository units;

    public FavoriteUnitRepository(JdbcTemplate jdbc, RecruitmentUnitRepository units) {
        this.jdbc = jdbc;
        this.units = units;
    }

    @Transactional(readOnly = true)
    public List<Favorite> list(long memberId) {
        return jdbc.query("""
            SELECT r.recruitment_unit_id, r.name, r.college_name,
                   u.university_id, u.name AS university_name
            FROM favorite_recruitment_units f
            JOIN recruitment_units r ON r.recruitment_unit_id=f.recruitment_unit_id
            JOIN universities u ON u.university_id=r.university_id
            WHERE f.user_id=? AND r.is_active=true AND u.is_active=true
            ORDER BY f.created_at DESC, f.favorite_recruitment_unit_id DESC
            """, (rs, idx) -> new Favorite(rs.getLong("recruitment_unit_id"), rs.getString("name"),
                rs.getString("college_name"), rs.getLong("university_id"),
                rs.getString("university_name")), memberId);
    }

    @Transactional
    public void add(long memberId, long unitId) {
        if (units.findByIdAndActiveTrueAndUniversityActiveTrue(unitId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "모집단위를 찾을 수 없습니다.");
        }
        jdbc.update("""
            INSERT INTO favorite_recruitment_units(user_id,recruitment_unit_id,created_at)
            VALUES (?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(user_id,recruitment_unit_id) DO NOTHING
            """, memberId, unitId);
    }

    @Transactional
    public void remove(long memberId, long unitId) {
        jdbc.update("DELETE FROM favorite_recruitment_units WHERE user_id=? AND recruitment_unit_id=?",
                    memberId, unitId);
    }
}

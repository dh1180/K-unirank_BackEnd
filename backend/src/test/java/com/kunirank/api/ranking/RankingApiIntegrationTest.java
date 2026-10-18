package com.kunirank.api.ranking;

import com.kunirank.api.ranking.entity.RankingBoard;
import com.kunirank.api.ranking.entity.UniversityRating;
import com.kunirank.api.ranking.repository.RankingBoardRepository;
import com.kunirank.api.ranking.repository.UniversityRatingRepository;
import com.kunirank.api.university.entity.University;
import com.kunirank.api.university.repository.UniversityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RankingApiIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired RankingBoardRepository boards;
    @Autowired UniversityRatingRepository ratings;
    @Autowired UniversityRepository universities;

    private RankingBoard board;

    @BeforeEach
    void setup() {
        ratings.deleteAll();
        boards.deleteAll();
        universities.deleteAll();
        board = boards.save(new RankingBoard("overall", "전체 대학 선호도", true));
    }

    private void add(String name, boolean active, double score, long matches) {
        University school = universities.save(new University(name, "서울", active));
        ratings.save(new UniversityRating(board, school, score, matches, matches / 2, matches / 2));
    }

    @Test
    void returnsExistingRatingsInDescendingOrderAndPreservesCounts() throws Exception {
        add("가대학교", true, 1500.123, 14);
        add("나대학교", true, 1600.789, 42);
        add("폐교대학교", false, 1999, 50);

        mvc.perform(get("/api/v1/boards/overall/ranking").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.board.slug").value("overall"))
                .andExpect(jsonPath("$.ranking.length()").value(2))
                .andExpect(jsonPath("$.ranking[0].name").value("나대학교"))
                .andExpect(jsonPath("$.ranking[0].rank").value(1))
                .andExpect(jsonPath("$.ranking[0].rating").value(1600.79))
                .andExpect(jsonPath("$.ranking[0].match_count").value(42))
                .andExpect(jsonPath("$.ranking[0].win_count").value(21));
    }

    @Test
    void tiesAreSortedByUniversityNameAndLimitsAreClamped() throws Exception {
        add("나대학교", true, 1500, 5);
        add("가대학교", true, 1500, 3);
        mvc.perform(get("/api/v1/boards/overall/ranking/").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranking.length()").value(1))
                .andExpect(jsonPath("$.ranking[0].name").value("가대학교"));

        mvc.perform(get("/api/v1/boards/overall/ranking").param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranking.length()").value(1));
    }

    @Test
    void missingBoardIs404AndDoesNotCreateNewBoard() throws Exception {
        mvc.perform(get("/api/v1/boards/unknown/ranking"))
                .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(1, boards.count());
    }
}

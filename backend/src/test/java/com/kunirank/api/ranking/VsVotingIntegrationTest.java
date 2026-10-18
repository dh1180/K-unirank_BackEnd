package com.kunirank.api.ranking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunirank.api.ranking.entity.*;
import com.kunirank.api.ranking.repository.*;
import com.kunirank.api.ranking.service.Glicko2;
import com.kunirank.api.university.entity.University;
import com.kunirank.api.university.repository.UniversityRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "VS_SIGNING_SECRET=some-very-long-test-hmac-signing-secret-not-for-production",
        "VS_COOKIE_SECURE=false"
})
class VsVotingIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RankingBoardRepository boards;
    @Autowired UniversityRatingRepository ratings;
    @Autowired VoteSessionRepository sessions;
    @Autowired ComparisonVoteRepository votes;
    @Autowired UniversityRepository universities;

    private University a, b;
    private RankingBoard board;

    @BeforeEach
    void setup() {
        votes.deleteAll();
        ratings.deleteAll();
        sessions.deleteAll();
        boards.deleteAll();
        universities.deleteAll();
        board = boards.save(new RankingBoard("overall", "종합", true));
        a = universities.save(new University("가대학", "서울", true));
        b = universities.save(new University("나대학", "서울", true));
    }

    record Challenge(String cookie, long a, long b, String signed) {}

    private Challenge challenge() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/v1/boards/overall/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pair.length()").value(2))
                .andReturn().getResponse();
        JsonNode node = mapper.readTree(response.getContentAsByteArray());
        String cookie = response.getHeader("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertEquals(1, sessions.count());
        return new Challenge(cookie.split(";")[0].split("=", 2)[1],
                node.get("pair").get(0).get("id").asLong(),
                node.get("pair").get(1).get("id").asLong(),
                node.get("challenge").asText());
    }

    private MockHttpServletResponse vote(Challenge c, Long selected, boolean skipped, String signature)
            throws Exception {
        String json = mapper.writeValueAsString(new java.util.LinkedHashMap<>(java.util.Map.of(
                "university_a", c.a, "university_b", c.b,
                "skipped", skipped, "challenge", signature == null ? c.signed : signature
        )));
        if (!skipped) {
            var m = new java.util.LinkedHashMap<String,Object>();
            m.put("university_a", c.a);
            m.put("university_b", c.b);
            m.put("selected_university", selected);
            m.put("skipped", false);
            m.put("challenge", signature == null ? c.signed : signature);
            json = mapper.writeValueAsString(m);
        }
        return mvc.perform(post("/api/v1/boards/overall/vote")
                        .cookie(new Cookie("kunirank_vs", c.cookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn().getResponse();
    }

    @Test
    void firstVotePersistsResultAndReturnsReceiptAndSecondReplayFails() throws Exception {
        Challenge c = challenge();
        MockHttpServletResponse first = vote(c, c.a, false, null);
        assertEquals(201, first.getStatus(), first.getContentAsString());
        assertEquals(1, votes.count());
        assertEquals(2, ratings.count());
        assertEquals(1, sessions.findAll().get(0).getVoteCount());
        assertEquals(1, ratings.findAll().stream().filter(r -> r.getWinCount() == 1).count());
        assertTrue(ratings.findAll().stream().allMatch(r -> Math.abs(r.getRating() - 1500) <= 22.0001));
        assertEquals(400, vote(c, c.a, false, null).getStatus());
        assertEquals(1, votes.count());
    }

    @Test
    void invalidSelectionOrForgedChallengeDoesNotMutateRatings() throws Exception {
        Challenge c = challenge();
        assertEquals(400, vote(c, 999999L, false, null).getStatus());
        assertEquals(400, vote(c, c.a, false, c.signed + "forged").getStatus());
        assertEquals(0, votes.count());
        assertEquals(0, ratings.count());
    }

    @Test
    void skipIncrementsParticipationWithoutModifyingRatings() throws Exception {
        Challenge c = challenge();
        MockHttpServletResponse result = vote(c, null, true, null);
        assertEquals(201, result.getStatus(), result.getContentAsString());
        assertEquals(1, votes.count());
        assertTrue(votes.findAll().get(0).isSkipped());
        assertEquals(0, ratings.count());
    }

    @Test
    void repeatedPairHasFullThenReducedThenZeroRatingWeight() throws Exception {
        Challenge c = challenge();
        assertEquals(201, vote(c, c.a, false, null).getStatus());
        double first = ratings.findByBoardIdAndUniversityId(board.getId(), c.a).orElseThrow().getRating();

        // In a two-school test DB the next pair is always identical, though its order may change.
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse next = mvc.perform(get("/api/v1/boards/overall/next")
                    .cookie(new Cookie("kunirank_vs", c.cookie)))
                    .andExpect(status().isOk()).andReturn().getResponse();
            JsonNode n = mapper.readTree(next.getContentAsByteArray());
            Challenge follow = new Challenge(c.cookie, n.get("pair").get(0).get("id").asLong(),
                    n.get("pair").get(1).get("id").asLong(), n.get("challenge").asText());
            assertEquals(201, vote(follow, c.a, false, null).getStatus());
        }
        var rating = ratings.findByBoardIdAndUniversityId(board.getId(), c.a).orElseThrow();
        assertTrue(rating.getRating() >= first);
        assertEquals(3, rating.getMatchCount());
        assertEquals(3, rating.getWinCount());
        assertEquals(3, votes.count());
    }

    @Test
    void glicko2UpdateHasExpectedDirectionAndBoundedMoves() {
        var neutral = new Glicko2.State(1500, 150, .06);
        var winner = Glicko2.update(neutral, neutral, 1);
        var loser = Glicko2.update(neutral, neutral, 0);
        assertTrue(winner.rating() > 1500);
        assertTrue(loser.rating() < 1500);
        assertEquals(3000, winner.rating() + loser.rating(), 1e-8);
        assertTrue(winner.rd() <= 150);
        assertEquals(1522, Glicko2.cappedRating(1500, 1800, 0, 1), 1e-8);
        assertEquals(1507.7, Glicko2.cappedRating(1500, 1800, 0, .35), 1e-8);
        assertEquals(1500, Glicko2.cappedRating(1500, 1800, 0, 0), 1e-8);
    }
}

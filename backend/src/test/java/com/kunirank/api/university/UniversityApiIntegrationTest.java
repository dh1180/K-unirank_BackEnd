package com.kunirank.api.university;

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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class UniversityApiIntegrationTest {

    // An isolated temporary PostgreSQL database: never the production DATABASE_URL.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UniversityRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new University("Alpha University", "서울", true),
                new University("Beta University", "부산", true),
                new University("Gamma University", "서울", false)
        ));
    }

    @Test
    void listReturnsOnlyActiveUniversitiesAndKeepsStablePages() throws Exception {
        mockMvc.perform(get("/api/v1/universities")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Alpha University"))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/api/v1/universities")
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Beta University"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void searchFiltersByNameAndRegionWithoutShowingInactiveSchools() throws Exception {
        mockMvc.perform(get("/api/v1/universities")
                        .param("q", "alpha").param("region", "서울"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Alpha University"));

        mockMvc.perform(get("/api/v1/universities")
                        .param("q", "Gamma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void rejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/universities").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void treatsSqlWildcardsAsLiteralSearchCharacters() throws Exception {
        mockMvc.perform(get("/api/v1/universities").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }
}

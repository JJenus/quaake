package com.homefit.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end slice against real PostGIS: seed a property + its cell sub-scores, then exercise
 * GET /properties/{id} and POST /properties/{id}/fit. Uses Testcontainers when Docker is present,
 * else falls back to a local PostGIS at localhost:5432/homefit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class VerticalSliceIT {

    private static final String DB_USER = "homefit";
    private static final String DB_PASS = "homefit";
    private static final String DB_URL;

    static {
        String url;
        try {
            PostgreSQLContainer<?> c = new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("homefit").withUsername(DB_USER).withPassword(DB_PASS);
            c.start();
            String base = c.getJdbcUrl();
            url = base + (base.contains("?") ? "&" : "?") + "stringtype=unspecified";
        } catch (Exception e) {
            url = "jdbc:postgresql://localhost:5432/homefit?stringtype=unspecified";
        }
        DB_URL = url;
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> DB_URL);
        r.add("spring.datasource.username", () -> DB_USER);
        r.add("spring.datasource.password", () -> DB_PASS);
    }

    static final long CELL = 617700440803966975L;
    static final UUID PROP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void seed() {
        jdbc.execute("""
            INSERT INTO ingest.data_source (id, code, name, group_tier, default_tier)
            VALUES (1,'seed','Seed','test','modeled'::source_tier) ON CONFLICT (id) DO NOTHING
            """);
        jdbc.update("DELETE FROM ingest.cell_subscore WHERE cell_h3 = ?", CELL);
        jdbc.update("""
            INSERT INTO ingest.cell_subscore (cell_h3, dimension, subscore, confidence, source_tier) VALUES
              (?, 'flood'::dimension, 74, 1.0, 'measured'::source_tier),
              (?, 'schools'::dimension, 100, 1.0, 'modeled'::source_tier),
              (?, 'worship'::dimension, 100, 1.0, 'modeled'::source_tier),
              (?, 'air_quality'::dimension, 79, 0.8, 'modeled'::source_tier)
            """, CELL, CELL, CELL, CELL);
        jdbc.update("DELETE FROM ingest.property WHERE id = ?", PROP_ID);
        jdbc.update("""
            INSERT INTO ingest.property
              (id, source_id, title, tenure, property_type, price, price_currency, status, cell_h3, address)
            VALUES (?, 1, 'Lekki Phase 1', 'sale'::tenure, 'apartment'::property_type,
                    80000000, 'NGN', 'active', ?, 'Lekki, Lagos')
            """, PROP_ID, CELL);
    }

    @Test
    void getPropertyReturnsLayers() throws Exception {
        mvc.perform(get("/api/v1/properties/{id}", PROP_ID))
           .andExpect(status().isOk());
    }

    @Test
    void fitReturnsRealScore() throws Exception {
        String body = """
            {"weights":[
               {"dimension":"flood","weight":0.30},{"dimension":"schools","weight":0.25},
               {"dimension":"affordability","weight":0.20},{"dimension":"worship","weight":0.15},
               {"dimension":"air_quality","weight":0.10}],
             "budget":{"amount":100000000,"currency":"NGN"}}
            """;
        var result = mvc.perform(post("/api/v1/properties/{id}/fit", PROP_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        assertThat(node.get("score").asInt()).isBetween(80, 95);
        assertThat(node.get("breakdown")).isNotEmpty();
    }
}

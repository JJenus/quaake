package com.homefit.api.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Repository integration test against a real PostGIS instance.
 * Flyway applies all migrations; no H2.
 *
 * <p>Uses a PostGIS Testcontainer when Docker is available (CI).
 * Falls back to a pre-existing local PostgreSQL+PostGIS instance
 * (localhost:5432/homefit) when Docker pull is not possible.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReadModelRepositoryTest {

    // -----------------------------------------------------------------------
    // Container bootstrap — static so the URL is resolved before Spring loads
    // -----------------------------------------------------------------------

    /** Non-null only when Testcontainers/Docker is available. */
    private static final PostgreSQLContainer<?> TC_CONTAINER;
    private static final String DB_URL;
    private static final String DB_USER = "homefit";
    private static final String DB_PASS = "homefit";

    static {
        PostgreSQLContainer<?> container = null;
        String url;
        try {
            container = new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:16-3.4")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("homefit")
                    .withUsername(DB_USER)
                    .withPassword(DB_PASS);
            container.start();
            // stringtype=unspecified: lets PG resolve enum-typed columns in WHERE clauses
            url = container.getJdbcUrl() + "?stringtype=unspecified";
        } catch (Exception e) {
            // Docker unavailable (socket missing, image pull blocked, etc.)
            // fall back to the local PostGIS instance used in development
            container = null;
            url = "jdbc:postgresql://localhost:5432/homefit?stringtype=unspecified";
        }
        TC_CONTAINER = container;
        DB_URL = url;
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> DB_URL);
        r.add("spring.datasource.username", () -> DB_USER);
        r.add("spring.datasource.password", () -> DB_PASS);
        // Expose ingest schema so seed SQL can reference enum types without schema-qualification
        r.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO ingest, public, app");
    }

    // -----------------------------------------------------------------------
    // Test data
    // -----------------------------------------------------------------------

    static final long CELL = 617700440803966975L;  // arbitrary H3 index in decimal
    static final UUID PROP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired CellSubscoreRepository cellSubscoreRepo;
    @Autowired PropertyRepository propertyRepo;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        // data_source is FK-required by ingest.property
        jdbc.execute("""
                INSERT INTO ingest.data_source (id, code, name, group_tier, default_tier)
                VALUES (1, 'test_src', 'Test Source', 'test', 'modeled'::source_tier)
                ON CONFLICT (id) DO NOTHING
                """);

        jdbc.execute("TRUNCATE ingest.cell_subscore");

        jdbc.execute("""
                INSERT INTO ingest.cell_subscore (cell_h3, dimension, subscore, confidence, source_tier)
                VALUES
                  (%1$d, 'flood'::dimension,   80, 0.90, 'measured'::source_tier),
                  (%1$d, 'schools'::dimension, 75, 0.80, 'modeled'::source_tier),
                  (%1$d, 'safety'::dimension,  90, 0.95, 'measured'::source_tier)
                """.formatted(CELL));

        jdbc.execute("DELETE FROM ingest.property WHERE id = '%s'".formatted(PROP_ID));
        jdbc.execute("""
                INSERT INTO ingest.property
                  (id, source_id, title, tenure, price_currency, status, bedrooms, bathrooms, address)
                VALUES
                  ('%s'::uuid, 1, 'Test Villa', 'sale'::tenure, 'NGN', 'active', 3, 2, 'Lagos Island')
                """.formatted(PROP_ID));
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void cellSubscoresAreFetchedByH3() {
        var scores = cellSubscoreRepo.findById_CellH3(CELL);

        assertThat(scores).hasSize(3);
        assertThat(scores)
                .extracting(CellSubscore::getDimension)
                .containsExactlyInAnyOrder("flood", "schools", "safety");
        assertThat(scores)
                .filteredOn(s -> "flood".equals(s.getDimension()))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.getSubscore()).isEqualTo((short) 80);
                    assertThat(s.getConfidence()).isEqualTo(0.90f, within(0.001f));
                    assertThat(s.getSourceTier()).isEqualTo("measured");
                });
    }

    @Test
    void propertyIsFoundById() {
        var prop = propertyRepo.findById(PROP_ID);

        assertThat(prop).isPresent();
        assertThat(prop.get().getTitle()).isEqualTo("Test Villa");
        assertThat(prop.get().getTenure()).isEqualTo("sale");
        assertThat(prop.get().getBedrooms()).isEqualTo((short) 3);
        assertThat(prop.get().getAddress()).isEqualTo("Lagos Island");
        assertThat(prop.get().getStatus()).isEqualTo("active");
    }

    @Test
    void propertyRepositoryExposesFindByIdOnly() {
        // PropertyRepository extends bare Repository — confirm no write surface on the interface
        var methods = java.util.Arrays.stream(PropertyRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();
        assertThat(methods).doesNotContain("save", "delete", "deleteById", "saveAll");
        assertThat(methods).contains("findById");
    }
}

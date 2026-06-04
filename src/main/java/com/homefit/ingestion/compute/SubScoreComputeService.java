package com.homefit.ingestion.compute;

import com.homefit.core.domain.AmenityType;
import com.homefit.core.domain.Dimension;
import com.homefit.core.scoring.normalize.FloodNormalizer;
import com.homefit.core.scoring.normalize.ProximityNormalizer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Step 3 of the slice: read derived data (cell_proximity, cell_flood_summary), run the core
 * normalizers, and upsert {@code ingest.cell_subscore}; then refresh the cell_profile view.
 */
@Service
public class SubScoreComputeService {

    private final JdbcTemplate jdbc;
    private final ProximityNormalizer proximity;
    private final FloodNormalizer flood;

    public SubScoreComputeService(JdbcTemplate jdbc, ProximityNormalizer proximity, FloodNormalizer flood) {
        this.jdbc = jdbc;
        this.proximity = proximity;
        this.flood = flood;
    }

    /** Recompute sub-scores for the given cells. Returns the number of sub-score rows written. */
    @Transactional
    public int recompute(List<Long> cells) {
        int written = 0;
        for (long cell : cells) {
            written += computeProximityDimensions(cell);
            written += computeFlood(cell);
        }
        refreshProfile();
        return written;
    }

    private int computeProximityDimensions(long cell) {
        // best (closest) travel minutes per dimension across contributing amenity types
        Map<Dimension, Double> bestMinutes = new EnumMap<>(Dimension.class);
        jdbc.query("SELECT amenity_type, travel_minutes FROM ingest.cell_proximity WHERE cell_h3 = ?",
                rs -> {
                    AmenityType at = AmenityType.fromToken(rs.getString("amenity_type"));
                    at.dimension().ifPresent(dim -> {
                        double m = rs.getDouble("travel_minutes");
                        bestMinutes.merge(dim, m, Math::min);
                    });
                }, cell);

        int written = 0;
        for (var e : bestMinutes.entrySet()) {
            int score = proximity.score(e.getKey(), e.getValue());
            upsert(cell, e.getKey().token(), score, 0.85, "modeled",
                    "{\"travelMinutes\":" + e.getValue() + "}");
            written++;
        }
        return written;
    }

    private int computeFlood(long cell) {
        var freqs = jdbc.query(
                "SELECT annual_freq FROM ingest.cell_flood_summary WHERE cell_h3 = ?",
                (rs, rowNum) -> rs.getDouble("annual_freq"), cell);
        if (freqs.isEmpty()) return 0;
        int score = flood.score(freqs.get(0));
        upsert(cell, Dimension.FLOOD.token(), score, 0.90, "measured",
                "{\"annualFreq\":" + freqs.get(0) + "}");
        return 1;
    }

    private void upsert(long cell, String dimension, int subscore, double confidence, String tier, String inputs) {
        jdbc.update("""
            INSERT INTO ingest.cell_subscore (cell_h3, dimension, subscore, confidence, source_tier, inputs)
            VALUES (?, CAST(? AS dimension), ?, ?, CAST(? AS source_tier), CAST(? AS jsonb))
            ON CONFLICT (cell_h3, dimension) DO UPDATE SET
              subscore = EXCLUDED.subscore, confidence = EXCLUDED.confidence,
              source_tier = EXCLUDED.source_tier, inputs = EXCLUDED.inputs, computed_at = now()
            """, cell, dimension, subscore, confidence, tier, inputs);
    }

    private void refreshProfile() {
        // first refresh cannot be CONCURRENT (view created WITH NO DATA); plain refresh is safe here.
        jdbc.execute("REFRESH MATERIALIZED VIEW ingest.cell_profile");
    }
}

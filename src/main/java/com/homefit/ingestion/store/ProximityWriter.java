package com.homefit.ingestion.store;

import com.homefit.ingestion.model.ProximityRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Upserts computed proximity into {@code ingest.cell_proximity}. */
@Component
public class ProximityWriter {

    private final JdbcTemplate jdbc;

    public ProximityWriter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void upsert(List<ProximityRow> rows) {
        String sql = """
            INSERT INTO ingest.cell_proximity
              (cell_h3, amenity_type, nearest_amenity_id, travel_minutes, travel_mode,
               distance_m, count_within_radius, radius_m)
            VALUES (?, CAST(? AS amenity_type), ?, ?, CAST(? AS travel_mode), ?, ?, ?)
            ON CONFLICT (cell_h3, amenity_type) DO UPDATE SET
              nearest_amenity_id = EXCLUDED.nearest_amenity_id,
              travel_minutes = EXCLUDED.travel_minutes,
              travel_mode = EXCLUDED.travel_mode,
              distance_m = EXCLUDED.distance_m,
              count_within_radius = EXCLUDED.count_within_radius,
              radius_m = EXCLUDED.radius_m,
              computed_at = now()
            """;
        for (ProximityRow r : rows) {
            jdbc.update(sql, r.cellH3(), r.type().token(), r.nearestAmenityId(),
                    r.travelMinutes(), r.travelMode(), r.distanceM(),
                    r.countWithinRadius(), r.radiusM());
        }
    }
}

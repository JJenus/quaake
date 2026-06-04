package com.homefit.ingestion.store;

import com.homefit.core.geo.H3Support;
import com.homefit.ingestion.model.MappedAmenity;
import com.homefit.ingestion.model.PersistedAmenity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Upserts amenities into {@code ingest.amenity} with a PostGIS point + H3 cell, returning their ids.
 * Uses native SQL (geometry) — JPA is reserved for the read side.
 */
@Component
public class AmenityWriter {

    private final JdbcTemplate jdbc;
    private final H3Support h3;

    public AmenityWriter(JdbcTemplate jdbc, H3Support h3) {
        this.jdbc = jdbc;
        this.h3 = h3;
    }

    public List<PersistedAmenity> upsert(List<MappedAmenity> amenities, int sourceId) {
        List<PersistedAmenity> out = new ArrayList<>();
        String sql = """
            INSERT INTO ingest.amenity
              (osm_type, osm_id, amenity_type, subtype, name, geom, cell_h3, source_id, tags)
            VALUES (?, ?, CAST(? AS amenity_type), ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, CAST(? AS jsonb))
            ON CONFLICT (osm_type, osm_id, amenity_type)
              DO UPDATE SET name = EXCLUDED.name, subtype = EXCLUDED.subtype,
                            geom = EXCLUDED.geom, cell_h3 = EXCLUDED.cell_h3
            RETURNING id
            """;
        for (MappedAmenity a : amenities) {
            long cell = h3.cellFor(a.lat(), a.lng());
            String osmType = a.osmType() == null ? "n" : a.osmType().substring(0, 1);
            Long id = jdbc.queryForObject(sql, Long.class,
                    osmType, a.osmId(), a.type().token(), a.subtype(), a.name(),
                    a.lng(), a.lat(), cell, sourceId, toJson(a.tags()));
            out.add(new PersistedAmenity(id, a.type(), a.lat(), a.lng(), cell));
        }
        return out;
    }

    private static String toJson(java.util.Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : tags.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(esc(e.getKey())).append('"').append(':')
              .append('"').append(esc(e.getValue())).append('"');
            first = false;
        }
        return sb.append('}').toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

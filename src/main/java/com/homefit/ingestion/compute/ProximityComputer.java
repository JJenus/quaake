package com.homefit.ingestion.compute;

import com.homefit.core.domain.AmenityType;
import com.homefit.core.geo.DistanceUtils;
import com.homefit.core.geo.H3Support;
import com.homefit.ingestion.model.PersistedAmenity;
import com.homefit.ingestion.model.ProximityRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * For each cell, finds the nearest amenity of each type and estimates travel time.
 * Pure given an {@link H3Support} for centroids — unit-tested without DB or network.
 */
@Component
public class ProximityComputer {

    private static final double WALK_M_PER_MIN = 80.0;   // ~4.8 km/h
    private static final double DRIVE_M_PER_MIN = 450.0; // ~27 km/h urban
    private static final int RADIUS_M = 2000;

    private final H3Support h3;

    public ProximityComputer(H3Support h3) { this.h3 = h3; }

    public List<ProximityRow> compute(List<Long> cells, List<PersistedAmenity> amenities) {
        // group amenities by type once
        Map<AmenityType, List<PersistedAmenity>> byType = new EnumMap<>(AmenityType.class);
        for (PersistedAmenity a : amenities) byType.computeIfAbsent(a.type(), k -> new ArrayList<>()).add(a);

        List<ProximityRow> rows = new ArrayList<>();
        for (long cell : cells) {
            double[] c = h3.centroid(cell);
            for (var entry : byType.entrySet()) {
                AmenityType type = entry.getKey();
                PersistedAmenity nearest = null;
                double nearestM = Double.MAX_VALUE;
                int countWithin = 0;
                for (PersistedAmenity a : entry.getValue()) {
                    double d = DistanceUtils.haversineMeters(c[0], c[1], a.lat(), a.lng());
                    if (d < nearestM) { nearestM = d; nearest = a; }
                    if (d <= RADIUS_M) countWithin++;
                }
                if (nearest == null) continue;
                String mode = (type == AmenityType.HOSPITAL || type == AmenityType.CLINIC) ? "drive" : "walk";
                double mpm = mode.equals("drive") ? DRIVE_M_PER_MIN : WALK_M_PER_MIN;
                double minutes = nearestM / mpm;
                rows.add(new ProximityRow(cell, type, nearest.id(),
                        round1(minutes), mode, (int) Math.round(nearestM), countWithin, RADIUS_M));
            }
        }
        return rows;
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}

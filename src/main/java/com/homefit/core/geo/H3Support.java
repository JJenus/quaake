package com.homefit.core.geo;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Thin wrapper over h3-java: the H3 cell anchors every observation, amenity, and property. */
@Component
public class H3Support {

    /** Default working resolution for the slice (~0.7 km edge). */
    public static final int DEFAULT_RES = 8;

    private final H3Core h3;

    public H3Support() {
        try {
            this.h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize H3", e);
        }
    }

    public long cellFor(double lat, double lng, int resolution) {
        return h3.latLngToCell(lat, lng, resolution);
    }

    public long cellFor(double lat, double lng) {
        return cellFor(lat, lng, DEFAULT_RES);
    }

    public String toAddress(long cell) {
        return h3.h3ToString(cell);
    }

    /** Centroid of a cell as [lat, lng]. */
    public double[] centroid(long cell) {
        LatLng c = h3.cellToLatLng(cell);
        return new double[]{c.lat, c.lng};
    }

    /** All cells whose centroid falls within the axis-aligned bounding box, at {@code resolution}. */
    public List<Long> cellsInBBox(double minLat, double minLng, double maxLat, double maxLng, int resolution) {
        List<LatLng> ring = new ArrayList<>();
        ring.add(new LatLng(minLat, minLng));
        ring.add(new LatLng(minLat, maxLng));
        ring.add(new LatLng(maxLat, maxLng));
        ring.add(new LatLng(maxLat, minLng));
        return h3.polygonToCells(ring, null, resolution);
    }
}

package com.homefit.core.geo;

import com.uber.h3core.H3Core;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Thin wrapper over h3-java: map lat/lng to the H3 cell that anchors every observation. */
@Component
public class H3Support {

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

    public String toAddress(long cell) {
        return h3.h3ToString(cell);
    }
}

package com.homefit.ingestion;

import com.homefit.core.domain.AmenityType;
import com.homefit.core.geo.H3Support;
import com.homefit.ingestion.compute.ProximityComputer;
import com.homefit.ingestion.model.PersistedAmenity;
import com.homefit.ingestion.model.ProximityRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Nearest-amenity + travel-mode logic, using real H3 centroids (no DB, no network). */
class ProximityComputerTest {

    private final H3Support h3 = new H3Support();
    private final ProximityComputer computer = new ProximityComputer(h3);

    @Test
    void picksNearestAndUsesWalkForSchoolDriveForHospital() {
        double lat = 6.45, lng = 3.47;
        long cell = h3.cellFor(lat, lng);
        double[] c = h3.centroid(cell);

        // a school ~at the centroid, another far away; a hospital nearby
        var near = new PersistedAmenity(1, AmenityType.SCHOOL, c[0], c[1], cell);
        var far  = new PersistedAmenity(2, AmenityType.SCHOOL, c[0] + 0.05, c[1] + 0.05, cell);
        var hosp = new PersistedAmenity(3, AmenityType.HOSPITAL, c[0] + 0.001, c[1], cell);

        List<ProximityRow> rows = computer.compute(List.of(cell), List.of(near, far, hosp));

        ProximityRow school = rows.stream().filter(r -> r.type() == AmenityType.SCHOOL).findFirst().orElseThrow();
        assertThat(school.nearestAmenityId()).isEqualTo(1L);   // the near one
        assertThat(school.travelMode()).isEqualTo("walk");
        assertThat(school.travelMinutes()).isLessThan(5.0);

        ProximityRow hospital = rows.stream().filter(r -> r.type() == AmenityType.HOSPITAL).findFirst().orElseThrow();
        assertThat(hospital.travelMode()).isEqualTo("drive");
    }
}

package com.homefit.api.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Read-only view of {@code ingest.cell_proximity} — powers the daily-life layer. */
@Entity
@Immutable
@Table(schema = "ingest", name = "cell_proximity")
public class CellProximity {

    @EmbeddedId
    private CellProximityId id;
    @Column(name = "travel_minutes")
    private Float travelMinutes;
    @Column(name = "travel_mode")
    private String travelMode;
    @Column(name = "distance_m")
    private Integer distanceM;

    protected CellProximity() {}

    public CellProximityId getId() { return id; }
    public long getCellH3() { return id.getCellH3(); }
    public String getAmenityType() { return id.getAmenityType(); }
    public Float getTravelMinutes() { return travelMinutes; }
    public String getTravelMode() { return travelMode; }
    public Integer getDistanceM() { return distanceM; }
}

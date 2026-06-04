package com.homefit.api.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Read-only view of {@code ingest.cell_subscore}. */
@Entity
@Immutable
@Table(schema = "ingest", name = "cell_subscore")
public class CellSubscore {

    @EmbeddedId
    private CellSubscoreId id;
    private short subscore;
    private float confidence;
    @Column(name = "source_tier")
    private String sourceTier;

    protected CellSubscore() {}

    public CellSubscoreId getId() { return id; }
    public long getCellH3() { return id.getCellH3(); }
    public String getDimension() { return id.getDimension(); }
    public short getSubscore() { return subscore; }
    public float getConfidence() { return confidence; }
    public String getSourceTier() { return sourceTier; }
}

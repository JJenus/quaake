package com.homefit.api.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CellProximityId implements Serializable {
    @Column(name = "cell_h3")
    private Long cellH3;
    @Column(name = "amenity_type")
    private String amenityType;

    protected CellProximityId() {}
    public CellProximityId(Long cellH3, String amenityType) { this.cellH3 = cellH3; this.amenityType = amenityType; }
    public Long getCellH3() { return cellH3; }
    public String getAmenityType() { return amenityType; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CellProximityId that)) return false;
        return Objects.equals(cellH3, that.cellH3) && Objects.equals(amenityType, that.amenityType);
    }
    @Override public int hashCode() { return Objects.hash(cellH3, amenityType); }
}

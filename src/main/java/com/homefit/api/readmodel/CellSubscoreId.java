package com.homefit.api.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CellSubscoreId implements Serializable {

    @Column(name = "cell_h3")
    private Long cellH3;

    @Column(name = "dimension")
    private String dimension;

    protected CellSubscoreId() {}

    public CellSubscoreId(Long cellH3, String dimension) {
        this.cellH3 = cellH3;
        this.dimension = dimension;
    }

    public Long getCellH3() { return cellH3; }
    public String getDimension() { return dimension; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CellSubscoreId that)) return false;
        return Objects.equals(cellH3, that.cellH3) && Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() { return Objects.hash(cellH3, dimension); }
}

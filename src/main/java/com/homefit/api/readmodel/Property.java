package com.homefit.api.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only view of {@code ingest.property} — only the fields needed by GET /properties/{id}.
 * {@code tenure} and {@code property_type} are Postgres enum columns; they resolve without
 * a cast error when the datasource URL carries {@code stringtype=unspecified}.
 */
@Entity
@Immutable
@Table(schema = "ingest", name = "property")
public class Property {

    @Id
    private UUID id;

    private String title;

    /** Maps {@code tenure} Postgres enum as text. */
    private String tenure;

    @Column(name = "property_type")
    private String propertyType;

    private Short bedrooms;

    private Short bathrooms;

    @Column(name = "size_sqm")
    private BigDecimal sizeSqm;

    private BigDecimal price;

    @Column(name = "price_currency")
    private String priceCurrency;

    @Column(name = "price_period")
    private String pricePeriod;

    private String address;

    @Column(name = "cell_h3")
    private Long cellH3;

    @Column(name = "admin_region_id")
    private Long adminRegionId;

    private String status;

    @Column(name = "listing_url")
    private String listingUrl;

    @Column(columnDefinition = "jsonb")
    private String photos;

    protected Property() {}

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getTenure() { return tenure; }
    public String getPropertyType() { return propertyType; }
    public Short getBedrooms() { return bedrooms; }
    public Short getBathrooms() { return bathrooms; }
    public BigDecimal getSizeSqm() { return sizeSqm; }
    public BigDecimal getPrice() { return price; }
    public String getPriceCurrency() { return priceCurrency; }
    public String getPricePeriod() { return pricePeriod; }
    public String getAddress() { return address; }
    public Long getCellH3() { return cellH3; }
    public Long getAdminRegionId() { return adminRegionId; }
    public String getStatus() { return status; }
    public String getListingUrl() { return listingUrl; }
    public String getPhotos() { return photos; }
}

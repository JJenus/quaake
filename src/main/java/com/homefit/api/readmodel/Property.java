package com.homefit.api.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

/** Read-only view of {@code ingest.property} — the fields the API serves. Postgres enums map as text. */
@Entity
@Immutable
@Table(schema = "ingest", name = "property")
public class Property {

    @Id
    private UUID id;
    private String title;
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
    private String address;
    @Column(name = "cell_h3")
    private Long cellH3;
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
    public String getAddress() { return address; }
    public Long getCellH3() { return cellH3; }
    public String getStatus() { return status; }
    public String getListingUrl() { return listingUrl; }
    public String getPhotos() { return photos; }
}

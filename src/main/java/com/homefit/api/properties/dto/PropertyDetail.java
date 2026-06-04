package com.homefit.api.properties.dto;

import java.math.BigDecimal;
import java.util.List;

/** Universal (non-personalized) property view — see api-surface.md §5. */
public record PropertyDetail(String id, String title, String areaLabel, Money price,
                             String propertyType, Short bedrooms, Short bathrooms, BigDecimal sizeSqm,
                             List<String> photos, String cellH3, List<Layer> layers) {}

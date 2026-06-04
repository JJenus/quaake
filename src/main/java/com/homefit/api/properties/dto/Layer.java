package com.homefit.api.properties.dto;

import java.util.List;

public record Layer(String layer, String label, Integer layerScore, Double confidence, List<LayerItem> items) {}

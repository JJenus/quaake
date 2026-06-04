package com.homefit.api.properties;

import com.homefit.api.properties.dto.*;
import com.homefit.api.readmodel.*;
import com.homefit.core.domain.AmenityType;
import org.springframework.stereotype.Service;

import java.util.*;

/** Assembles the layered, universal property view from the read model (no user weighting). */
@Service
public class PropertyProfileService {

    private final PropertyRepository properties;
    private final CellSubscoreRepository subscores;
    private final CellProximityRepository proximity;

    public PropertyProfileService(PropertyRepository properties, CellSubscoreRepository subscores,
                                  CellProximityRepository proximity) {
        this.properties = properties;
        this.subscores = subscores;
        this.proximity = proximity;
    }

    public Optional<PropertyDetail> findDetail(UUID id) {
        return properties.findById(id).map(this::toDetail);
    }

    private PropertyDetail toDetail(Property p) {
        Map<String, CellSubscore> subByDim = new HashMap<>();
        if (p.getCellH3() != null)
            for (CellSubscore s : subscores.findById_CellH3(p.getCellH3())) subByDim.put(s.getDimension(), s);

        List<CellProximity> prox = p.getCellH3() == null ? List.of() : proximity.findById_CellH3(p.getCellH3());

        List<Layer> layers = new ArrayList<>();
        layers.add(layer("risk_environment", "Risk & environment",
                subItems(subByDim, "flood", "Flood safety"),
                subItems(subByDim, "air_quality", "Air quality"),
                subItems(subByDim, "other_hazards", "Other hazards")));
        layers.add(dailyLifeLayer(prox, subByDim));
        layers.add(layer("livability", "Livability",
                subItems(subByDim, "safety", "Safety")));
        layers.add(new Layer("cost", "Cost", null, null,
                List.of(new LayerItem("affordability", "Affordability", null, null, null, null, null))));

        return new PropertyDetail(
                p.getId().toString(), p.getTitle(),
                p.getAddress() == null ? "Lagos, Nigeria" : p.getAddress(),
                new Money(p.getPrice(), p.getPriceCurrency()),
                p.getPropertyType(), p.getBedrooms(), p.getBathrooms(), p.getSizeSqm(),
                List.of(), p.getCellH3() == null ? null : Long.toString(p.getCellH3()), layers);
    }

    private List<LayerItem> subItems(Map<String, CellSubscore> byDim, String dim, String label) {
        CellSubscore s = byDim.get(dim);
        if (s == null) return List.of();
        return List.of(new LayerItem(dim, label, (int) s.getSubscore(), (double) s.getConfidence(),
                s.getSourceTier(), null, null));
    }

    private Layer dailyLifeLayer(List<CellProximity> prox, Map<String, CellSubscore> byDim) {
        List<LayerItem> items = new ArrayList<>();
        for (CellProximity cp : prox) {
            AmenityType at = AmenityType.fromToken(cp.getAmenityType());
            Integer sub = at.dimension().map(d -> byDim.get(d.token()))
                    .map(s -> (int) s.getSubscore()).orElse(null);
            items.add(new LayerItem(cp.getAmenityType(), labelFor(at),
                    sub, null, null,
                    cp.getTravelMinutes() == null ? null : cp.getTravelMinutes().doubleValue(),
                    cp.getTravelMode()));
        }
        return buildLayer("daily_life", "Daily life & proximity", items);
    }

    private Layer layer(String key, String label, List<LayerItem>... itemLists) {
        List<LayerItem> items = new ArrayList<>();
        for (List<LayerItem> l : itemLists) items.addAll(l);
        return buildLayer(key, label, items);
    }

    private Layer buildLayer(String key, String label, List<LayerItem> items) {
        var scored = items.stream().map(LayerItem::subScore).filter(Objects::nonNull).toList();
        Integer layerScore = scored.isEmpty() ? null
                : (int) Math.round(scored.stream().mapToInt(Integer::intValue).average().orElse(0));
        var confs = items.stream().map(LayerItem::confidence).filter(Objects::nonNull).toList();
        Double conf = confs.isEmpty() ? null : confs.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        return new Layer(key, label, layerScore, conf, items);
    }

    private String labelFor(AmenityType t) {
        return switch (t) {
            case SCHOOL -> "Primary school";
            case PLACE_OF_WORSHIP -> "Worship center";
            case MARKET, GROCERY -> "Market";
            case HOSPITAL, CLINIC -> "Hospital";
            case PHARMACY -> "Pharmacy";
            case TRANSIT_STOP -> "Transit";
            case PARK -> "Park";
            default -> t.token();
        };
    }
}

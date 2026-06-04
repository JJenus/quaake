package com.homefit.ingestion.map;

import com.homefit.core.domain.AmenityType;
import com.homefit.ingestion.model.MappedAmenity;
import com.homefit.ingestion.model.OsmElement;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Classifies an OSM element into one of our {@link AmenityType}s from its tags.
 * Pure and deterministic — unit-tested without network or DB.
 */
@Component
public class OsmAmenityMapper {

    public Optional<MappedAmenity> map(OsmElement e) {
        var tags = e.tags();
        if (tags == null || tags.isEmpty()) return Optional.empty();

        AmenityType type = classify(tags);
        if (type == null) return Optional.empty();

        String subtype = switch (type) {
            case PLACE_OF_WORSHIP -> tags.get("religion");
            case SCHOOL -> tags.getOrDefault("amenity", tags.get("isced:level"));
            default -> null;
        };
        return Optional.of(new MappedAmenity(
                e.type(), e.id(), type, subtype, tags.get("name"), e.lat(), e.lon(), tags));
    }

    private AmenityType classify(java.util.Map<String, String> t) {
        String amenity = t.get("amenity");
        String shop = t.get("shop");
        String leisure = t.get("leisure");
        String railway = t.get("railway");
        String pt = t.get("public_transport");
        String highway = t.get("highway");

        if (amenity != null) {
            switch (amenity) {
                case "school", "kindergarten", "college", "university" -> { return AmenityType.SCHOOL; }
                case "hospital" -> { return AmenityType.HOSPITAL; }
                case "clinic", "doctors" -> { return AmenityType.CLINIC; }
                case "pharmacy" -> { return AmenityType.PHARMACY; }
                case "marketplace" -> { return AmenityType.MARKET; }
                case "place_of_worship" -> { return AmenityType.PLACE_OF_WORSHIP; }
                default -> { /* fall through */ }
            }
        }
        if (shop != null) {
            if (shop.equals("supermarket") || shop.equals("grocery") || shop.equals("convenience"))
                return AmenityType.GROCERY;
        }
        if ("park".equals(leisure)) return AmenityType.PARK;
        if ("station".equals(railway) || "platform".equals(pt) || "station".equals(pt)
                || "bus_stop".equals(highway)) return AmenityType.TRANSIT_STOP;
        return null;
    }
}

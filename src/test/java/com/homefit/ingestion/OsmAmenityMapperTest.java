package com.homefit.ingestion;

import com.homefit.core.domain.AmenityType;
import com.homefit.ingestion.map.OsmAmenityMapper;
import com.homefit.ingestion.model.OsmElement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for OSM tag -> amenity classification (no network, no DB). */
class OsmAmenityMapperTest {

    private final OsmAmenityMapper mapper = new OsmAmenityMapper();

    private OsmElement node(Map<String, String> tags) {
        return new OsmElement("node", 1L, 6.45, 3.47, tags);
    }

    @Test
    void classifiesSchool() {
        var m = mapper.map(node(Map.of("amenity", "school", "name", "Lekki British")));
        assertThat(m).isPresent();
        assertThat(m.get().type()).isEqualTo(AmenityType.SCHOOL);
        assertThat(m.get().name()).isEqualTo("Lekki British");
    }

    @Test
    void classifiesMosqueWithReligionSubtype() {
        var m = mapper.map(node(Map.of("amenity", "place_of_worship", "religion", "muslim")));
        assertThat(m).isPresent();
        assertThat(m.get().type()).isEqualTo(AmenityType.PLACE_OF_WORSHIP);
        assertThat(m.get().subtype()).isEqualTo("muslim");
    }

    @Test
    void classifiesSupermarketAsGrocery() {
        assertThat(mapper.map(node(Map.of("shop", "supermarket"))).map(a -> a.type()))
                .contains(AmenityType.GROCERY);
    }

    @Test
    void classifiesHospitalAndBusStop() {
        assertThat(mapper.map(node(Map.of("amenity", "hospital"))).map(a -> a.type()))
                .contains(AmenityType.HOSPITAL);
        assertThat(mapper.map(node(Map.of("highway", "bus_stop"))).map(a -> a.type()))
                .contains(AmenityType.TRANSIT_STOP);
    }

    @Test
    void ignoresUnrelatedAndEmptyTags() {
        assertThat(mapper.map(node(Map.of("building", "yes")))).isEqualTo(Optional.empty());
        assertThat(mapper.map(node(Map.of()))).isEqualTo(Optional.empty());
    }
}

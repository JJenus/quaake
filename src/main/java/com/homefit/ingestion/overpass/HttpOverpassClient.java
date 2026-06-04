package com.homefit.ingestion.overpass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefit.ingestion.model.BBox;
import com.homefit.ingestion.model.OsmElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Overpass QL over HTTP (POST data=...). Requests {@code out center} so ways/relations carry a point. */
@Component
public class HttpOverpassClient implements OverpassClient {

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpOverpassClient(@Value("${homefit.ingestion.osm.overpass-url:https://overpass-api.de/api/interpreter}") String url) {
        this.http = RestClient.builder().baseUrl(url).build();
    }

    @Override
    public List<OsmElement> fetchAmenities(BBox b) {
        String bbox = b.minLat() + "," + b.minLng() + "," + b.maxLat() + "," + b.maxLng();
        String q = """
            [out:json][timeout:60];
            (
              node["amenity"~"school|kindergarten|college|university|hospital|clinic|doctors|pharmacy|marketplace|place_of_worship"](%1$s);
              way["amenity"~"school|hospital|clinic|marketplace|place_of_worship"](%1$s);
              node["shop"~"supermarket|grocery|convenience"](%1$s);
              node["leisure"="park"](%1$s);
              way["leisure"="park"](%1$s);
              node["highway"="bus_stop"](%1$s);
              node["public_transport"](%1$s);
            );
            out center;
            """.formatted(bbox);

        String body = http.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("data=" + q)
                .retrieve()
                .body(String.class);

        return parse(body);
    }

    List<OsmElement> parse(String json) {
        List<OsmElement> out = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(json);
            for (JsonNode el : root.path("elements")) {
                double lat, lon;
                if (el.has("lat")) { lat = el.get("lat").asDouble(); lon = el.get("lon").asDouble(); }
                else if (el.has("center")) { lat = el.get("center").get("lat").asDouble(); lon = el.get("center").get("lon").asDouble(); }
                else continue;
                Map<String, String> tags = new HashMap<>();
                JsonNode tagNode = el.path("tags");
                tagNode.fields().forEachRemaining(e -> tags.put(e.getKey(), e.getValue().asText()));
                out.add(new OsmElement(el.path("type").asText(), el.path("id").asLong(), lat, lon, tags));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Overpass response", e);
        }
        return out;
    }
}

package com.homefit.api.properties;

import com.homefit.api.properties.dto.PropertySummary;
import org.springframework.web.bind.annotation.*;

/**
 * Stub until the PostGIS read model (ingest.property + cell_subscore) is wired.
 * Returns a sample so the contract shape is exercisable end-to-end.
 */
@RestController
@RequestMapping("/api/v1/properties")
public class PropertyController {

    @GetMapping("/sample")
    public PropertySummary sample() {
        return new PropertySummary(
                "b3f10000-0000-0000-0000-000000000000",
                "Lekki Phase 1", "Lagos, Nigeria", "NGN 75M - 85M",
                6.4413, 3.4790, 83);
    }
}

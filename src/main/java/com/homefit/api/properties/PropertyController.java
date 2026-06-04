package com.homefit.api.properties;

import com.homefit.api.properties.dto.PropertyDetail;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** GET /api/v1/properties/{id} — universal, cacheable property view (no personalization). */
@RestController
@RequestMapping("/api/v1/properties")
public class PropertyController {

    private final PropertyProfileService service;

    public PropertyController(PropertyProfileService service) { this.service = service; }

    @GetMapping("/{id}")
    public PropertyDetail get(@PathVariable UUID id) {
        return service.findDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
    }
}

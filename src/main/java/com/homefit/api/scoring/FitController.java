package com.homefit.api.scoring;

import com.homefit.api.scoring.dto.FitResponse;
import com.homefit.api.scoring.dto.ScoringContextDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** POST /api/v1/properties/{id}/fit — the personalized score + breakdown. */
@RestController
@RequestMapping("/api/v1/properties")
public class FitController {

    private final FitService fitService;

    public FitController(FitService fitService) { this.fitService = fitService; }

    @PostMapping("/{id}/fit")
    public FitResponse fit(@PathVariable UUID id, @RequestBody ScoringContextDto ctx) {
        try {
            return fitService.fit(id, ctx)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }
}

package com.homefit.api.scoring.dto;

import java.util.List;

/** Inline scoring context (api-surface.md §1.1): the user's weights, budget, and deal-breakers. */
public record ScoringContextDto(List<WeightDto> weights, BudgetDto budget, List<DealBreakerDto> dealbreakers) {}

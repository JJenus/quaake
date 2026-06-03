package com.homefit.api.scoring.dto;

import java.util.List;

/**
 * Demonstration payload: a user's weights + a location's available sub-scores.
 * Mirrors how a real request will assemble scoringContext + cached cell sub-scores.
 */
public record DemoScoreRequest(List<WeightDto> weights, List<SubScoreDto> subScores) {}

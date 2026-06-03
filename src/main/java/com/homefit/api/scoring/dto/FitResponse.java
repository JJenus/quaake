package com.homefit.api.scoring.dto;

import java.util.List;

public record FitResponse(int score, double confidence, List<ContributionDto> breakdown) {}

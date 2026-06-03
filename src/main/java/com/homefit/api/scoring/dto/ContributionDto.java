package com.homefit.api.scoring.dto;

public record ContributionDto(String dimension, double weight, int subScore, double contribution) {}

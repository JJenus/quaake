package com.homefit.api.scoring.dto;

public record SubScoreDto(String dimension, int subScore, double confidence, String sourceTier) {}

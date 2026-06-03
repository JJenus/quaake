package com.homefit.core.domain;

import java.util.List;

/** The personalized result: overall score, confidence (covered weight), and per-dimension breakdown. */
public record FitResult(int score, double confidence, List<Contribution> breakdown) {}

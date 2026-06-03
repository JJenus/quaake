package com.homefit.core.domain;

/** One dimension's share of a Fit Score: normalized weight x sub-score. */
public record Contribution(Dimension dimension, double weight, int subScore, double contribution) {}

package com.homefit.core.domain;

import java.util.Map;

/** A hard filter applied before scoring. e.g. type="max_price", params={"amount":90000000}. */
public record DealBreaker(String type, Map<String, Object> params) {}

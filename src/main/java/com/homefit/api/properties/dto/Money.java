package com.homefit.api.properties.dto;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {}

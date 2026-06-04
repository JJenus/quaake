package com.homefit.api.scoring.dto;

import java.math.BigDecimal;

public record BudgetDto(BigDecimal amount, String currency) {}

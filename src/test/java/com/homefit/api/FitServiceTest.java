package com.homefit.api;

import com.homefit.api.readmodel.CellSubscore;
import com.homefit.api.readmodel.CellSubscoreRepository;
import com.homefit.api.readmodel.Property;
import com.homefit.api.readmodel.PropertyRepository;
import com.homefit.api.scoring.FitService;
import com.homefit.api.scoring.dto.*;
import com.homefit.core.scoring.aggregate.ConfidenceCalculator;
import com.homefit.core.scoring.aggregate.WeightedAggregator;
import com.homefit.core.scoring.normalize.PriceNormalizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Unit test of the request-time fit path with mocked read model + real core engine. */
class FitServiceTest {

    private final PropertyRepository propertyRepo = mock(PropertyRepository.class);
    private final CellSubscoreRepository subscoreRepo = mock(CellSubscoreRepository.class);
    private final FitService service = new FitService(
            propertyRepo, subscoreRepo,
            new WeightedAggregator(new ConfidenceCalculator()), new PriceNormalizer());

    private CellSubscore sub(String dim, int score) {
        CellSubscore s = mock(CellSubscore.class);
        when(s.getDimension()).thenReturn(dim);
        when(s.getSubscore()).thenReturn((short) score);
        when(s.getConfidence()).thenReturn(1.0f);
        when(s.getSourceTier()).thenReturn("measured");
        return s;
    }

    @Test
    void computesPersonalizedFitWithAffordability() {
        UUID id = UUID.randomUUID();
        long cell = 123456789L;

        Property p = mock(Property.class);
        when(p.getCellH3()).thenReturn(cell);
        when(p.getPrice()).thenReturn(new BigDecimal("80000000"));
        when(propertyRepo.findById(id)).thenReturn(java.util.Optional.of(p));
        when(subscoreRepo.findById_CellH3(cell))
                .thenReturn(List.of(sub("flood", 74), sub("schools", 100), sub("worship", 100), sub("air_quality", 79)));

        var ctx = new ScoringContextDto(
                List.of(new WeightDto("flood", 0.30), new WeightDto("schools", 0.25),
                        new WeightDto("affordability", 0.20), new WeightDto("worship", 0.15),
                        new WeightDto("air_quality", 0.10)),
                new BudgetDto(new BigDecimal("100000000"), "NGN"), null);

        var fit = service.fit(id, ctx).orElseThrow();

        assertThat(fit.score()).isBetween(80, 95);
        assertThat(fit.confidence()).isEqualTo(1.0);
        assertThat(fit.breakdown()).extracting(ContributionDto::dimension)
                .contains("affordability", "flood", "schools");
    }

    @Test
    void missingPropertyReturnsEmpty() {
        UUID id = UUID.randomUUID();
        when(propertyRepo.findById(id)).thenReturn(java.util.Optional.empty());
        assertThat(service.fit(id, new ScoringContextDto(List.of(new WeightDto("flood", 1.0)), null, null)))
                .isEmpty();
    }
}

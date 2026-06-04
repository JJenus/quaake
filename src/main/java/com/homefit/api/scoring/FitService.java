package com.homefit.api.scoring;

import com.homefit.api.readmodel.CellSubscore;
import com.homefit.api.readmodel.CellSubscoreRepository;
import com.homefit.api.readmodel.Property;
import com.homefit.api.readmodel.PropertyRepository;
import com.homefit.api.scoring.dto.*;
import com.homefit.core.domain.*;
import com.homefit.core.scoring.aggregate.WeightedAggregator;
import com.homefit.core.scoring.normalize.PriceNormalizer;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Step 5: the personalized Fit Score for one property. Reads the property's cell sub-scores,
 * computes affordability at request time, applies the user's weights via the core aggregator.
 */
@Service
public class FitService {

    private final PropertyRepository properties;
    private final CellSubscoreRepository subscores;
    private final WeightedAggregator aggregator;
    private final PriceNormalizer priceNormalizer;

    public FitService(PropertyRepository properties, CellSubscoreRepository subscores,
                      WeightedAggregator aggregator, PriceNormalizer priceNormalizer) {
        this.properties = properties;
        this.subscores = subscores;
        this.aggregator = aggregator;
        this.priceNormalizer = priceNormalizer;
    }

    public Optional<FitResponse> fit(UUID propertyId, ScoringContextDto ctx) {
        Optional<Property> maybe = properties.findById(propertyId);
        if (maybe.isEmpty()) return Optional.empty();
        Property p = maybe.get();

        Map<Dimension, DimensionScore> available = new EnumMap<>(Dimension.class);
        if (p.getCellH3() != null) {
            for (CellSubscore s : subscores.findById_CellH3(p.getCellH3())) {
                Dimension d = Dimension.fromToken(s.getDimension());
                available.put(d, new DimensionScore(d, s.getSubscore(), s.getConfidence(),
                        SourceTier.valueOf(s.getSourceTier().toUpperCase())));
            }
        }

        // affordability is request-time: needs the user's budget + the property's price
        if (ctx.budget() != null && ctx.budget().amount() != null && p.getPrice() != null) {
            int aff = priceNormalizer.score(p.getPrice().doubleValue(), ctx.budget().amount().doubleValue(), null);
            available.put(Dimension.AFFORDABILITY,
                    new DimensionScore(Dimension.AFFORDABILITY, aff, 1.0, SourceTier.MEASURED));
        }

        UserWeights weights = toWeights(ctx.weights());
        FitResult r = aggregator.aggregate(weights, available);

        List<ContributionDto> breakdown = r.breakdown().stream()
                .map(c -> new ContributionDto(c.dimension().token(), round3(c.weight()), c.subScore(), round1(c.contribution())))
                .toList();
        return Optional.of(new FitResponse(r.score(), round3(r.confidence()), breakdown));
    }

    private UserWeights toWeights(List<WeightDto> weights) {
        if (weights == null || weights.isEmpty())
            throw new IllegalArgumentException("At least one weight must be provided");
        Map<Dimension, Double> raw = new EnumMap<>(Dimension.class);
        for (WeightDto w : weights) raw.put(Dimension.fromToken(w.dimension()), w.weight());
        if (raw.values().stream().noneMatch(v -> v != null && v > 0))
            throw new IllegalArgumentException("At least one weight must be positive");
        return new UserWeights(raw);
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
}

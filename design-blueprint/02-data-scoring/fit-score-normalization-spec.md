# Fit Score — Sub-Score Normalization Specification

**Status:** Design spec (v1) · **Audience:** developers and AI agents implementing the scoring engine.
**Goal:** turn heterogeneous raw data (flood frequency, distance, price, AQI…) into comparable **0–100 sub-scores**, then combine them into a single personalized Fit Score.

This document is self-contained. Read top to bottom and you have everything needed to implement scoring without external context.

---

## 1. How this fits into the Fit Score

The Fit Score is a transparent weighted model:

```
FitScore = Σ (weightᵢ × subScoreᵢ)
```

- **weightᵢ** comes from the user's stated priorities and sums to 1.
- **subScoreᵢ** is what this document defines: a normalized 0–100 value per data layer.
- The output is always explainable — each sub-score and its contribution can be shown to the user.

This spec covers **only** the `raw data → subScore` step. Weighting, filtering, missing-data handling, and aggregation are summarized in §10–§12.

---

## 2. Universal conventions

1. **Range:** every sub-score is `0–100`.
2. **Direction:** higher always means **better fit**, regardless of the raw metric's direction. "Less is better" metrics (flood, distance, price) are inverted during normalization.
3. **Clamping:** results are always clamped to `[0, 100]`.
4. **Determinism:** same inputs → same score. No randomness.
5. **Confidence is separate:** a sub-score never encodes uncertainty. Missing/low-quality data is handled by the confidence mechanism (§11), not by lowering the score.

### 2.1 Core primitive — banded linear interpolation

Almost every layer uses one helper. Pass the value that should earn **100** as `xFull` and the value that should earn **0** as `xZero`. Direction is handled automatically by which bound is which (`xFull` may be greater *or* less than `xZero`).

```python
def band_score(x, x_full, x_zero):
    # Linear ramp from x_zero (=> 0) to x_full (=> 100), clamped.
    if x_full == x_zero:
        return 100.0 if x == x_full else 0.0
    t = (x - x_zero) / (x_full - x_zero)
    return max(0.0, min(1.0, t)) * 100.0
```

### 2.2 Core primitive — exponential decay

Used where impact tapers smoothly (e.g. flood frequency). `λ` controls steepness.

```python
import math
def decay_score(x, lam):
    # x >= 0; returns 100 at x=0, decaying toward 0.
    return 100.0 * math.exp(-lam * max(0.0, x))
```

---

## 3. Flood risk (less is better → inverted)

**Source priority:** (1) NASA LANCE reprocessed historical archive 2003–2025 → derive event frequency; (2) CHRR/CIESIN Global Flood Hazard Frequency class as fallback; (3) elevation/drainage (DEM) percentile as last resort. Always record which tier was used (affects confidence, §11).

### Tier 1 — Annualized flood frequency (preferred)

```
f = (number of distinct flood events at location) / (years observed)
subScore_flood = decay_score(f, λ = 3.0)
```

Reference points (λ = 3.0):

| Annual frequency `f` | Interpretation | Sub-score |
|---|---|---|
| 0.00 | No recorded floods | 100 |
| 0.10 | ~1 in 10 years | 74 |
| 0.20 | ~1 in 5 years | 55 |
| 0.33 | ~1 in 3 years | 37 |
| 0.50 | ~1 in 2 years | 22 |
| ≥1.00 | Most years | ≤5 |

### Tier 2 — Hazard frequency class (fallback)

CHRR class is `1`–`10` (higher = more frequent); a cell with no recorded flood is treated as class `0`.

```
subScore_flood = band_score(class, x_full = 0, x_zero = 10)
# class 0 → 100, class 5 → 50, class 10 → 0
```

### Tier 3 — Elevation percentile within region (last resort)

When no flood layer exists, approximate: low-lying land near water is riskier.

```
p = percentile rank of location elevation within its admin region   # 0..1, higher = higher ground
subScore_flood = band_score(p, x_full = 1.0, x_zero = 0.0)   # i.e. p * 100
```

> ⚠️ Tier 3 is a proxy, not a flood measurement. Flag it clearly and weight confidence down.

---

## 4. Proximity / distance to amenities (less is better → inverted)

Computed **per amenity type**, because "close enough" differs by need. Use **travel time** (walk or drive minutes via routing API) where available; fall back to road distance, then straight-line distance × 1.3 detour factor.

```
subScore_proximity = band_score(t, x_full = t_ideal, x_zero = t_max)
```

`t` = minutes to the **nearest** amenity of that type. Below `t_ideal` → 100; above `t_max` → 0; linear between.

### 4.1 Per-type thresholds

| Amenity type | Mode | `t_ideal` (100) | `t_max` (0) | Rationale |
|---|---|---|---|---|
| Daily market / grocery | walk | 5 min | 25 min | Frequent, ideally walkable |
| Primary school | walk | 10 min | 30 min | Daily school run |
| Worship center (mosque/church/temple) | walk | 5 min | 30 min | Often visited on foot, repeatedly |
| Pharmacy | walk | 5 min | 20 min | Convenience need |
| Hospital / clinic | drive | 10 min | 40 min | Tolerate distance; reachability matters |
| Public transit stop | walk | 5 min | 20 min | First/last-mile access |
| Park / green space | walk | 10 min | 30 min | Quality-of-life amenity |

Match the user's chosen worship type via OSM tags (`amenity=place_of_worship` + `religion=muslim` → mosque, `religion=christian` → church, etc.).

### 4.2 Optional density bonus

If the user values abundance (e.g. many schools), blend nearest-distance with count-in-radius:

```
density = count of that amenity within a fixed radius (e.g. 2 km)
densityScore = band_score(density, x_full = 5, x_zero = 0)   # 5+ nearby → full
subScore_proximity = 0.75 * nearestScore + 0.25 * densityScore
```

Default to nearest-only unless density is explicitly relevant.

---

## 5. Price / affordability (less is better → inverted, two parts)

Blend **fit-to-budget** (primary) with **local market value** (secondary).

### 5.1 Fit-to-user-budget

```
r = price / user_budget        # ratio of asking price to stated budget
```

| Ratio `r` | Meaning | Sub-score |
|---|---|---|
| `r ≤ 0.7` | Comfortably under budget | 100 |
| `0.7 < r ≤ 1.0` | Approaching budget | linear 100 → 60 |
| `1.0 < r ≤ 1.3` | Over budget | linear 60 → 0 |
| `r > 1.3` | Far over budget | 0 |

```python
def budget_score(price, budget):
    r = price / budget
    if r <= 0.7:  return 100.0
    if r <= 1.0:  return band_score(r, x_full=0.7, x_zero=1.0)  # 100→60 region
    if r <= 1.3:  return band_score(r, x_full=1.0, x_zero=1.3) * 0.6  # 60→0 region
    return 0.0
```
*(Note: scale the second band to land at 60 at r=1.0 and 0 at r=1.3.)*

### 5.2 Local market value (is it good value *here*?)

Score by percentile against **comparable listings in the same city/neighborhood** (similar type/size). Cheaper relative to local peers = better.

```
q = price percentile within comparable local set   # 0..1, higher = more expensive
marketScore = band_score(q, x_full = 0.0, x_zero = 1.0)   # (1 - q) * 100
```

### 5.3 Blend

```
subScore_price = 0.7 * budget_score + 0.3 * marketScore
```

> If no budget is set, use `marketScore` alone and note it.

---

## 6. Air quality (less is better → inverted)

Use PM2.5 (µg/m³) where available (OpenAQ ground stations; Sentinel-5P satellite fallback).

```
subScore_air = band_score(pm25, x_full = 5, x_zero = 150)
```

- `x_full = 5` µg/m³ ≈ WHO annual guideline → 100.
- `x_zero = 150` µg/m³ ≈ hazardous → 0.

Reference: 5 → 100, 35 → ~79, 75 → ~52, 110 → ~28, 150+ → 0.

**AQI alternative** (if only AQI is available): `subScore_air = band_score(aqi, x_full = 0, x_zero = 300)`.

---

## 7. Safety / crime (city/region-level only — low granularity)

Granularity is coarse; always pair with reduced confidence (§11).

- **If source is already a 0–100 safety index** (e.g. Numbeo safety index — higher = safer): use directly, `subScore_safety = index`.
- **If source is a crime/event rate** (e.g. ACLED event density, NBS crime rate): invert via percentile across regions:
  ```
  q = crime-rate percentile across comparable regions   # higher = more crime
  subScore_safety = band_score(q, x_full = 0.0, x_zero = 1.0)
  ```

Attach the data's geographic level (city/state) to the output so the UI never implies street-level precision.

---

## 8. Other natural hazards (composite, less is better)

For non-flood hazards (storm/wind, drought, seismic where relevant), compute a sub-score per available hazard using `band_score` against documented severity scales, then combine with a **worst-case (min) rule** — a location is only as safe as its highest risk:

```
subScore_hazards = min(subScore_hazard_1, subScore_hazard_2, …)
```

Use `min`, not average, so a single severe hazard isn't masked by mild ones.

---

## 9. Summary table of layers

| Layer | Direction | Method | `x_full` → `x_zero` (or λ) | Primary source |
|---|---|---|---|---|
| Flood | invert | decay / band | λ=3 · class 0→10 · elev pct | NASA LANCE → CHRR → DEM |
| Proximity (per type) | invert | band | per §4.1 | OSM + routing |
| Price | invert | blended | budget bands + market pct | listings / Estate Intel |
| Air quality | invert | band | PM2.5 5 → 150 | OpenAQ / Sentinel-5P |
| Safety | mixed | direct / pct | index, or rate pct | Numbeo / ACLED / NBS |
| Other hazards | invert | min of bands | per scale | SEDAC / regional |

---

## 10. Hard filters vs. soft weights

Keep separate. **Filters** are deal-breakers applied *before* scoring (e.g. `price ≤ budget × 1.1`, `hospital within 15 km`) and remove a property entirely. **Weights** only shade the score. Never convert a deal-breaker into a weight.

---

## 11. Missing data & confidence

Properties will have missing layers (common in Nigeria). Rules:

1. **Never** treat missing as `0` or as `100`.
2. **Drop** the missing dimension and **renormalize** remaining weights:
   ```
   available = dimensions with data
   w'ᵢ = wᵢ / Σ_{j in available} wⱼ
   FitScore = Σ_{i in available} w'ᵢ × subScoreᵢ
   ```
3. **Confidence** = total original weight that was actually covered:
   ```
   confidence = Σ_{i in available} wᵢ        # 0..1, show as %
   ```
   Also downgrade confidence when a sub-score used a fallback tier (e.g. flood Tier 3).
4. **Display** the score with its confidence, e.g. `"79 · based on 4 of 5 factors"`.

---

## 12. Worked end-to-end example

User weights: flood 0.30, schools 0.25, price 0.20, worship 0.15, air 0.10.

| Layer | Raw input | Normalized sub-score | Weight | Contribution |
|---|---|---|---|---|
| Flood | f = 0.10 /yr | decay(0.10, 3) ≈ 74 | 0.30 | 22.2 |
| Schools | 8 min walk | band(8, 10, 30) = 100 | 0.25 | 25.0 |
| Price | r = 0.95, q = 0.4 | 0.7·67 + 0.3·60 ≈ 65 | 0.20 | 13.0 |
| Worship | 4 min walk | band(4, 5, 30) = 100 | 0.15 | 15.0 |
| Air | PM2.5 = 35 | band(35, 5, 150) ≈ 79 | 0.10 | 7.9 |
| **FitScore** | | | | **≈ 83 / 100** · confidence 100% |

UI reveal: *"Scores high — flooding here is rare and both a school and a mosque are a short walk away; held back slightly by price and moderate air quality."*

---

## 13. Implementation & tuning notes

- **Store sub-scores, not just the total.** They power the radar chart, the "why" breakdown, and comparison view.
- **All thresholds in this doc are starting defaults.** Externalize them as config so they can be tuned per region (a 25-min walk to market means something different in Lagos vs. a rural area).
- **Precompute** sub-scores in the ingestion pipeline (PostGIS), keyed to geography. Apply user weights at request time — that's cheap and keeps the UI instant.
- **Round only for display.** Keep full precision internally so comparisons are stable.
- **Log the source tier** used per layer per property for auditability and confidence.
- **Regional overrides:** `min`/`max` bounds, λ, and per-type thresholds should be overridable by country/city as you scale beyond Nigeria.
```

# HomeFit Backend (skeleton)

Java 21 · Spring Boot 4.0.6 · Spring Modulith 2.0.6 · PostgreSQL/PostGIS · Maven.

A **modular monolith** — one Spring Boot app, three Spring Modulith modules:

```
com.homefit
├── core/        shared domain + scoring  (no web; depends on nothing)
│   ├── domain/      Dimension, SourceTier, DimensionScore, UserWeights, FitResult, ...
│   ├── scoring/     ScoringMath, ScoringThresholds
│   │   ├── normalize/   Flood/Proximity/Price/AirQuality/Safety/Hazard normalizers  [offline]
│   │   └── aggregate/   WeightedAggregator, HardFilterEvaluator, ConfidenceCalculator [request-time]
│   └── geo/         H3Support (h3-java), DistanceUtils
├── ingestion/   background pipeline (depends only on core)
│   ├── adapter/     SourceAdapter
│   └── pipeline/    SubScoreComputeJob (@Scheduled, profile "worker")
└── api/         request-time HTTP (depends only on core)
    ├── scoring/     ScoreController (/api/v1/score/demo — wired to the real engine)
    └── properties/  PropertyController (/api/v1/properties/sample — stub)
```

Boundaries are enforced by `ModularityTests` (`ApplicationModules.verify()`).

## Prerequisites
- JDK 21+, Maven 3.9+
- A PostGIS database. Quick start:

```bash
docker run --name homefit-db -e POSTGRES_DB=homefit \
  -e POSTGRES_USER=homefit -e POSTGRES_PASSWORD=homefit \
  -p 5432:5432 -d postgis/postgis:16-3.4
```

## Run
```bash
mvn spring-boot:run                                   # api (default)
mvn spring-boot:run -Dspring-boot.run.profiles=worker # scheduled ingestion

# verify boundaries + run unit tests
mvn test
```

Flyway applies `src/main/resources/db/migration/V1..V6` on startup, creating the
`ingest` and `app` schemas. Override DB connection with `DB_URL`/`DB_USER`/`DB_PASSWORD`.

## Try the scoring engine (the spec's worked example -> 83)
```bash
curl -s localhost:8080/api/v1/score/demo -H 'Content-Type: application/json' -d '{
  "weights":[
    {"dimension":"flood","weight":0.30},{"dimension":"schools","weight":0.25},
    {"dimension":"affordability","weight":0.20},{"dimension":"worship","weight":0.15},
    {"dimension":"air_quality","weight":0.10}],
  "subScores":[
    {"dimension":"flood","subScore":74,"confidence":1.0,"sourceTier":"measured"},
    {"dimension":"schools","subScore":100,"confidence":1.0,"sourceTier":"measured"},
    {"dimension":"affordability","subScore":65,"confidence":1.0,"sourceTier":"measured"},
    {"dimension":"worship","subScore":100,"confidence":1.0,"sourceTier":"measured"},
    {"dimension":"air_quality","subScore":79,"confidence":1.0,"sourceTier":"measured"}]
}'
# -> {"score":83,"confidence":1.0,"breakdown":[...]}
```

## What's real vs. stubbed
- **Real:** module structure + boundary test, all Flyway migrations (full schema), the scoring
  primitives + every normalizer + the weighted aggregator (verified by `ScoringSmokeTest`).
- **Stubbed / next:** JPA read-model entities over `ingest.*`, the production endpoints
  (`/properties/search`, `/properties/{id}`, `/fit`), the source adapters, and the
  sub-score compute job body.

## Next step — one vertical data slice
Ingest NASA POWER + a flood layer + OSM amenities for one Lagos region, aggregate to H3 cells,
run the normalizers, populate `ingest.cell_subscore`, then back the API endpoints with it.

See the design blueprint (`homefit-design-blueprint.zip`) for the full specs.

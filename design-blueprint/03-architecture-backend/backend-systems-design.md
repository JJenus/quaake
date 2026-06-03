# Backend Systems Design — Java / Spring Boot

**Status:** Design spec (v1) · **Audience:** backend engineers and architects.
**Companion docs:** *System Architecture*, *Fit Score Normalization Spec*, *Data Source Matrix*.

---

## Decisions recap

- **Single repository, modular monolith.** One codebase, clear internal module boundaries.
- **Java 21 + Spring Boot 3.x**, with **Spring Modulith** to enforce module boundaries.
- **Two logical halves:** background **ingestion** (sourcing + precompute) and request-time **api** (serving). They share **PostGIS** as the handoff — ingestion writes, api reads.
- **Shared `core`** holds the scoring/normalization logic so Fit Scores cannot drift between the two halves.
- **Deploy as one process now; split into two when ingestion load justifies it.** Structure for two from day one; defer the split.

---

## A note on the word "module"

Two distinct meanings — keep them separate in your head:

1. **Spring Modulith modules** — logical modules *inside one Spring Boot application*, used to enforce boundaries (`core`, `ingestion`, `api`). This is how we start.
2. **Maven (reactor) modules** — separately *buildable* artifacts under a parent POM. Maven confusingly also calls these "modules"; we only split into them in Phase 2, when we want two deployable jars.

We begin with one Spring Boot app (one Maven module) containing three Spring Modulith modules. The boundaries we enforce now are exactly what make the later Maven multi-module split mechanical instead of risky.

---

## Module breakdown — what actually lives in each

Base package: `com.homefit`. Each top-level package below is a Spring Modulith application module.

### `core` — shared domain + scoring (the crown jewel)

Framework-light: plain Java plus small libraries (`h3-java`, JTS). **No Spring Web, no controllers, no HTTP.** Both other modules depend on it; it depends on neither. This is what guarantees one definition of a Fit Score.

```
com.homefit.core
├── domain/            Property, GeoCell (H3), Dimension (enum),
│                      SubScore, FitScore, Confidence, SourceTier,
│                      UserWeights, DealBreaker
├── scoring/
│   ├── normalize/     BandScore, DecayScore        (primitives)
│   │                  FloodNormalizer, ProximityNormalizer,
│   │                  PriceNormalizer, AirQualityNormalizer,
│   │                  SafetyNormalizer, HazardNormalizer
│   ├── aggregate/     WeightedAggregator, HardFilterEvaluator,
│   │                  ConfidenceCalculator
│   └── ScoringThresholds   (externalized config + regional overrides)
└── geo/               H3Support, GeometryUtils (JTS), DistanceUtils
```

**The crucial split — same code, two runtimes:**

| Code in `core` | Called by | When |
|---|---|---|
| `scoring/normalize/*` (sub-score normalizers) | **ingestion** | offline, per cell |
| `scoring/aggregate/*` (weighted aggregator, filters, confidence) | **api** | per request |

Both sides import the same `core` classes. Ingestion precomputes universal sub-scores; the api applies the user's personal weights at request time. Because both implement the *same* `ScoringThresholds` and primitives, the numbers are always consistent.

### `ingestion` — background pipeline (no public HTTP)

A Spring Boot worker. Owns its database tables (it writes; nobody else does).

```
com.homefit.ingestion
├── adapter/      SourceAdapter (interface)
│                 NasaPowerAdapter, NasaLanceFloodAdapter,
│                 CopernicusAdapter, OsmGeofabrikImporter,
│                 OpenAqAdapter, AcledAdapter, ListingsAdapter
├── raster/       GdalCommandRunner, RasterToCellAggregator   ← GDAL-CLI lives here
├── vector/       OsmPoiProcessor, ProximityPrecomputer       ← GeoTools/JTS here
├── geocode/      GeocodingClient (Nominatim / OpenCage)
├── normalize/    SubScoreComputeJob  (calls core normalize/*, writes SubScore rows)
├── pipeline/     scheduled jobs / Spring Batch steps, QualityTierTagger
└── store/        write-side repositories (PostGIS)
```

Responsibilities: fetch from each source (auth, retry, rate-limit), reproject/clip rasters and aggregate them to H3 cells, process OSM amenities into geometry + proximity values, tag each value with its source tier and confidence, run `core`'s sub-score normalizers, and persist sub-scores per cell.

### `api` — request-time serving (Spring Web)

A Spring Boot web app. Reads ingestion's tables as a **stable read model**; never writes them. Internally split into feature sub-modules (Spring Modulith supports nesting).

```
com.homefit.api
├── properties/   PropertyController, PropertyQueryService
├── search/       SearchController, DiscoveryService
├── scoring/      ScoreController, ScoringService  (calls core aggregate/*)
├── comparison/   CompareController, ComparisonService
├── profile/      ProfileController, WeightService  (user weights + deal-breakers)
├── readmodel/    read-only repositories over ingestion tables
└── cache/        Redis / Spring Cache config
```

Request path: read precomputed sub-scores for the relevant cells → apply the user's `UserWeights` via `core.WeightedAggregator` → run `HardFilterEvaluator` for deal-breakers → compute `Confidence` over available dimensions → return DTOs. Hot areas served from Redis.

---

## Where GeoTools, JTS, and GDAL-CLI sit

This is the one place Java differs from the Python default, so it's deliberate:

| Library | Lives in | Used for |
|---|---|---|
| **JTS** (vector geometry) | `core.geo`, `ingestion.vector` | distance/proximity math, geometry ops |
| **GeoTools** (vector/feature) | `ingestion.vector` | OSM amenity processing, feature handling |
| **GDAL CLI** (`gdalwarp`, `gdal_translate`) | `ingestion.raster` only | reproject/clip/resample NASA & Copernicus flood/climate rasters before cell aggregation |

**The rule:** GDAL is invoked as a command-line *tool* from `ingestion.raster` (via `ProcessBuilder` in `GdalCommandRunner`) — never as a second application language, and never in `core` or `api`. It's the same C engine Python's rasterio wraps, so you get proven raster tooling without fragmenting the application. Domain logic stays in Java; GDAL stays a utility.

---

## Spring Modulith setup

- Declare each top-level package as a module (via `package-info.java` with `@ApplicationModule`, or convention).
- **Enforce boundaries with a test:** `ApplicationModules.of(Application.class).verify()` fails the build if, say, `api` reaches into `ingestion`'s internals or `core` accidentally depends on either. This is the mechanism that keeps the monolith honest.
- **Allowed dependencies:** `ingestion → core`, `api → core`. Never `ingestion ↔ api`. Never `core → anything`.
- **The handoff contract is the database, not code.** Ingestion owns and writes its tables; api reads them through `readmodel/` repositories and treats them as read-only. Version that schema with **Flyway/Liquibase** — it is the real contract between the two halves.
- Inter-module messaging within a side can use **Spring Modulith application events** if useful, but the api↔ingestion seam is intentionally just the DB, so no cross-side events are needed.

---

## Deployment topology

A three-stage progression. Start at Stage 0; move only when behaviour demands it. Code does not change between stages — only build/run configuration.

### Stage 0 — single process (start here)

One Spring Boot jar. The api serves HTTP; ingestion runs via `@Scheduled` jobs in the same JVM.

```
┌─────────────────────────────┐
│  homefit.jar  (one process)  │
│  [api web] + [ingestion @Scheduled] │
└───────────────┬─────────────┘
                ▼
        PostGIS  +  Redis
```

Simplest possible ops. Fine while data volumes are small. Risk: a long raster job competes with request handling for the same JVM.

### Stage 1 — same artifact, two processes (the sweet spot)

Still **one jar, one build** — but run it twice with different Spring profiles. The `api` profile starts the web server with ingestion scheduling off; the `worker` profile runs scheduled ingestion with the web server off.

```
   homefit.jar --spring.profiles.active=api      (scale to traffic)
   homefit.jar --spring.profiles.active=worker   (scale to schedule)
                         │
                         ▼
                 PostGIS  +  Redis
```

You get full process isolation and independent scaling **with zero code split**. This is the answer to "joined initially if not too large": one artifact, two run configs. Recommended as soon as ingestion starts to matter.

### Stage 2 — two artifacts (only when justified)

Refactor into a **Maven multi-module reactor** under a parent POM: `core` becomes a library jar (its own module); `api` and `ingestion` become separate Spring Boot apps that depend on it. Two images, fully independent release cycles.

```
core (lib) ──┬──► api-app   (image 1, horizontal scale)
             └──► worker-app (image 2, batch scale / scale-to-zero)
                         │
                         ▼
                 PostGIS  +  Redis
```

Because Spring Modulith already enforced the boundaries, this is a packaging change, not a rewrite.

**Move to the next stage when:** raster jobs measurably affect API latency (0→1), or you need to scale API horizontally without carrying idle ingestion, or separate teams need independent release cycles (1→2). The trigger is operational/organizational, never aesthetic.

---

## Recommended stack

| Concern | Choice | Note |
|---|---|---|
| Language / runtime | **Java 21 (LTS)** | Virtual threads help API concurrency |
| Framework | **Spring Boot 3.x** | Batteries-included for a modular monolith |
| Modularity | **Spring Modulith** | Enforces boundaries; verifies in tests |
| Build | **Maven** (single module → multi-module reactor) | Parent POM; multi-module only at Stage 2 |
| Spatial DB | **PostgreSQL + PostGIS** | via Hibernate Spatial + JTS |
| Spatial index | **H3** (`h3-java`) | Even hex cells simplify raster aggregation |
| Vector geo | **GeoTools + JTS** | OSM amenities, proximity |
| Raster geo | **GDAL CLI** from `ingestion.raster` | `gdalwarp`/`gdal_translate` via ProcessBuilder |
| Batch / scheduling | **Spring Batch + `@Scheduled`** (Quartz if needed) | External orchestrator (Airflow/Dagster) only if pipelines grow complex |
| Cache | **Redis** (Spring Data Redis / Spring Cache) | Hot areas, sub-score reads |
| API style | **REST (Spring Web)** or GraphQL (Spring for GraphQL) | GraphQL fits flexible comparison queries |
| Migrations | **Flyway** or Liquibase | Versions the ingestion→api schema contract |
| Map tiles (client) | **Mapbox / MapLibre** | Replaces the stylized prototype basemap |
| Hosting | Containerized; one image run as `api`/`worker` profiles → later two images | Matches the deployment stages above |

---

## Evolution path in one line

**One repo · one Spring Boot app · three Modulith modules (`core`/`ingestion`/`api`) → run as one process, then the same jar as two profiles, then two artifacts sharing `core`.** Each step is config, not rewrite — which is exactly the "don't over-complicate" goal.

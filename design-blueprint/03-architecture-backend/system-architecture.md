# System Architecture — Property/Home Finding App

**Status:** Design spec (v1) · **Audience:** developers, architects, and AI agents.
**Companion docs:** *Data Source Matrix*, *Fit Score Normalization Spec*, *UX Flow Spec*.

---

## Core principle

**Ingest and pre-compute offline; apply each user's weights at request time.**

Heavy geospatial work (rasters, flood archives, amenity graphs) is processed on a schedule and cached. Nothing heavy touches the live request. Only the cheap, personal step — combining cached sub-scores with a user's weights — runs per request. This is what keeps every screen instant, which is what makes the experience feel *calm*.

Two paths run through the system:

- **Background pipeline (scheduled):** Layers 1–4 ingestion + sub-score precomputation.
- **Request-time (live & personal):** weighted aggregation, filtering, and serving in Layers 4–6.

---

## Flow overview

```
┌──────────────────────────────────────────────────────────┐
│ 1. EXTERNAL DATA SOURCES                                   │
│    NASA POWER · NASA LANCE · Copernicus · SEDAC/DEM        │
│    OSM/Geofabrik · OpenAQ · ACLED/Numbeo · Listings        │
└───────────────────────────┬──────────────────────────────┘
                  pull on schedule
                            ▼
┌──────────────────────────────────────────────────────────┐
│ 2. INGESTION PIPELINE  (scheduled workers)                 │
│    source adapters → geo-normalizer (H3 grid) →            │
│    raster aggregation → geocoding → quality/tier tagging   │
└───────────────────────────┬──────────────────────────────┘
                  write normalized layers
                            ▼
┌──────────────────────────────────────────────────────────┐
│ 3. GEOSPATIAL DATA STORE  (source of truth)                │
│    PostGIS: raw + normalized layers · pre-computed         │
│    sub-scores per cell · confidence/source tier · tiles    │
└───────────────────────────┬──────────────────────────────┘
                  offline sub-score job
                            ▼
┌──────────────────────────────────────────────────────────┐
│ 4. SCORING ENGINE  (implements normalization spec)         │
│    sub-score normalizers [offline]                         │
│    weighted aggregator · hard filters · confidence [live]  │
└───────────────────────────┬──────────────────────────────┘
                  Fit Score on the fly
                            ▼
┌──────────────────────────────────────────────────────────┐
│ 5. API LAYER  (stateless · cached)                         │
│    properties/search · score/explain · comparison ·        │
│    profile/weights · Redis cache for hot areas             │
└───────────────────────────┬──────────────────────────────┘
                  JSON · personalized
                            ▼
┌──────────────────────────────────────────────────────────┐
│ 6. CLIENT APPS  (mobile + web — the calm surface)          │
│    onboarding/priorities · discovery (map+list) ·          │
│    property profile · comparison · saved & alerts          │
└──────────────────────────────────────────────────────────┘
        ▲                                          │
        └──── user weights feed back into Layer 4 ─┘
```

---

## Layer 1 — External data sources

Grouped by the two tiers from the Data Source Matrix.

| Group | Sources | Cadence | Tier |
|---|---|---|---|
| Environment & climate | NASA POWER, NASA LANCE (flood, incl. 2003–2025 archive), Copernicus SAR, SEDAC, DEM | daily / static | Free, global |
| Amenities | OpenStreetMap via Geofabrik extracts, HOTOSM | ~daily | Free, global |
| Air quality | OpenAQ, Sentinel-5P | hourly–daily | Free, global |
| Safety | ACLED (conflict events), Numbeo | weekly / periodic | Free, coarse |
| Market | Listing portals, Estate Intel | listing-driven | Hard / paid |

---

## Layer 2 — Ingestion pipeline

Scheduled workers, **one adapter per source**, all writing to a common geography.

- **Source adapters** — handle fetch, authentication (e.g. Earthdata Login), retries, rate limits.
- **Geo-normalizer** — align every source onto a shared spatial grid (recommended: H3 hexagonal cells) so heterogeneous data becomes joinable.
- **Raster → cell aggregation** — collapse flood/climate rasters into per-cell values (e.g. annualized flood frequency).
- **Geocoding** — Nominatim for low volume, OpenCage/Google/Mapbox at scale.
- **Quality & source-tier tagging** — record which source tier produced each value (drives confidence later).

---

## Layer 3 — Geospatial data store

The single source of truth the app reads from.

- **PostGIS** — raw and normalized layers, plus **pre-computed sub-scores per cell, per layer**.
- **Confidence & source tier** stored alongside every value, so honesty about coverage needs no extra lookup.
- **Object storage** — map tiles and imagery.

---

## Layer 4 — Scoring engine

Implements the *Fit Score Normalization Spec*.

- **Sub-score normalizers** *(offline)* — flood, distance, price, air, etc. → 0–100, precomputed and cached because they don't depend on the user.
- **Weighted aggregator** *(request-time)* — applies the user's weights to cached sub-scores → Fit Score.
- **Hard-filter pass** *(request-time)* — removes properties failing deal-breakers before scoring.
- **Missing-data + confidence** *(request-time)* — renormalizes weights over available dimensions and computes the confidence figure.

---

## Layer 5 — API layer

Stateless and cached.

- Endpoints: properties/search, score & explain, comparison, profile/weights.
- **Redis cache** for hot areas and repeated sub-score reads.

---

## Layer 6 — Client apps

Mobile and web — the calm surface. Onboarding, discovery (map + list), property profile, comparison, saved & alerts. **Onboarding feeds the user's weights back up into Layer 4**, closing the loop.

---

## Design rationale

- **Why precompute?** Flood and climate data are heavy rasters. Aggregating them into cells once — not per request — is what keeps the app fast enough to feel calm.
- **Weights stay personal.** Sub-scores are universal and cached; only the weighted aggregation runs per user. The same property scores differently for different people, cheaply.
- **Confidence travels with data.** Every cached value carries its source tier and confidence, so the UI stays honest about Nigeria's uneven coverage without extra lookups.
- **Built to scale out.** A new country = new source adapters (Layer 1) + regional thresholds (Layer 2). Layers 3–6 don't change, so global expansion is mostly a Layer 1–2 effort.

---

## Recommended stack

Backend is a **single-repo modular monolith on Java 21 + Spring Boot**, with `core` / `ingestion` / `api` as Spring Modulith modules. See *Backend Systems Design* for module breakdown and deployment topology.

| Concern | Choice | Note |
|---|---|---|
| Language / runtime | Java 21 (LTS) | Virtual threads aid API concurrency |
| Framework | Spring Boot 3.x | Modular monolith |
| Modularity | Spring Modulith | Enforces `core`/`ingestion`/`api` boundaries |
| Build | Maven (single module → multi-module reactor) | Multi-module only when splitting to two artifacts |
| Spatial DB | PostgreSQL + PostGIS | via Hibernate Spatial + JTS |
| Spatial index | H3 (`h3-java`) | Even hex cells simplify raster aggregation & joins |
| Vector geo | GeoTools + JTS | OSM amenities, proximity |
| Raster geo | GDAL CLI (`gdalwarp`/`gdal_translate`) | Invoked from `ingestion.raster`; not a second app language |
| Batch / scheduling | Spring Batch + `@Scheduled` | External orchestrator (Airflow/Dagster) only if pipelines grow complex |
| Cache | Redis | Hot areas, sub-score reads |
| API style | REST (Spring Web) or GraphQL | Stateless; GraphQL fits flexible comparison queries |
| Migrations | Flyway / Liquibase | Versions the ingestion→api schema contract |
| Map tiles (client) | Mapbox / MapLibre + tiles | Replaces the stylized prototype basemap |
| Hosting | Containerized; one image run as `api`/`worker` profiles → later two images | Scale pipeline and API independently |

> The architecture principle — precompute offline, personalize at request time — holds regardless of stack; the Java choice above is the committed implementation.

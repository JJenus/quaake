# HomeFit — Design Blueprint

> *Don't just find a house — understand the life around it.*

A property/home-finding platform that scores places by the **life around them** — flood and disaster risk, environment, proximity to schools/markets/worship/healthcare, safety, and affordability — against **each person's own priorities**, producing a personalized **Fit Score**. Calm to use, gently gamified. **Nigeria-first, built to scale worldwide.**

This repository is the complete v1 **design blueprint**: the research, specifications, working UI prototypes, and architecture needed to build the app. This README is the orchestrator — read it first.

---

## The core idea

Most property apps describe the *unit* (beds, baths, price). HomeFit describes the *context* and matches it to you:

1. **You set your priorities** (onboarding) → weights.
2. Every location has **precomputed sub-scores** per dimension (flood, schools, air…).
3. Your weights + a property's sub-scores → a **personalized Fit Score**, which always explains itself and always shows its **confidence**.

Two design throughlines everywhere: **calm surface, gently-gamified core** (reward understanding, never urgency) and **honesty over false precision** (confidence and data-source tier travel with every number).

---

## Tech stack at a glance

| Layer | Choice |
|---|---|
| Backend | **Java 21 + Spring Boot 3**, modular monolith (Spring Modulith), **Maven** |
| Modules | `core` (shared scoring) · `ingestion` (sourcing + precompute) · `api` (serving) |
| Data store | **PostgreSQL + PostGIS**, H3 spatial grid; Redis cache |
| Geo | GeoTools/JTS (vector) · GDAL CLI (raster) |
| Frontend | **Nuxt 4 / Vue 3** (latest 4.4.x), TypeScript, Pinia, MapLibre GL; `app/` srcDir |
| Key principle | Precompute offline, personalize at request time |

---

## How to read this blueprint

Suggested order — vision → data → backend → frontend → process:

1. **This README** — the map.
2. **`01-vision-ux/ux-flow-spec.md`** — what the user experiences, screen by screen.
3. **`02-data-scoring/`** — where data comes from, and how it becomes scores. *Start here for the substance.*
4. **`03-architecture-backend/`** — system architecture → backend design → schema → API → auth.
5. **`04-frontend/frontend-design-system.md`** — the design language and Nuxt architecture.
6. **`01-vision-ux/app-breadth-map.md`** — what's deferred and what to do next.
7. **`prototypes/`** — open the HTML files in a browser to see it working.

---

## File index

### `01-vision-ux/`
- **`ux-flow-spec.md`** — the five screens (onboarding, discovery, profile, comparison, saved), each with elements, calm/gamified choices, and states.
- **`app-breadth-map.md`** — every remaining app area mapped (notifications, payments, agent side, admin, i18n…), with pull-forward vs defer verdicts and immediate decisions.

### `02-data-scoring/`
- **`data-source-matrix.md`** — every external source (NASA POWER, LANCE flood, OSM, OpenAQ, ACLED, listings…), with access, auth, cost, refresh, and **Nigeria coverage rating**. The honest data landscape.
- **`fit-score-normalization-spec.md`** — how raw data becomes 0–100 sub-scores: per-dimension formulas (flood, distance, price, air…), the weighted aggregation, hard filters, and the missing-data/confidence rule.

### `03-architecture-backend/`
- **`system-architecture.md`** — the six layers from external sources to client, and the offline-vs-request-time split.
- **`backend-systems-design.md`** — the Spring modular monolith: `core`/`ingestion`/`api` module breakdown, where GeoTools/GDAL sit, and the three-stage deployment topology.
- **`database-schema.md`** — full PostGIS DDL: `ingest` vs `app` schemas, every data layer, provenance, the read model, **agent listing submissions**, and role-based ownership.
- **`api-surface.md`** — the REST contract: endpoints, the reusable `scoringContext`, request/response DTOs, agent endpoints, errors.
- **`auth-security-design.md`** — anonymous-first auth, JWT + rotating refresh tokens, roles (`user`/`agent`/`admin`), and security baseline.

### `04-frontend/`
- **`frontend-design-system.md`** — color tokens, typography, motion, the Vue component library, and the Nuxt 4 architecture.

### `prototypes/` (open in a browser)
- **`onboarding-flow.html`** — multi-step priorities; drag sliders to reshape your profile radar live.
- **`discovery-map.html`** — map with score pins + list cards; toggle between them.
- **`property-profile.html`** — the core screen: animated Fit Score, radar, layered detail, why-this-score.
- **`compare-properties.html`** — overlaid radars + sub-score matrix; tap a card to focus.
- **`system-architecture.html`** — the architecture diagram, visualized.

---

## How the pieces connect

```
   data-source-matrix ──► fit-score-normalization ──► database-schema
          │                        │                        │
          │                        ▼                        ▼
          │                 (sub-scores)            system-architecture
          │                        │                        │
          └────────────────────────┴───────► backend-systems-design
                                                     │
                                   ┌─────────────────┼─────────────────┐
                                   ▼                 ▼                 ▼
                              api-surface    auth-security      (ingestion)
                                   │
                                   ▼
              frontend-design-system ◄──► ux-flow-spec ◄──► prototypes
```

- **The data matrix** decides what's sourceable → **normalization** defines how it scores → **schema** stores both raw data and precomputed sub-scores → **architecture/backend** move and serve them → **API** exposes them → **frontend** renders them as the **screens** in the UX flow and prototypes.
- **The scoring split is the spine:** sub-scores are precomputed offline (ingestion); weights are applied per request (api). This single idea recurs in the normalization spec, the architecture, the schema (`cell_subscore` vs request-time affordability), the API (`fit` block vs cacheable data), and the frontend (paint-then-animate).
- **Two cross-cutting concerns** touch everything: **auth/security** (the `me/*` surface) and the **breadth map** (what to build when).

---

## Status

| Piece | State |
|---|---|
| Vision, UX flow, all specs | ✅ Designed |
| 5 UI screens | ✅ Working HTML prototypes |
| Backend architecture, schema, API, auth | ✅ Designed |
| Frontend design system | ✅ Designed |
| Agent listing submission | ✅ Designed (schema/API/auth) |
| Running Spring Boot project | ⬜ Not yet built |
| Live data ingestion | ⬜ Not yet built |
| Nuxt app | ⬜ Not yet built |

---

## The way forward

**Decisions already made:** Java/Spring Boot + Maven; modular monolith; PostGIS + H3; Nuxt/Vue; agents submit listings (via the submission→promotion flow that preserves the schema boundary).

**Decide before building (from the breadth map):**
1. **Data-source licensing** — confirm which sources are legally usable for the launch set. This gates what pipelines can be built.
2. **Low-connectivity stance** — a Nigeria-market client constraint to design for now (caching, small payloads, the `pin` projection).
3. **Observability + accessibility** — adopt as day-one principles.

**Suggested build order:**
1. **Scaffold the Spring Boot project** — parent POM, the three Modulith modules, Flyway migrations from `database-schema.md`, `ApplicationModules.verify()` test.
2. **One vertical slice of real data** — ingest NASA POWER + a flood layer + OSM amenities for one Lagos region; run the normalization formulas; populate `cell_subscore`.
3. **Wire the core API endpoints** — `properties/search`, `properties/{id}`, `/fit` — against the real read model.
4. **Build the Nuxt screens** — onboarding → discovery → profile → comparison, consuming the API, styled from the design system.
5. **Layer in auth, agent submissions, then deferred features** as releases require.

Everything in this blueprint is internally consistent and ready to build against. Start with the README, follow the reading order, and the rest composes.

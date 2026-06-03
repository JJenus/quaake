# PROJECT-STATUS

**Living status + handoff brief for Quaake (HomeFit).** Lives on `main`. Any session — human or AI — orients from this file plus the repo. Keep it short and current; it is the bootloader, not the documentation (the docs are in `design-blueprint/`).

_Last updated: handoff to the build phase._

---

## 1. What this project is

A property/home-finding platform that scores places by the life around them (flood/disaster risk, environment, proximity to amenities, safety, affordability) against each user's priorities → a personalized **Fit Score**. Nigeria-first. Core principle: **precompute sub-scores offline, apply user weights at request time.**

## 2. Repository map (branch-per-area)

| Branch | Holds | State |
|---|---|---|
| `main` | Design blueprint + specs + this file + `CONTRIBUTING.md` | ✅ canonical source of truth |
| `backend` | Java 21 · Spring Boot 4.0.6 · Spring Modulith 2.0.6 (Maven) | ✅ skeleton; ⬜ no real data yet |
| `frontend` | Nuxt 4 (pnpm) — Nuxt UI v4 + Tailwind v4 + Iconify | ✅ skeleton; ⬜ no screens yet |

Branches are **orphans** (no shared history, never merged into each other). `main` is canonical for specs and the API contract; code branches **consume** it. See `CONTRIBUTING.md`.

## 3. Done so far

- **Design:** complete blueprint on `main` — UX flow, data-source matrix, Fit Score normalization spec, system architecture, backend design, full PostGIS schema, REST API surface, auth/security, frontend design system, breadth map. Five clickable HTML prototypes in `design-blueprint/prototypes/`.
- **Backend skeleton:** `core` (domain + scoring engine: ScoringMath, all normalizers, WeightedAggregator/HardFilter/Confidence), `ingestion` (SourceAdapter iface, SubScoreComputeJob stub), `api` (ScoreController `/api/v1/score/demo` wired to real engine, PropertyController stub). 6 Flyway migrations = full schema. `ModularityTests` (boundary verify) + `ScoringSmokeTest` (worked example → 83).
- **Frontend skeleton:** Nuxt 4 app (`app/` srcDir), Nuxt UI v4, Tailwind v4, pnpm.

## 4. Housekeeping (quick, do first)

- **`backend` stray files** to remove: `quaake-main-README.md`, `setup-branch.sh` (leftovers from the orphan-branch creation; not part of the build). A duplicate `CONTRIBUTING.md` on `backend` may stay or be replaced with a one-line pointer to `main`.
- **Design-system reconciliation note:** the frontend uses Nuxt UI v4 + Tailwind v4 + Iconify(lucide), not the hand-rolled tokens + `lucide-vue-next` in `04-frontend/frontend-design-system.md`. Reconcile later: map the palette/typography tokens into Tailwind v4 `@theme` + Nuxt UI `app.config.ts`; keep the **bespoke** SVG pieces (RadarChart, FitScore) custom; use Nuxt UI for generic controls. Update the design doc on `main` when settled.

## 5. ACTIVE TASK — Vertical data slice (on `backend`)

**Goal:** prove the spine end-to-end — real Lagos data → precomputed `cell_subscore` → a real personalized Fit Score from the API. Work on a feature branch off `backend` (e.g. `backend/vertical-slice`), merge into `backend`.

**Fetch these first (from `main`):**
- `design-blueprint/03-architecture-backend/database-schema.md` (§ cell_subscore, property, cell_proximity, cell_flood_summary)
- `design-blueprint/02-data-scoring/fit-score-normalization-spec.md`
- `design-blueprint/03-architecture-backend/api-surface.md` (§5 `/properties/{id}`, §5.1 `/fit`, §1.1 scoringContext)
- `design-blueprint/02-data-scoring/data-source-matrix.md` (OSM/NASA access)
- On `backend`: existing `core` classes, `application.yml`, `db/migration/*`

**Steps (each independently reviewable, with acceptance criteria):**

1. **Read-model JPA entities (`api`, read-only).** Map `ingest.cell_subscore` (PK `cell_h3`+`dimension`, `dimension` as String) and a minimal `ingest.property`. Spring Data repositories.
   _Done when:_ a repo test reads seeded rows; entities are read-only (no writes to `ingest`).
2. **One `SourceAdapter` end-to-end (`ingestion`).** Recommended: **OSM amenities** for one small Lagos region — fetch via Overpass, write `ingest.amenity`, compute `ingest.cell_proximity` for the region's H3 cells (use `H3Support`, `DistanceUtils`). (A flood source feeding `cell_flood_summary` is a good optional second.)
   _Done when:_ running it populates `amenity` + `cell_proximity` for ≥1 region; provenance + source tier recorded.
3. **`SubScoreComputeJob` body (`ingestion`).** Read `cell_proximity` (+ `cell_flood_summary` if present), call the `core` normalizers (Proximity, Flood…), upsert `ingest.cell_subscore`, then refresh `cell_profile`.
   _Done when:_ `cell_subscore` has rows for the region's cells across ≥2 dimensions; values match the normalization spec.
4. **`GET /api/v1/properties/{id}` (`api`).** Return universal property data + the cell's sub-scores as the layered shape in api-surface §5 (no weighting).
   _Done when:_ returns real layers for a seeded property; cacheable; no `fit` block unless authed.
5. **`POST /api/v1/properties/{id}/fit` (`api`).** Load the property's cell sub-scores, accept inline `scoringContext` (§1.1), compute affordability via `PriceNormalizer` (property price + budget), run `WeightedAggregator` + `HardFilterEvaluator` + confidence → `FitResponse` with breakdown.
   _Done when:_ a request with the spec's example weights returns a real score + breakdown + confidence for a real Lagos property.
6. **Integration test (Testcontainers + PostGIS).** Seed a region/cell/property + sub-scores; assert `/fit` output.
   _Done when:_ green in CI; migrations apply cleanly against a real PostGIS container.

**Definition of done for the slice:** one real Lagos property returns a real, explainable Fit Score from `/fit`, backed by data ingested by a real adapter and scored by the real engine — with an integration test proving it.

## 6. After the slice (backlog, not now)

Add more sources (NASA POWER climate, flood archive, ACLED safety); `/properties/search`; auth + `me/*`; agent listing submission flow; then frontend screens consuming the API.

## 7. Roles & limits in this phase

- **Coding session:** writes code, pushes to `backend` feature branches, opens PRs. The doer.
- **Coordinator session (the AI PM/reviewer):** **reads** the repo (clones public over HTTPS), reviews against the specs on `main`, returns review notes + the next concrete prompt. **Cannot push, open PRs, or merge** — advisory only.
- **Contract changes** follow `CONTRIBUTING.md`: update the spec on `main` first, then `backend`, then `frontend`.

## 8. How to update this file

When a step lands, tick it here (move from §5 to §3) and bump the date line. This file is the single place to read "where are we"; keep it honest and small.

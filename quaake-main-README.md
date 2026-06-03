# Quaake — HomeFit

> *Don't just find a house — understand the life around it.*

A property/home-finding platform that scores places by the **life around them** — flood and disaster risk, environment, proximity to schools/markets/worship/healthcare, safety, and affordability — against **each person's own priorities**, producing a personalized **Fit Score**. Calm to use, gently gamified. **Nigeria-first, built to scale worldwide.**

---

## Repository layout — branch per area

| Branch | Purpose |
|---|---|
| **`main`** | 📖 Documentation & entry point — the complete design blueprint (this branch) |
| **`backend`** | ☕ Java 21 · Spring Boot 4 · Spring Modulith modular monolith |
| **`frontend`** | 🟢 Nuxt 4 · Vue 3 app *(in progress)* |

```bash
git checkout backend    # the API / scoring / ingestion code
git checkout frontend   # the Nuxt app
git checkout main        # these docs
```

> The design blueprint lives only on `main` so there's one source of truth; the code branches reference it here.

---

## Start here

1. Read **`design-blueprint/README.md`** — it orchestrates every spec and explains how they connect.
2. Open the prototypes in **`design-blueprint/prototypes/`** in a browser to see the UI working.
3. Switch to `backend` or `frontend` to build.

## The design blueprint (`design-blueprint/`)

- **`01-vision-ux/`** — UX flow (the five screens) and the app breadth map (what's deferred).
- **`02-data-scoring/`** — data-source matrix (NASA/OSM/…, with Nigeria coverage) and the Fit Score normalization spec.
- **`03-architecture-backend/`** — system architecture, backend (Spring modular monolith) design, full PostGIS schema, REST API surface, and auth/security.
- **`04-frontend/`** — design system + Nuxt 4 architecture.
- **`prototypes/`** — five clickable HTML screens.

---

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 21 · Spring Boot 4.0.6 · Spring Modulith 2.0.6 · Maven |
| Modules | `core` (scoring) · `ingestion` (sourcing + precompute) · `api` (serving) |
| Data | PostgreSQL + PostGIS, H3 grid; Redis cache |
| Frontend | Nuxt 4 / Vue 3 · TypeScript · Pinia · MapLibre GL |
| Principle | Precompute offline, personalize at request time |

## Status

| Piece | State |
|---|---|
| Design blueprint (specs + prototypes) | ✅ Complete (`main`) |
| Backend skeleton (modules, migrations, scoring engine, tests) | ✅ On `backend` |
| Live data ingestion | ⬜ Next |
| Nuxt app | ⬜ Next (`frontend`) |

## Way forward

1. One **vertical data slice** on `backend` — ingest NASA POWER + a flood layer + OSM amenities for one Lagos region, run the normalizers, populate `cell_subscore`.
2. Back the API endpoints (`/properties/search`, `/{id}`, `/fit`) with that real read model.
3. Build the Nuxt screens on `frontend`, consuming the API, styled from the design system.

See `design-blueprint/README.md` for the full reading order and rationale.

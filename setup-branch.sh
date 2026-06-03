#!/usr/bin/env bash
#
# Restructure the quaake repo into branch-per-area:
#   main      -> documentation / entry point (design-blueprint only)
#   backend   -> Spring Boot modular monolith (orphan branch, code only)
#   frontend  -> Nuxt 4 scaffold (orphan branch, placeholder)
#
# Run ONCE, from the root of your local clone, on an up-to-date `main`.
# It pushes to origin. Review before running.

set -euo pipefail

# --- safety checks ---
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "Not a git repo."; exit 1; }
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "You have uncommitted changes. Commit or stash them first."; exit 1
fi
echo "This creates orphan branches 'backend' + 'frontend' and rewrites 'main' (docs only), then pushes."
read -r -p "Proceed? [y/N] " ok; [ "${ok:-N}" = "y" ] || { echo "Aborted."; exit 1; }

git checkout main

# ============================================================
# 1) backend branch  (orphan — code only, no blueprint)
# ============================================================
git checkout --orphan backend
git rm -r --cached -q . >/dev/null
rm -rf design-blueprint
# point the backend README's footer at the blueprint that now lives on main
sed -i 's|^See the design blueprint.*|See the full design blueprint on the `main` branch (`design-blueprint/`).|' README.md || true
git add -A
git commit -q -m "backend: Spring Boot modular monolith skeleton"
git push -u origin backend
echo "  -> pushed 'backend'"

# ============================================================
# 2) frontend branch (orphan — Nuxt scaffold placeholder)
# ============================================================
git checkout -f main
git checkout --orphan frontend
git rm -r --cached -q . >/dev/null
rm -rf design-blueprint src pom.xml README.md

cat > README.md <<'FRONTEND_README'
# Quaake — Frontend (Nuxt 4)

Nuxt 4 / Vue 3 application for HomeFit. This branch is a scaffold — implementation to follow.

The design system, component library, and Nuxt architecture live on the `main` branch:
`design-blueprint/04-frontend/frontend-design-system.md`.

## Setup (when implementing)
```bash
pnpm create nuxt@latest .      # Node: active LTS (22.x+)
```

**Stack:** Nuxt 4, Vue 3, TypeScript, Pinia, MapLibre GL, lucide-vue-next.
**Key idea:** the Pinia priorities store is the API's scoring context; pages map to the
five screens (onboarding, discovery, property profile, comparison, saved).

Route layout (see design system §8.1): public routes top-level; `(app)/` group for the
signed-in user area; `agent/` and `admin/` prefixed + role-guarded.
FRONTEND_README

cat > .gitignore <<'FRONTEND_GITIGNORE'
# Nuxt / Node
node_modules
.nuxt
.output
.data
.nitro
.cache
dist

# env / secrets
.env
.env.*
!.env.example

# misc
*.log
.DS_Store
.idea
.vscode
FRONTEND_GITIGNORE

git add -A
git commit -q -m "frontend: branch scaffold (Nuxt 4 app to follow)"
git push -u origin frontend
echo "  -> pushed 'frontend'"

# ============================================================
# 3) clean up main (docs / entry point only)
# ============================================================
git checkout -f main
rm -rf src pom.xml

cat > README.md <<'MAIN_README'
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
MAIN_README

git add -A
git commit -q -m "main: clean up as entry/documentation point"
git push origin main
echo "  -> pushed 'main'"

echo
echo "Done. Branch-per-area is set up:"
git branch -a

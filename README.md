# HomeFit Frontend (Nuxt 4 — starter)

Vue 3 · Nuxt 4 · TypeScript · Pinia. Consumes the HomeFit API; styled from the design system
(`design-blueprint/04-frontend/frontend-design-system.md` on `main`).

## Run
```bash
pnpm install
pnpm dev                       # http://localhost:3000
# point at the backend (default http://localhost:8080):
NUXT_PUBLIC_API_BASE=http://localhost:8080 pnpm dev
```

Open `/property/<a-property-uuid>` (seed one via the backend) to see the layered profile +
the personalized Fit Score.

## What's wired
- `shared/types/api.ts` — TypeScript mirror of the API contract. **When the backend ships an
  OpenAPI spec, regenerate this with `openapi-typescript` so contract drift becomes a build error.**
- `app/composables/useApi.ts` / `useFitScore.ts` — typed `$fetch` client.
- `app/stores/priorities.ts` — the Pinia store that *is* the request-time scoring context.
- `app/components/score/FitScore.vue`, `RadarChart.vue` — bespoke animated SVG (ported from the prototypes).
- `app/pages/property/[id].vue` — GET `/properties/{id}` + POST `/properties/{id}/fit`.

## Next
Onboarding (set priorities → store), discovery/map (MapLibre GL), comparison, saved.
Reconcile tokens with whatever component lib you adopt; keep the bespoke score visuals custom.

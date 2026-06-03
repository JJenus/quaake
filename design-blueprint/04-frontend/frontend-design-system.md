# Frontend Design System & Nuxt Architecture — v1

**Status:** Design spec (v1) · **Audience:** frontend engineers, designers.
**Companion docs:** *UX Flow Spec*, *API Surface*. **Reference implementations:** the HTML prototypes (`onboarding-flow`, `discovery-map`, `property-profile`, `compare-properties`).
**Framework:** Nuxt 4 (Vue 3) — latest stable 4.4.x (2026). **This doc formalizes the language the prototypes establish.**

---

## 1. Design philosophy

**Calm surface, gently gamified core.** The two are reconciled by assigning each to a layer:

- **Calm = the visual layer.** Warm paper background, generous whitespace, one primary action per screen, soft transitions, no alarm colors. Reference feel: Headspace, not a trading app.
- **Gamified = discovery, never urgency.** Reward *understanding* — scores counting up, radars drawing in, a profile completing, live re-ranking when priorities change. Never countdowns, scarcity, or "people viewing now."

Three rules that bind every component: the **score always explains itself** (tappable "why"); **confidence travels with every score** (honest about data gaps); **one concept per screen**.

---

## 2. Color system

Warm, natural, grounded — a "home" palette, deliberately not the default SaaS blue/purple. Defined as CSS custom properties (design tokens).

```css
:root {
  /* surfaces */
  --paper:        #F4F1EA;   /* app background */
  --surface:      #FBFAF6;   /* cards, sheets */
  --surface-sunk: #E9E3D4;   /* map land, wells */

  /* brand greens */
  --forest:       #2F4A3C;   /* primary buttons, dark headers */
  --forest-deep:  #243A2F;
  --green:        #3A5A47;
  --sage:         #5E8268;   /* primary accent, high scores, data viz */
  --sage-soft:    #7C9885;

  /* accents */
  --clay:         #C08A5A;   /* secondary accent, drags, save-active */
  --clay-deep:    #9C5F38;
  --slate-blue:   #6E8CA0;   /* third comparison series */

  /* text */
  --ink:          #2A2A24;   /* primary text */
  --ink-muted:    #6B6B5E;   /* secondary text */
  --ink-faint:    #9A9A8C;   /* tertiary / labels */

  /* lines */
  --line:         rgba(42,42,36,0.06);
  --line-strong:  rgba(42,42,36,0.10);
}
```

### 2.1 Score color scale (soft, non-alarm)

Lower scores never go alarm-red — they shift to warm clay. This keeps the surface calm even when data is poor.

```
score ≥ 80 → var(--sage)      #5E8268
score ≥ 70 → #7C9559          (olive)
score ≥ 60 → #A98B4E          (gold)
score < 60 → var(--clay)      #C08A5A
```

### 2.2 Semantic roles

| Role | Token |
|---|---|
| Primary action | `--forest` on `--paper`; text `--paper` |
| Boost / positive | `--sage` |
| Drag / caution (soft) | `--clay` |
| Confidence pill | `--sage` on `rgba(94,130,104,.10)` |
| Comparison series | `--sage`, `--clay`, `--slate-blue` (in order) |

Genuine hazard flags are the *only* place a stronger warning tone is allowed, used sparingly.

---

## 3. Typography

Two families. A characterful display serif gives warmth; a clean grotesque keeps UI legible.

- **Display — Fraunces** (`opsz`, weights 400–600): headings, the big Fit Score number, hero titles. Soft, humanist, a little editorial.
- **Body / UI — Hanken Grotesk** (400–700): everything else.
- **Mono — JetBrains Mono** (optional): data labels, small numeric captions.

```css
--font-display: 'Fraunces', Georgia, serif;
--font-ui:      'Hanken Grotesk', system-ui, sans-serif;
```

Type scale (mobile-first): hero 30–34 / title 20–26 / body 14–15 / label 11–12.5 / caption 10–11. Display serif at ≥18px; never set body copy in Fraunces.

---

## 4. Shape, depth, motion, texture

```css
--radius-sm: 12px;  --radius-md: 16px;  --radius-lg: 22px;  --radius-xl: 28px;
--shadow-card:  0 3px 14px rgba(36,58,47,.05);
--shadow-float: 0 12px 40px rgba(36,58,47,.12);
--ease-out: cubic-bezier(.2,.7,.2,1);
--ease-soft: cubic-bezier(.4,0,.2,1);
```

- **Grain:** a subtle SVG `feTurbulence` overlay at ~2.5% opacity, `mix-blend-mode: multiply`, fixed over the viewport — gives the paper its tactile quality. Ship once as a root component.
- **Motion durations:** entrance 600–700ms; score/radar reveal ~1400ms; accordion 400ms.
- **Reduced motion:** wrap all signature animations in `@media (prefers-reduced-motion: reduce)` → render final state instantly. The count-up and radar-draw are delight, not information; never gate meaning behind motion.

---

## 5. Signature motion patterns

These are the brand's "feel." Implement as reusable composables.

1. **Score count-up + radar draw-in** — a single eased master progress `0→1` (cubic ease-out) over ~1400ms drives both the number and the radar polygon growing from center. (`useCountUp`, `useRevealProgress`.)
2. **Staggered entrance** — sections fade up (`translateY(14px)→0`) with per-element delay.
3. **Accordion** — `grid-template-rows: 0fr → 1fr` transition (smooth height with no JS measurement).
4. **Shared-element score** — the Fit Score badge transitions from a discovery card into the profile hero, "growing" into place (Nuxt page transitions + a shared layout id).

---

## 6. Iconography

**lucide-vue-next.** Stroke width 1.8–1.9, `currentColor`, sizes 14/16/18/20. Consistent set already used across prototypes (droplets=flood, graduation-cap=schools, landmark=worship, wind=air, shield=safety, basket=markets, stethoscope=hospital, wallet=cost, foot=walk, car=drive).

---

## 7. Core component library

Vue SFCs. Props listed are the essentials.

| Component | Purpose | Key props |
|---|---|---|
| `FitScore` | Animated circular score + label | `score`, `confidence`, `size`, `animate` |
| `RadarChart` | Custom SVG radar (1–N series) | `dimensions`, `series[]`, `progress`, `focusIndex` |
| `WhyBreakdown` | Boosters/drags + contribution list | `breakdown[]`, `boosters[]`, `drags[]` |
| `LayerAccordion` | Expandable layer with sub-scores | `layer`, `open` |
| `ScoreRow` | One sub-score / proximity row | `label`, `value`, `subScore`, `mode`, `confidence` |
| `PropertyCard` | Discovery list card | `property`, `fit` |
| `ScorePin` | Map marker bubble | `lat`, `lng`, `score`, `selected` |
| `ConfidencePill` | "Based on 4 of 5 factors" | `available`, `total`, `sourceNote` |
| `FilterChip` | Removable filter / sort chip | `label`, `removable` |
| `SegmentedToggle` | Buy/Rent, Map/List | `options`, `modelValue` |
| `PriorityCard` | Onboarding "what matters" pick | `dimension`, `selected` |
| `PrioritySlider` | Weight slider w/ live label | `dimension`, `modelValue` |
| `ActionBar` | Persistent Save/Compare/Share | `saved`, `comparing` |
| `GrainOverlay` | Paper texture | — |

Charts are **bespoke SVG components**, not a heavy charting lib — the radar's hand-made feel is part of the brand, and SVG animates cleanly.

---

## 8. Nuxt 4 architecture

**Project setup** (verified against the official Nuxt 4 docs):

```bash
npm create nuxt@latest homefit-web      # or: pnpm create nuxt@latest homefit-web
# Node.js: active LTS (20.x+; the v4 install guide currently lists 22.x or newer)
```

**The defining Nuxt 4 change:** application code now lives in an **`app/` directory** (the default `srcDir`), while `server/`, `shared/`, `public/`, `modules/`, and `nuxt.config.ts` stay at the project **root**. This is the official v4 layout:

```
homefit-web/
├── app/                       # srcDir (Nuxt 4 default)
│   ├── assets/css/
│   │   ├── tokens.css         # the CSS custom properties above
│   │   └── base.css
│   ├── components/
│   │   ├── score/             FitScore, RadarChart, WhyBreakdown, ConfidencePill
│   │   ├── property/          PropertyCard, LayerAccordion, ScoreRow, ActionBar
│   │   ├── discovery/         ScorePin, FilterChip, SegmentedToggle, MapCanvas
│   │   ├── onboarding/        PriorityCard, PrioritySlider, ProgressDots
│   │   └── common/            GrainOverlay, AppButton, Sheet
│   ├── composables/
│   │   ├── useCountUp.ts
│   │   ├── useRevealProgress.ts
│   │   ├── useFitScore.ts     # calls /properties/{id}/fit, /score/batch
│   │   └── useApi.ts          # typed $fetch/useFetch factory, auth, problem+json
│   ├── stores/                # Pinia (auto-imported from app/stores)
│   │   ├── profile.ts         # intent, region, budget
│   │   ├── priorities.ts      # weights + deal-breakers (the scoringContext source)
│   │   └── session.ts         # auth tokens, user
│   ├── pages/
│   │   ├── index.vue          # discovery (map + list)
│   │   ├── onboarding.vue     # multi-step priorities flow
│   │   ├── property/[id].vue  # profile
│   │   ├── compare.vue        # comparison
│   │   └── saved.vue
│   ├── middleware/            auth.ts (guards /me, /saved, agent routes)
│   ├── app.config.ts          # theme tokens (useAppConfig)
│   ├── app.vue
│   └── error.vue
├── shared/                    # NEW in v4 — types/util shared by app + Nitro server
│   └── types/                 # API DTOs (Property, FitResult, ScoringContext…)
├── server/                    # Nitro — optional BFF/proxy to the Java API
│   ├── api/                   # e.g. token-handling, SSR-side fetches, caching
│   └── utils/
├── public/
├── modules/
└── nuxt.config.ts
```

- **State:** Pinia (`@pinia/nuxt`), auto-imported from `app/stores/`. The `priorities` + `profile` stores *are* the inline `scoringContext` (API §1.1) — built during onboarding, sent with anonymous requests, persisted to `me/*` after sign-in.
- **Data fetching:** a **custom `useFetch`/`useAsyncData` factory** (Nuxt 4.4 feature) in `useApi` gives a typed client — attaches the Bearer token, sets the API base URL, and surfaces RFC 7807 errors in one place. Universal data (`GET /properties/{id}`) is SSR-friendly/cacheable; the personal `fit` block is fetched client-side and animated in.
- **`shared/` for the contract:** put the API DTO types (mirroring `api-surface.md`) in `shared/types` so both the Nitro `server/` layer and the Vue `app/` use one definition. Generate them from the backend's OpenAPI spec to stay in sync.
- **`server/` as an optional BFF:** since the backend is Java/Spring Boot, the Nitro `server/` can act as a thin proxy — keeping refresh tokens in httpOnly cookies, doing SSR-side fetches, and caching hot reads — without duplicating business logic (which stays in Spring).
- **Routing:** Vue Router v5 (Nuxt 4.4) with typed routes; pages above map 1:1 to the screens.
- **SSR/SEO:** discovery and property pages render server-side for shareable, indexable URLs; personalization hydrates on the client.
- **Map:** **MapLibre GL** (open, no vendor lock) via a thin `MapCanvas` wrapper; `ScorePin` markers from the search `pin` projection.
- **Rendering the score split:** the page paints cacheable property data immediately, then `useFitScore` resolves and the `FitScore`/`RadarChart` animate — mirroring the architecture's universal-vs-request-time seam.
- **TypeScript:** Nuxt 4 emits separate `tsconfig` project references for app / server / node contexts — keep the `shared/` types framework-agnostic so they import cleanly in all three.

### 8.1 Route organization & access boundaries

Pages are arranged by **audience**, so the access boundary is visible in the URL and mirrors the role boundary in the auth design (`user` / `agent` / `admin`). The rule: **role-gated surfaces get a real path prefix; the end-user area gets a *code* group but no URL prefix; public content stays top-level and shareable.**

```
app/pages/
├── index.vue                → /              public · discovery (map + list)
├── property/[id].vue        → /property/:id  public · profile (richer when authed)
├── compare.vue              → /compare       public
│
├── (app)/                   route GROUP — shared layout/middleware, NO url segment
│   ├── saved.vue            → /saved         authenticated end-user
│   ├── profile.vue          → /profile
│   └── comparisons/index.vue→ /comparisons
│
├── agent/                   → /agent/*       role: agent
│   ├── index.vue            → /agent         dashboard
│   ├── listings/index.vue   → /agent/listings
│   └── listings/[id].vue    → /agent/listings/:id
│
└── admin/                   → /admin/*        role: admin
    ├── index.vue            → /admin
    ├── submissions/index.vue→ /admin/submissions   approve agent listings
    └── sources/index.vue    → /admin/sources
```

**Why these three differ:**

- **`agent/` and `admin/` use real prefixes.** They're distinct, role-gated surfaces; a visible prefix is honest about access, easy to guard per-folder, and never collides with public content.
- **The end-user area uses a Nuxt route group `(app)/`, not a `/user` prefix.** Parentheses group files (shared layout + middleware) **without adding a URL segment**, so the pages stay at `/saved`, `/profile`. For a signed-in person the app *is* their experience — burying it under `/user/*` makes the core product feel like a sub-section of itself.
- **Shareable content stays public-shaped.** `/property/:id` and `/compare` are the *same* URL for everyone — just richer when authenticated — so a logged-in user can paste a link to anyone. Never move shareable pages behind an audience prefix.

**Guarding — one rule per area** (the frontend gate is UX; the API's `@PreAuthorize` + ownership remains the real enforcement):

```ts
// app/middleware/auth.ts   — must be signed in        (applied to (app) group)
// app/middleware/agent.ts  — must have 'agent' role    (applied to agent/)
// app/middleware/admin.ts  — must have 'admin' role    (applied to admin/)
```

Apply per group via the group's layout or `definePageMeta({ middleware: 'agent' })`. Each variant reads the `session` store and redirects unauthenticated users to login, or returns a 403 view for wrong-role access. This maps 1:1 onto the API authorization in *Auth & Security* §5.

> Optional: a branded post-login landing (e.g. `/app` or `/home`) is fine **only** for the dashboard, never for shareable content pages.

---

## 9. Tailwind mapping (optional but recommended)

If using `@nuxtjs/tailwindcss` (Tailwind v4, which Nuxt 4 / Nuxt UI v4 support), map the tokens into the theme so utilities stay on-brand. (The prototypes use inline styles for portability; production should use tokens.)

```js
// tailwind.config — theme.extend
colors: {
  paper:'#F4F1EA', surface:'#FBFAF6', forest:'#2F4A3C',
  green:'#3A5A47', sage:'#5E8268', sageSoft:'#7C9885',
  clay:'#C08A5A', slate:'#6E8CA0',
  ink:'#2A2A24', inkMuted:'#6B6B5E', inkFaint:'#9A9A8C'
},
fontFamily: { display:['Fraunces','serif'], sans:['Hanken Grotesk','sans-serif'] },
borderRadius: { sm:'12px', md:'16px', lg:'22px', xl:'28px' }
```

---

## 10. Accessibility (a day-one principle, per the breadth map)

- **Contrast:** body text on paper/surface meets WCAG AA; verify the gold/clay score text on light pills.
- **Motion:** honor `prefers-reduced-motion` everywhere (see §4); never convey meaning only through animation.
- **Semantics:** real headings, buttons, and labels; the radar carries an accessible text alternative (the `WhyBreakdown` list *is* the non-visual equivalent of the chart).
- **Focus:** visible focus rings (use `--sage`); full keyboard paths through onboarding, filters, and the action bar.
- **Targets:** ≥44px touch targets (the action-bar buttons are 54px).
- **Color independence:** scores pair number + position (radar) + words (why), so they never rely on hue alone.

---

## 11. Stack summary (frontend)

| Concern | Choice |
|---|---|
| Framework | Nuxt 4 / Vue 3 (latest 4.4.x) |
| Language | TypeScript (separate app/server/node tsconfigs) |
| State | Pinia (`@pinia/nuxt`) |
| Routing | Vue Router v5 (typed routes) |
| Styling | CSS custom-property tokens (+ optional Tailwind) |
| Icons | lucide-vue-next |
| Charts | Bespoke SVG components |
| Map | MapLibre GL |
| Fonts | Fraunces + Hanken Grotesk (self-host for performance) |
| Data | `$fetch`/`useFetch` against the v1 REST API |

Backend remains **Java / Spring Boot** (see *Backend Systems Design*); this frontend consumes its REST API.

---

## 12. One-line summary

**A warm, calm "home" palette (paper + forest/sage/clay) with Fraunces + Hanken Grotesk, gentle discovery-driven motion (count-up, radar draw-in), bespoke SVG data viz, and an accessible, token-driven Nuxt 4 component system whose Pinia priorities store doubles as the API's scoring context.**

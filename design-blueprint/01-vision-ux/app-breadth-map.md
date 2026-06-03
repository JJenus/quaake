# App Breadth Map — What a Complete App Still Needs

**Status:** Planning map (v1) · **Audience:** product + engineering.
**Purpose:** one shallow pass over *every* remaining area, so nothing structural surprises us. This is a map, **not** detailed design. Each row says what it is, when it's roughly needed, what it touches, and a verdict.

**Verdict key**
- 🔴 **Pull forward** — crosses a structural boundary (schema / auth / architecture) or a core decision depends on it. Decide/design before more core work risks rework.
- 🟡 **Decide direction now** — a business choice gates later design; make the choice, defer the detail.
- 🟢 **Defer** — self-contained leaf; add later without touching existing contracts.
- 🔵 **Cross-cutting** — bake in as a standing principle from day one; not a feature to schedule.

---

## The map

| Area | What it is | When | Touches | Verdict |
|---|---|---|---|---|
| **Listing origin** (agent/owner side) | **DECIDED: agents/owners submit listings in-app** (plus any sourced data) | Core | Schema (`app.listing_submission` → promoted to `ingest.property`), auth (`agent` role), agent portal | ✅ |
| **Data-source licensing** | Legal right to use each source (listing ToS, ACLED/Numbeo terms) | Core/now | What's buildable at all; could block launch | 🔴 |
| **Observability** | Logging, metrics, tracing, error tracking | Now | Architecture (instrumentation everywhere) | 🔵 |
| **Accessibility** | WCAG-level usable UI | Now | Every client screen | 🔵 |
| **Low-connectivity / offline** | Graceful behavior on poor networks (key for NG) | Now-ish | **Client architecture** (caching, PWA, payload size) | 🔴 |
| **Notifications & alerts** | New matches above a fit threshold; saved-property price changes | Phase 3 | Schema (alert rules, log), a trigger job, push/email infra | 🟡 |
| **Monetization / payments** | Subscriptions, agent fees, premium features | When monetizing | Schema (plans, transactions), Paystack/Flutterwave, what's gated | 🟡 |
| **Reviews / lived-experience** | User reviews of areas/properties | Later | Schema, API, moderation — *and* could feed thin data tiers (safety) | 🟢 |
| **Admin & moderation** | Internal tooling: manage sources, review runs, moderate, manage users | As ops grows | `admin` role, separate UI, reads across schemas | 🟢 |
| **Saved searches** | Persisted search criteria / recents | Phase 3 | Schema (`saved_search`); overlaps alerts (search + threshold = alert) | 🟢 |
| **i18n / multi-currency** | Multiple languages & currencies for worldwide reach | Expansion | UI content; **schema already carries currency + country_code** | 🟢 |
| **Media handling** | Property photo storage, CDN, resizing | When listings real | Object storage (already in schema), CDN | 🟢 |
| **Product analytics** | Funnel, what users weight, retention | Post-launch | Event pipeline, privacy | 🟢 |
| **Legal / privacy docs** | Privacy policy, terms, NDPR notices | Pre-launch | Content; auth already does deletion/export | 🟢 |
| **Support / feedback / help** | In-app feedback, help center | Later | Leaf | 🟢 |

---

## Why the 🔴 / 🔵 items can't wait

**Listing origin is the big one — and it can invalidate a schema decision.** We committed to *ingestion owns `property`* (sourced data, read-only to the API). If agents/owners submit listings **in-app**, that's an API-side write to property data — which would break that boundary. The clean resolution (decide now, design later): agent submissions land in an `app.listing_submission` queue, get validated, then are **promoted into `ingest.property`** by a pipeline step. That preserves the boundary *if* we plan for it. But whether agents submit at all is real scope (a whole portal + `agent` role + verification), so **the decision must be made now** even though the build is later.

**Data-source licensing gates what's even buildable.** The data matrix already flagged that listing portals have no open API and need scraping or partnership, and that several sources have terms. This isn't a feature — it's a go/no-go input. Confirm which sources are legally usable *before* building pipelines against them, or you build on sand.

**Low-connectivity is a Nigeria-market architecture constraint, not a feature.** It shapes client choices (aggressive caching, small payloads, PWA/offline-tolerant patterns, the `pin` projection we already added to search). Cheap to design for now; expensive to retrofit. Hence 🔴, not 🟢.

**Observability and accessibility are 🔵** — standing principles to instrument/build from the first real line of code, because both are painful and incomplete when bolted on late.

---

## Recommended immediate actions (decisions, not builds)

1. **Listing origin — DECIDED:** agents/owners submit listings in-app. Handled as a source feeding ingestion: `app.listing_submission` → validated/geocoded → promoted into `ingest.property` (`origin='agent'`), so the ownership boundary survives. Adds the `agent` role + a submission/review flow.
2. **Confirm data-source licensing** for the launch set — which sources are usable, and on what terms.
3. **Adopt baseline observability + accessibility** as build-from-day-one principles.
4. **Note low-connectivity** as an explicit client-architecture constraint before frontend build.

Everything else (🟡/🟢) is correctly deferred. Continue deep-designing the core; pull a deferred item in only when a release needs it or a core decision starts to depend on it.

---

## One-line takeaway

**Only listing-origin, data-licensing, and low-connectivity genuinely cross boundaries and need attention now; observability and accessibility are day-one principles; the rest are safe, well-understood leaves to design when their moment comes — so keep going core-first.**

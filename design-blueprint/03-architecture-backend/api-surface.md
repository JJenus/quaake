# RESTful API Surface — v1

**Status:** Design spec (v1) · **Audience:** backend & frontend engineers, AI agents.
**Companion docs:** *Database Schema*, *Fit Score Normalization Spec*, *Backend Systems Design*, *UX Flow Spec*.

The API is the `api` Spring Boot module. It reads the precomputed read model (`ingest.cell_subscore`, `cell_proximity`, `property`, …) and applies the user's weights at request time. Endpoints map directly onto the built screens (onboarding, discovery, profile, comparison) and the schema tables.

---

## 1. Conventions

- **Base path / versioning:** `/api/v1`. Breaking changes → `/api/v2`.
- **Format:** `application/json`, **camelCase** keys. Server is snake_case internally; Jackson maps at the edge.
- **Money:** `{ "amount": 80000000, "currency": "NGN" }` — integer/decimal amount + ISO 4217. Never a bare float.
- **Scores:** integer `0–100`. **Confidence:** float `0–1`. **Dimension tokens** match the `dimension` enum exactly: `flood, schools, affordability, worship, air_quality, safety, markets, hospital, transit, parks, other_hazards`.
- **IDs:** property/user are UUID strings; H3 cells are the bigint index serialized as a string.
- **Auth:** `Authorization: Bearer <JWT>`. Per the UX, auth is **deferred** — discovery, property detail, scoring, and compare all work anonymously; only `me/*` persistence requires a token.
- **Pagination:** cursor-based. List responses return `{ items, nextCursor, approximateTotal }`; pass `?cursor=` to continue. `nextCursor` is `null` on the last page.
- **Idempotency:** `PUT` for profile/weights/saved is idempotent. `POST` that mutates accepts an optional `Idempotency-Key` header.
- **Errors:** RFC 7807 `application/problem+json` (§9).

### 1.1 The `scoringContext` object (reused everywhere scoring happens)

Resolved in priority order:

1. **Authenticated, omitted body** → server loads the user's stored `user_weight`, `user_profile.budget`, and `user_dealbreaker`.
2. **Inline** (for anonymous use and onboarding preview before signup):

```json
{
  "weights": [
    { "dimension": "flood", "weight": 0.30 },
    { "dimension": "schools", "weight": 0.25 },
    { "dimension": "affordability", "weight": 0.20 },
    { "dimension": "worship", "weight": 0.15 },
    { "dimension": "air_quality", "weight": 0.10 }
  ],
  "budget": { "amount": 85000000, "currency": "NGN" },
  "dealbreakers": [
    { "type": "max_price", "params": { "amount": 90000000 } },
    { "type": "hospital_within_km", "params": { "km": 15 } },
    { "type": "min_subscore", "params": { "dimension": "flood", "min": 50 } }
  ]
}
```

Weights need not sum to 1; the server normalizes (and renormalizes over available dimensions per the spec's missing-data rule).

---

## 2. Reference data

Used by onboarding pickers and region selection.

### `GET /api/v1/dimensions`
Returns the weightable dimensions for the "what matters" step.
```json
{ "items": [
  { "dimension": "flood",   "label": "Flood safety", "description": "How rarely this area floods", "betterWhen": "higher" },
  { "dimension": "schools", "label": "Schools",      "description": "Proximity to primary schools", "betterWhen": "higher" }
] }
```

### `GET /api/v1/regions`
Admin regions for location selection. `200 OK`.
| Query | Type | Notes |
|---|---|---|
| `country` | string(2) | ISO country (e.g. `NG`) |
| `level` | int | 0 country … 2 city/LGA |
| `parentId` | long | children of a region |
| `q` | string | name search |

```json
{ "items": [
  { "id": 4412, "name": "Lagos", "level": 1, "parentId": 1, "countryCode": "NG" },
  { "id": 5531, "name": "Eti-Osa", "level": 2, "parentId": 4412, "countryCode": "NG" }
] }
```

---

## 3. Profile & priorities (`me/*`, auth required)

Maps to `app.user_profile`, `app.user_weight`, `app.user_dealbreaker`. These are written by the onboarding flow.

### `GET /api/v1/me/profile` · `PUT /api/v1/me/profile`
```json
// PUT body
{ "intent": "buy", "searchRegionId": 4412, "budget": { "amount": 85000000, "currency": "NGN" } }
```
`200 OK` returns the saved profile. `PUT` is a full replace.

### `GET /api/v1/me/weights` · `PUT /api/v1/me/weights`
```json
// PUT body — raw weights; server stores raw, returns normalized
{ "weights": [
  { "dimension": "flood", "weight": 55 },
  { "dimension": "schools", "weight": 45 },
  { "dimension": "worship", "weight": 30 }
] }
```
```json
// 200 OK
{ "weights": [
  { "dimension": "flood",   "weight": 0.423 },
  { "dimension": "schools", "weight": 0.346 },
  { "dimension": "worship", "weight": 0.231 }
], "updatedAt": "2026-06-02T10:15:00Z" }
```
Changing weights invalidates `fit_score_cache` for the user (via `weightsHash`).

### `PUT /api/v1/me/dealbreakers`
Replaces the deal-breaker set. Body `{ "dealbreakers": [ … ] }` using the same shape as in `scoringContext`. `200 OK`.

---

## 4. Discovery / search (the discovery + map screen)

### `POST /api/v1/properties/search`
Returns properties scored for the caller. `POST` because the scoring context + filters form a rich body (a common, accepted REST exception for search). Auth optional; uses stored profile if present, else inline `scoringContext`.

```json
// request
{
  "area": { "regionId": 4412 },          // OR "bbox":[minLng,minLat,maxLng,maxLat] OR "center":{lat,lng},"radiusM":5000
  "filters": {                            // hard query constraints (distinct from soft weights)
    "tenure": "sale",
    "propertyType": ["apartment","duplex"],
    "price": { "min": 0, "max": 90000000, "currency": "NGN" },
    "bedrooms": { "min": 2 }
  },
  "scoringContext": { /* §1.1, or omit when authenticated */ },
  "sort": "fitScore",                     // fitScore | priceAsc | priceDesc | newest
  "projection": "summary",                // summary (cards) | pin (map)
  "limit": 20,
  "cursor": null
}
```

```json
// 200 OK — projection=summary
{
  "items": [
    {
      "id": "b3f1…",
      "name": "Lekki Phase 1",
      "areaLabel": "Lagos, Nigeria",
      "location": { "lat": 6.4413, "lng": 3.4790 },
      "priceBand": "₦75M – ₦85M",
      "price": { "amount": 80000000, "currency": "NGN" },
      "thumbnailUrl": "https://…",
      "fit": {
        "score": 83,
        "confidence": 1.0,
        "basedOnFactors": { "available": 5, "total": 5 },
        "topReasons": [
          { "kind": "boost", "text": "Low flood risk" },
          { "kind": "boost", "text": "School 8 min away" }
        ]
      }
    }
  ],
  "nextCursor": "eyJvIjoyMH0",
  "approximateTotal": 47
}
```

```json
// 200 OK — projection=pin (lightweight for map markers)
{ "items": [ { "id": "b3f1…", "lat": 6.4413, "lng": 3.4790, "fitScore": 83, "confidence": 1.0 } ],
  "nextCursor": null }
```

Empty result with active filters returns `items: []` plus a `hints` array suggesting which filter to relax (powers the empty-state UI):
```json
{ "items": [], "nextCursor": null, "approximateTotal": 0,
  "hints": [ { "relax": "price.max", "wouldUnlock": 12 } ] }
```

---

## 5. Property profile (the property profile screen)

### `GET /api/v1/properties/{id}`
Universal property data + cell sub-scores (cacheable; not user-weighted). If the request is authenticated, a personalized `fit` block is included; otherwise omit it and call §5.1.

```json
// 200 OK
{
  "id": "b3f1…",
  "name": "Lekki Phase 1",
  "areaLabel": "Lagos, Nigeria",
  "location": { "lat": 6.4413, "lng": 3.4790 },
  "tenure": "sale",
  "propertyType": "apartment",
  "bedrooms": 3, "bathrooms": 3, "sizeSqm": 145,
  "price": { "amount": 80000000, "currency": "NGN" },
  "priceBand": "₦75M – ₦85M",
  "photos": ["https://…"],
  "listingUrl": "https://…",
  "cellH3": "612...",
  "layers": [
    {
      "layer": "risk_environment", "label": "Risk & environment",
      "layerScore": 76, "confidence": 0.95, "sourceNote": "NASA flood archive",
      "items": [
        { "dimension": "flood", "label": "Flood — floods ~1 in 10 years", "subScore": 74, "confidence": 1.0, "sourceTier": "measured" },
        { "dimension": "air_quality", "label": "Air quality — PM2.5 moderate", "subScore": 79, "confidence": 0.8, "sourceTier": "modeled" },
        { "dimension": "other_hazards", "label": "Other hazards — low", "subScore": 88, "confidence": 0.9, "sourceTier": "modeled" }
      ]
    },
    {
      "layer": "daily_life", "label": "Daily life & proximity",
      "layerScore": 96, "confidence": 0.92, "sourceNote": "OpenStreetMap",
      "items": [
        { "amenityType": "school",            "label": "Primary school", "travelMinutes": 8,  "travelMode": "walk",  "subScore": 100, "confidence": 1.0 },
        { "amenityType": "place_of_worship",  "label": "Mosque",         "travelMinutes": 4,  "travelMode": "walk",  "subScore": 100, "confidence": 1.0 },
        { "amenityType": "market",            "label": "Daily market",   "travelMinutes": 11, "travelMode": "walk",  "subScore": 90,  "confidence": 0.9 },
        { "amenityType": "hospital",          "label": "Hospital",       "travelMinutes": 14, "travelMode": "drive", "subScore": 85,  "confidence": 0.9 }
      ]
    },
    {
      "layer": "livability", "label": "Livability",
      "layerScore": 70, "confidence": 0.5, "sourceNote": "City-level data",
      "items": [ { "dimension": "safety", "label": "Safety (city-level)", "subScore": 72, "confidence": 0.5, "sourceTier": "proxy" } ]
    },
    {
      "layer": "cost", "label": "Cost",
      "layerScore": null, "confidence": null, "sourceNote": "Computed against your budget",
      "items": [ { "dimension": "affordability", "label": "Affordability", "subScore": null, "note": "Personalized — see fit" } ]
    }
  ],
  "fit": { /* present only if authenticated — same shape as §5.1 response */ }
}
```

### 5.1 `POST /api/v1/properties/{id}/fit`
Computes the personalized Fit Score (and the affordability sub-score, which needs the budget). Powers the animated score + "why this score" breakdown.

```json
// request: { "scoringContext": { … §1.1 } }   (or empty body when authenticated)
// 200 OK
{
  "propertyId": "b3f1…",
  "score": 83,
  "confidence": 1.0,
  "basedOnFactors": { "available": 5, "total": 5 },
  "breakdown": [
    { "dimension": "flood",         "weight": 0.30, "subScore": 74,  "contribution": 22.2 },
    { "dimension": "schools",       "weight": 0.25, "subScore": 100, "contribution": 25.0 },
    { "dimension": "affordability", "weight": 0.20, "subScore": 65,  "contribution": 13.0 },
    { "dimension": "worship",       "weight": 0.15, "subScore": 100, "contribution": 15.0 },
    { "dimension": "air_quality",   "weight": 0.10, "subScore": 79,  "contribution": 7.9 }
  ],
  "boosters": [
    "Flooding here is rare — about 1 in 10 years",
    "A mosque is a 4-minute walk away",
    "Primary school within an 8-minute walk"
  ],
  "drags": [
    "Price sits close to your budget ceiling",
    "Air quality is moderate, not pristine"
  ]
}
```
`404` if the property doesn't exist; `422` if the scoring context is invalid (e.g. weights all zero).

---

## 6. Scoring (batch)

### `POST /api/v1/score/batch`
Scores several properties in one call (used internally by search and by comparison).
```json
// request
{ "scoringContext": { … }, "propertyIds": ["b3f1…","c2a9…","d4e8…"] }
// 200 OK
{ "items": [ { "propertyId": "b3f1…", "score": 83, "confidence": 1.0,
               "breakdown": [ … ] } ] }
```

---

## 7. Comparison (the comparison screen)

### `POST /api/v1/compare` (stateless)
Compares 2–4 properties for the caller. Returns each property's fit plus the per-dimension matrix, winners, and a plain-language trade-off summary.

```json
// request
{ "scoringContext": { … }, "propertyIds": ["b3f1…","c2a9…","d4e8…"] }   // 2–4 ids
```
```json
// 200 OK
{
  "properties": [
    { "id": "b3f1…", "name": "Lekki Phase 1", "priceBand": "₦75M – ₦85M",
      "fit": { "score": 83, "confidence": 1.0 } },
    { "id": "c2a9…", "name": "Yaba", "priceBand": "₦45M – ₦55M",
      "fit": { "score": 75, "confidence": 1.0 } },
    { "id": "d4e8…", "name": "Ikoyi", "priceBand": "₦140M – ₦160M",
      "fit": { "score": 77, "confidence": 1.0 } }
  ],
  "matrix": [
    { "dimension": "flood",         "values": { "b3f1…": 74,  "c2a9…": 55, "d4e8…": 88 }, "winner": "d4e8…" },
    { "dimension": "schools",       "values": { "b3f1…": 100, "c2a9…": 90, "d4e8…": 95 }, "winner": "b3f1…" },
    { "dimension": "affordability", "values": { "b3f1…": 65,  "c2a9…": 88, "d4e8…": 40 }, "winner": "c2a9…" },
    { "dimension": "worship",       "values": { "b3f1…": 100, "c2a9…": 80, "d4e8…": 70 }, "winner": "b3f1…" },
    { "dimension": "air_quality",   "values": { "b3f1…": 79,  "c2a9…": 60, "d4e8…": 84 }, "winner": "d4e8…" }
  ],
  "overallWinner": "b3f1…",
  "tradeoffs": [
    { "propertyId": "b3f1…", "text": "Highest overall fit — best for schools and worship proximity." },
    { "propertyId": "d4e8…", "text": "Safest from floods with the cleanest air, but the priciest." },
    { "propertyId": "c2a9…", "text": "Most affordable and central, but floods more often and air quality is poorer." }
  ]
}
```
`422` if fewer than 2 or more than 4 ids.

### Persisted comparison sets (`app.comparison_set`, auth required)
| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/me/comparisons` | Create a saved set `{ name, propertyIds }` |
| `GET` | `/api/v1/me/comparisons` | List sets |
| `GET` | `/api/v1/me/comparisons/{id}` | Fetch a set (resolves to a `/compare` result) |
| `PATCH` | `/api/v1/me/comparisons/{id}` | Rename / add / remove items |
| `DELETE` | `/api/v1/me/comparisons/{id}` | Delete |

---

## 8. Saved properties (`app.saved_property`, auth required)

| Method | Path | Purpose | Codes |
|---|---|---|---|
| `GET` | `/api/v1/me/saved` | List saved properties, each with current `fit` | `200` |
| `PUT` | `/api/v1/me/saved/{propertyId}` | Save (idempotent); optional `{ "note": "…" }` | `200`/`201` |
| `DELETE` | `/api/v1/me/saved/{propertyId}` | Unsave | `204` |

---

## 8A. Agent listings (`app.listing_submission`, role `agent`)

Agents submit listings here; an ingestion adapter promotes approved ones into `ingest.property` (schema §6.1). The agent owns the submission; the API never writes `ingest.property`.

| Method | Path | Purpose | Codes |
|---|---|---|---|
| `POST` | `/api/v1/me/listings` | Create a draft submission | `201` |
| `GET` | `/api/v1/me/listings` | List the agent's own submissions (filter `?status=`) | `200` |
| `GET` | `/api/v1/me/listings/{id}` | Fetch one (incl. `promotedPropertyId` once live) | `200` |
| `PATCH` | `/api/v1/me/listings/{id}` | Edit while `draft`/`submitted` | `200` |
| `POST` | `/api/v1/me/listings/{id}/submit` | Move `draft → submitted` for review | `200` |
| `POST` | `/api/v1/me/listings/{id}/withdraw` | Withdraw | `200` |
| `DELETE` | `/api/v1/me/listings/{id}` | Delete a draft | `204` |

```json
// POST /me/listings  request
{
  "title": "3-bed apartment, Lekki Phase 1",
  "tenure": "sale",
  "propertyType": "apartment",
  "bedrooms": 3, "bathrooms": 3, "sizeSqm": 145,
  "price": { "amount": 80000000, "currency": "NGN" },
  "address": "…", "location": { "lat": 6.4413, "lng": 3.4790 },
  "photos": ["https://…"]
}
// 201 Created
{ "id": "f9c2…", "status": "draft", "createdAt": "2026-06-02T10:20:00Z" }
```

```json
// GET /me/listings/{id} after promotion
{ "id": "f9c2…", "status": "promoted",
  "promotedPropertyId": "b3f1…",      // now a normal property in discovery/profile
  "reviewedAt": "2026-06-03T08:00:00Z" }
```

Moderation/approval endpoints (`approve`/`reject`) live behind the `admin` role and are out of the public surface here (see *Auth & Security* §5). `422` on submit if required fields (price, location, tenure) are missing.

---

## 9. Error model (RFC 7807)

```json
// 422 Unprocessable Entity
{
  "type": "https://errors.homefit.app/invalid-scoring-context",
  "title": "Invalid scoring context",
  "status": 422,
  "detail": "All dimension weights are zero.",
  "instance": "/api/v1/properties/b3f1…/fit",
  "errors": [ { "field": "weights", "message": "At least one weight must be positive." } ]
}
```

| Status | When |
|---|---|
| `400` | Malformed request / bad query params |
| `401` | Missing/invalid token on a `me/*` endpoint |
| `403` | Authenticated but not permitted |
| `404` | Unknown property/region/resource |
| `409` | Conflicting write (e.g. duplicate comparison name) |
| `422` | Semantically invalid (bad weights, <2 or >4 compare ids) |
| `429` | Rate limited |
| `503` | Read model temporarily unavailable (e.g. during refresh) |

---

## 10. Endpoint summary

| Method | Path | Screen | Auth |
|---|---|---|---|
| GET | `/dimensions` | Onboarding · choose | no |
| GET | `/regions` | Onboarding · location | no |
| GET·PUT | `/me/profile` | Onboarding · intent | yes |
| GET·PUT | `/me/weights` | Onboarding · weight | yes |
| PUT | `/me/dealbreakers` | Onboarding · deal-breakers | yes |
| POST | `/properties/search` | Discovery (list + map) | optional |
| GET | `/properties/{id}` | Property profile | optional |
| POST | `/properties/{id}/fit` | Profile · score + why | optional |
| POST | `/score/batch` | (internal: search, compare) | optional |
| POST | `/compare` | Comparison | optional |
| CRUD | `/me/comparisons` | Saved comparisons | yes |
| GET·PUT·DELETE | `/me/saved/{propertyId}` | Saved | yes |
| CRUD | `/me/listings` (+ submit/withdraw) | Agent listing submission | yes (`agent`) |

---

## 11. Mapping to schema & the offline/request-time split

- **Universal, cacheable** (read from `ingest.*`): property details, `cell_subscore` per dimension, `cell_proximity`. Served by `GET /properties/{id}` and the `summary`/`pin` parts of search. These can sit behind Redis and a CDN.
- **Personal, request-time** (computed with `core.aggregate.*`): the `fit` block — affordability sub-score (`PriceNormalizer` over `property.price` + `region_price_stats` + `user budget`), weighted aggregation, hard filters, confidence. Produced by `/fit`, `/score/batch`, `/compare`, and the scored part of search; optionally memoized in `app.fit_score_cache`.

The seam in the URL space mirrors the seam in the architecture: anything under `me/*` or returning a `fit` block is request-time and personal; everything else is universal read-model data.

---

## 12. Source of truth

This document is the human-readable design. The machine contract should be an **OpenAPI 3.1 spec** generated from the Spring controllers (springdoc-openapi), kept in sync via CI — so the DTOs here become annotated Java records and the examples become contract tests.

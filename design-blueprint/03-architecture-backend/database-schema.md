# Database Schema & Data Model — v1

**Status:** Design spec (v1) · **Audience:** backend engineers, DBAs, AI agents.
**Companion docs:** *Backend Systems Design*, *Fit Score Normalization Spec*, *Data Source Matrix*, *System Architecture*.
**Engine:** PostgreSQL 16 + PostGIS 3.x.

This is the contract between the two halves of the system. Design rules followed throughout:

1. **Two schemas = ownership boundary.** `ingest.*` is written only by the ingestion worker; `app.*` is written only by the API. The API reads `ingest.*` read-only. Enforced with DB roles (§9).
2. **Provenance on every value.** Every ingested row records its `source_id`, `ingested_at`, and `source_tier`. Every derived value records `confidence` and `computed_at`.
3. **Never lose source data.** Every raw table keeps a `raw jsonb` of the original payload, so re-processing never requires re-fetching.
4. **Canonical units** (§11): SRID 4326, `timestamptz` UTC, money as `numeric` + ISO currency, scores `smallint` 0–100, confidence `real` 0–1, H3 index as `bigint`.

---

## 1. Extensions, schemas, enums

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS citext;       -- case-insensitive email
-- Optional native H3 (else compute H3 in app via h3-java):
CREATE EXTENSION IF NOT EXISTS h3;
CREATE EXTENSION IF NOT EXISTS h3_postgis;

CREATE SCHEMA ingest;   -- ingestion-owned (write)
CREATE SCHEMA app;      -- api-owned (write)

-- Shared enums (public)
CREATE TYPE dimension AS ENUM (
  'flood','schools','affordability','worship','air_quality',
  'safety','markets','hospital','transit','parks','other_hazards');
CREATE TYPE amenity_type AS ENUM (
  'school','hospital','clinic','pharmacy','market','grocery',
  'place_of_worship','transit_stop','park','other');
CREATE TYPE source_tier      AS ENUM ('measured','modeled','proxy');
CREATE TYPE hazard_type      AS ENUM ('flood','storm','drought','seismic','heat','other');
CREATE TYPE property_type    AS ENUM ('apartment','detached','semi_detached','terrace',
                                      'duplex','bungalow','land','commercial','other');
CREATE TYPE tenure           AS ENUM ('sale','rent');
CREATE TYPE user_intent      AS ENUM ('buy','rent');
CREATE TYPE travel_mode      AS ENUM ('walk','drive','transit','cycle');
CREATE TYPE ingestion_status AS ENUM ('running','succeeded','failed','partial');
```

`dimension` is the user-weightable axis set. `amenity_type` is finer-grained than `dimension`; the proximity precompute maps amenity types to dimensions (`school→schools`, `place_of_worship→worship`, `market`/`grocery→markets`, `hospital`/`clinic→hospital`, `transit_stop→transit`, `park→parks`).

---

## 2. Provenance & bookkeeping (`ingest`)

```sql
CREATE TABLE ingest.data_source (
  id            smallint PRIMARY KEY,
  code          text UNIQUE NOT NULL,        -- 'nasa_power','nasa_lance','osm','openaq','acled','estate_intel'
  name          text NOT NULL,
  group_tier    text NOT NULL,               -- 'free_global' | 'paid_market'
  default_tier  source_tier NOT NULL,        -- measured/modeled/proxy default
  license       text,
  base_url      text,
  auth_type     text,                        -- 'none','earthdata','api_key'
  refresh_cron  text,                        -- expected cadence
  notes         text
);

CREATE TABLE ingest.ingestion_run (
  id              bigserial PRIMARY KEY,
  source_id       smallint NOT NULL REFERENCES ingest.data_source(id),
  started_at      timestamptz NOT NULL DEFAULT now(),
  finished_at     timestamptz,
  status          ingestion_status NOT NULL DEFAULT 'running',
  records_in      integer,
  records_written integer,
  error_detail    text,
  params          jsonb
);
CREATE INDEX ON ingest.ingestion_run (source_id, started_at DESC);
```

---

## 3. Geography backbone (`ingest`)

```sql
CREATE TABLE ingest.admin_region (
  id            bigserial PRIMARY KEY,
  parent_id     bigint REFERENCES ingest.admin_region(id),
  level         smallint NOT NULL,           -- 0 country,1 state,2 lga/city,3 ward
  code          text,
  name          text NOT NULL,
  country_code  char(2) NOT NULL,
  boundary      geometry(MultiPolygon,4326) NOT NULL,
  centroid      geometry(Point,4326),
  created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON ingest.admin_region USING gist (boundary);
CREATE INDEX ON ingest.admin_region (country_code, level);
CREATE INDEX ON ingest.admin_region (parent_id);

-- The spatial unit everything attaches to.
CREATE TABLE ingest.geo_cell (
  h3              bigint PRIMARY KEY,         -- 64-bit H3 index
  resolution      smallint NOT NULL,
  centroid        geometry(Point,4326)   NOT NULL,
  boundary        geometry(Polygon,4326) NOT NULL,
  admin_region_id bigint REFERENCES ingest.admin_region(id),
  country_code    char(2) NOT NULL,
  elevation_m     real,                       -- DEM (static)
  slope_deg       real,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON ingest.geo_cell USING gist (centroid);
CREATE INDEX ON ingest.geo_cell USING gist (boundary);
CREATE INDEX ON ingest.geo_cell (admin_region_id);
CREATE INDEX ON ingest.geo_cell (resolution);

-- Per-region scoring threshold overrides (normalization spec §13).
CREATE TABLE ingest.region_threshold_override (
  id              bigserial PRIMARY KEY,
  admin_region_id bigint REFERENCES ingest.admin_region(id),
  country_code    char(2),
  dimension       dimension NOT NULL,
  params          jsonb NOT NULL,             -- {"t_ideal":5,"t_max":25} | {"lambda":3}
  created_at      timestamptz NOT NULL DEFAULT now(),
  CHECK (admin_region_id IS NOT NULL OR country_code IS NOT NULL)
);
```

---

## 4. Raw observations (`ingest`, time-series → partitioned)

The high-volume tables are declaratively partitioned by date (see §10). `cell_h3` is a logical reference to `geo_cell.h3`; FKs on very large partitioned tables are optional for write performance.

```sql
-- Flood events (NASA LANCE daily + 2003–2025 archive)
CREATE TABLE ingest.flood_observation (
  cell_h3      bigint NOT NULL,
  observed_on  date   NOT NULL,
  flooded      boolean NOT NULL,
  extent_frac  real,                          -- fraction of cell flooded 0..1
  source_id    smallint NOT NULL,
  source_tier  source_tier NOT NULL,
  run_id       bigint,
  ingested_at  timestamptz NOT NULL DEFAULT now(),
  raw          jsonb,
  PRIMARY KEY (cell_h3, observed_on, source_id)
) PARTITION BY RANGE (observed_on);
CREATE INDEX ON ingest.flood_observation (cell_h3, observed_on);

-- Climate / weather (NASA POWER) — generic metric rows to hold any variable
CREATE TABLE ingest.climate_observation (
  cell_h3      bigint NOT NULL,
  observed_on  date   NOT NULL,
  metric       text   NOT NULL,               -- 'precip_mm','t2m_c','rh2m_pct','solar_kwh'
  value        double precision NOT NULL,
  source_id    smallint NOT NULL,
  ingested_at  timestamptz NOT NULL DEFAULT now(),
  raw          jsonb,
  PRIMARY KEY (cell_h3, observed_on, metric, source_id)
) PARTITION BY RANGE (observed_on);

-- Air quality (OpenAQ ground stations / Sentinel-5P)
CREATE TABLE ingest.air_quality_observation (
  cell_h3        bigint NOT NULL,
  observed_at    timestamptz NOT NULL,
  pm25 real, pm10 real, no2 real, o3 real, aqi integer,
  station_dist_m integer,                      -- nearest station (feeds confidence)
  source_id      smallint NOT NULL,
  source_tier    source_tier NOT NULL,
  ingested_at    timestamptz NOT NULL DEFAULT now(),
  raw            jsonb,
  PRIMARY KEY (cell_h3, observed_at, source_id)
) PARTITION BY RANGE (observed_at);

-- Other natural hazards (SEDAC / regional scales)
CREATE TABLE ingest.hazard_observation (
  cell_h3      bigint NOT NULL,
  hazard_type  hazard_type NOT NULL,
  observed_on  date,
  severity     real,                           -- normalized 0..1 per source scale
  metric       text,
  value        double precision,
  source_id    smallint NOT NULL,
  source_tier  source_tier NOT NULL,
  ingested_at  timestamptz NOT NULL DEFAULT now(),
  raw          jsonb,
  PRIMARY KEY (cell_h3, hazard_type, COALESCE(observed_on,'epoch'), source_id)
);

-- Conflict/violence events (ACLED) — point-level, drives safety density
CREATE TABLE ingest.conflict_event (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  external_id     text UNIQUE,
  event_date      date NOT NULL,
  event_type      text,
  sub_type        text,
  fatalities      integer DEFAULT 0,
  geom            geometry(Point,4326) NOT NULL,
  cell_h3         bigint,
  admin_region_id bigint,
  source_id       smallint NOT NULL,
  ingested_at     timestamptz NOT NULL DEFAULT now(),
  raw             jsonb
);
CREATE INDEX ON ingest.conflict_event USING gist (geom);
CREATE INDEX ON ingest.conflict_event (event_date);
CREATE INDEX ON ingest.conflict_event (cell_h3);

-- Region-level safety index/rate (Numbeo / NBS)
CREATE TABLE ingest.safety_observation (
  admin_region_id bigint NOT NULL REFERENCES ingest.admin_region(id),
  period          daterange NOT NULL,
  safety_index    real,                        -- 0..100 higher = safer (index sources)
  crime_rate      real,                        -- per 100k (rate sources)
  metric          text,
  source_id       smallint NOT NULL,
  source_tier     source_tier NOT NULL,
  ingested_at     timestamptz NOT NULL DEFAULT now(),
  raw             jsonb,
  PRIMARY KEY (admin_region_id, period, source_id)
);
```

---

## 5. Amenities (`ingest`)

```sql
-- OSM points of interest. tags jsonb keeps the full tag set — every bit preserved.
CREATE TABLE ingest.amenity (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  osm_type        char(1),                     -- n/w/r
  osm_id          bigint,
  amenity_type    amenity_type NOT NULL,
  subtype         text,                        -- religion=muslim, school level, etc.
  name            text,
  geom            geometry(Point,4326) NOT NULL,
  cell_h3         bigint,
  admin_region_id bigint,
  source_id       smallint NOT NULL,
  tags            jsonb,                        -- full OSM tags
  ingested_at     timestamptz NOT NULL DEFAULT now(),
  UNIQUE (osm_type, osm_id, amenity_type)
);
CREATE INDEX ON ingest.amenity USING gist (geom);
CREATE INDEX ON ingest.amenity (amenity_type);
CREATE INDEX ON ingest.amenity (cell_h3, amenity_type);
CREATE INDEX ON ingest.amenity USING gin (tags);
```

---

## 6. Properties / listings (`ingest`)

```sql
CREATE TABLE ingest.property (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  external_id     text,                        -- source listing id
  source_id       smallint NOT NULL,
  title           text,
  tenure          tenure NOT NULL,
  property_type   property_type,
  bedrooms        smallint,
  bathrooms       smallint,
  size_sqm        numeric(10,2),
  price           numeric(15,2),
  price_currency  char(3) NOT NULL DEFAULT 'NGN',
  price_period    text,                        -- rent: 'month','year'
  address         text,
  geom            geometry(Point,4326),
  cell_h3         bigint,
  admin_region_id bigint,
  status          text NOT NULL DEFAULT 'active', -- active/under_offer/sold/expired
  listing_url     text,
  photos          jsonb,                        -- ["url", ...]
  attributes      jsonb,                        -- any extra source fields
  raw             jsonb,
  origin          text NOT NULL DEFAULT 'sourced', -- 'sourced' | 'agent'
  submitted_by    uuid,                         -- agent app_user.id when origin='agent'
  submission_id   uuid,                         -- → app.listing_submission.id
  listed_at       timestamptz,
  ingested_at     timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (source_id, external_id)
);
CREATE INDEX ON ingest.property USING gist (geom);
CREATE INDEX ON ingest.property (cell_h3);
CREATE INDEX ON ingest.property (admin_region_id, property_type, status);
CREATE INDEX ON ingest.property (price);
```

A property **sits in a cell** and inherits that cell's environment/proximity sub-scores. Its **price** is property-specific and feeds the affordability sub-score, which is therefore computed at request time (§8), not stored per cell.

### 6.1 Agent listing submissions (`app` → promoted into `ingest`)

**Decision:** agents/owners submit listings in-app. To keep the ownership boundary intact, an agent submission is treated as **just another source feeding ingestion**: the API writes the submission to `app`, and an ingestion `AgentSubmissionAdapter` validates, geocodes, assigns a cell, and **promotes** approved submissions into `ingest.property` (with `origin='agent'`). The API never writes `ingest.property` directly.

```sql
CREATE TABLE app.listing_submission (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_user_id   uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  status          text NOT NULL DEFAULT 'draft',  -- draft/submitted/approved/rejected/promoted/withdrawn
  -- listing payload (mirrors property)
  title           text,
  tenure          tenure,
  property_type   property_type,
  bedrooms        smallint,
  bathrooms       smallint,
  size_sqm        numeric(10,2),
  price           numeric(15,2),
  price_currency  char(3) NOT NULL DEFAULT 'NGN',
  price_period    text,
  address         text,
  lat             double precision,
  lng             double precision,
  photos          jsonb,
  attributes      jsonb,
  -- review / promotion bookkeeping
  promoted_property_id uuid,                      -- → ingest.property.id once promoted
  review_note     text,
  submitted_at    timestamptz,
  reviewed_at     timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON app.listing_submission (agent_user_id, status);
CREATE INDEX ON app.listing_submission (status);
```

Lifecycle: `draft → submitted → approved → promoted` (or `rejected`/`withdrawn`). The agent owns the submission (edits while `draft`/`submitted`); once `promoted`, `ingest.property` is canonical and further edits create a new submission version. Approval is an `admin`/`agent`-trust gate (see Auth doc); auto-approval for trusted agents is a policy toggle, not a schema change.

---

## 7. Derived / precomputed read model (`ingest`)

These are the tables the API reads to build a Fit Score. Written by the offline sub-score job.

```sql
-- Annualized flood frequency per cell → feeds FloodNormalizer
CREATE TABLE ingest.cell_flood_summary (
  cell_h3        bigint PRIMARY KEY,
  years_observed real NOT NULL,
  event_count    integer NOT NULL,
  annual_freq    real NOT NULL,                -- event_count / years_observed
  tier_used      source_tier NOT NULL,
  computed_at    timestamptz NOT NULL DEFAULT now()
);

-- Nearest amenity + density per cell per type → feeds ProximityNormalizer
CREATE TABLE ingest.cell_proximity (
  cell_h3            bigint NOT NULL,
  amenity_type       amenity_type NOT NULL,
  nearest_amenity_id bigint REFERENCES ingest.amenity(id),
  travel_minutes     real,
  travel_mode        travel_mode NOT NULL,
  distance_m         integer,
  count_within_radius integer,
  radius_m           integer,
  computed_at        timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (cell_h3, amenity_type)
);

-- Regional price distribution → feeds PriceNormalizer (market-value component)
CREATE TABLE ingest.region_price_stats (
  admin_region_id      bigint NOT NULL,
  property_type        property_type NOT NULL,
  tenure               tenure NOT NULL,
  currency             char(3) NOT NULL,
  sample_size          integer NOT NULL,
  median_price         numeric(15,2),
  p25_price            numeric(15,2),
  p75_price            numeric(15,2),
  price_per_sqm_median numeric(15,2),
  period               daterange NOT NULL,
  computed_at          timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (admin_region_id, property_type, tenure, period)
);

-- THE central read-model table: one normalized sub-score per cell per location-based dimension.
CREATE TABLE ingest.cell_subscore (
  cell_h3      bigint NOT NULL,
  dimension    dimension NOT NULL,             -- excludes 'affordability' (request-time)
  subscore     smallint NOT NULL CHECK (subscore BETWEEN 0 AND 100),
  confidence   real     NOT NULL CHECK (confidence BETWEEN 0 AND 1),
  source_tier  source_tier NOT NULL,
  inputs       jsonb,                          -- raw values + formula/threshold used (audit & "why")
  computed_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (cell_h3, dimension)
);
CREATE INDEX ON ingest.cell_subscore (dimension, subscore);

-- Optional read optimization: one row per cell, all dims folded into jsonb.
CREATE MATERIALIZED VIEW ingest.cell_profile AS
SELECT cell_h3,
       jsonb_object_agg(dimension,
         jsonb_build_object('s',subscore,'c',confidence,'t',source_tier)) AS dims,
       min(confidence) AS min_confidence,
       max(computed_at) AS computed_at
FROM ingest.cell_subscore
GROUP BY cell_h3;
CREATE UNIQUE INDEX ON ingest.cell_profile (cell_h3);
```

---

## 8. User / personalization (`app`)

```sql
CREATE TABLE app.app_user (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email        citext UNIQUE,
  display_name text,
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app.user_profile (
  user_id          uuid PRIMARY KEY REFERENCES app.app_user(id) ON DELETE CASCADE,
  intent           user_intent NOT NULL DEFAULT 'buy',
  search_region_id bigint,                     -- logical ref → ingest.admin_region
  budget           numeric(15,2),
  budget_currency  char(3) NOT NULL DEFAULT 'NGN',
  updated_at       timestamptz NOT NULL DEFAULT now()
);

-- The priorities from onboarding. Stored raw; normalized to sum 1 in the app.
CREATE TABLE app.user_weight (
  user_id   uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  dimension dimension NOT NULL,
  weight    real NOT NULL CHECK (weight >= 0),
  PRIMARY KEY (user_id, dimension)
);

-- Hard filters (deal-breakers) — applied before scoring.
CREATE TABLE app.user_dealbreaker (
  id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  type       text NOT NULL,                    -- 'max_price','hospital_within_km','min_subscore'
  params     jsonb NOT NULL,                   -- {"value":85000000} | {"km":15} | {"dimension":"flood","min":50}
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON app.user_dealbreaker (user_id);

CREATE TABLE app.saved_property (
  user_id     uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  property_id uuid NOT NULL,                   -- logical ref → ingest.property
  note        text,
  saved_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, property_id)
);

CREATE TABLE app.comparison_set (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  name       text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE app.comparison_item (
  set_id      uuid NOT NULL REFERENCES app.comparison_set(id) ON DELETE CASCADE,
  property_id uuid NOT NULL,                   -- logical ref → ingest.property
  position    smallint,
  PRIMARY KEY (set_id, property_id)
);

-- Optional cache of computed Fit Scores (invalidate via weights_hash).
CREATE TABLE app.fit_score_cache (
  user_id      uuid NOT NULL,
  property_id  uuid NOT NULL,
  fit_score    smallint NOT NULL,
  confidence   real NOT NULL,
  breakdown    jsonb NOT NULL,                 -- per-dimension contributions for "why this score"
  weights_hash text NOT NULL,
  computed_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, property_id)
);
CREATE INDEX ON app.fit_score_cache (user_id, fit_score DESC);
```

Cross-schema references from `app` to `ingest` (e.g. `property_id`) are intentionally **logical, not FK-enforced** — it keeps the ownership boundary clean and lets the two halves deploy and migrate independently. Integrity is maintained in application code.

---

## 9. Ownership enforced by roles

```sql
CREATE ROLE homefit_ingest LOGIN;             -- the worker
CREATE ROLE homefit_api    LOGIN;             -- the API

-- Ingestion: full control of ingest, nothing in app
GRANT ALL ON SCHEMA ingest TO homefit_ingest;
GRANT ALL ON ALL TABLES IN SCHEMA ingest TO homefit_ingest;
ALTER DEFAULT PRIVILEGES IN SCHEMA ingest GRANT ALL ON TABLES TO homefit_ingest;

-- API: read-only on ingest, read-write on app
GRANT USAGE ON SCHEMA ingest TO homefit_api;
GRANT SELECT ON ALL TABLES IN SCHEMA ingest TO homefit_api;
ALTER DEFAULT PRIVILEGES IN SCHEMA ingest GRANT SELECT ON TABLES TO homefit_api;
GRANT USAGE, CREATE ON SCHEMA app TO homefit_api;
GRANT ALL ON ALL TABLES IN SCHEMA app TO homefit_api;
```

This makes the contract physical: the API **cannot** write `ingest.*` even by mistake.

**One documented exception — agent-submission promotion (§6.1).** The ingestion worker needs to read submissions and write back their status. This is granted narrowly on a single table, not a general blur of the boundary:

```sql
GRANT USAGE ON SCHEMA app TO homefit_ingest;
GRANT SELECT (id, agent_user_id, status, title, tenure, property_type, bedrooms,
              bathrooms, size_sqm, price, price_currency, price_period, address,
              lat, lng, photos, attributes)
  ON app.listing_submission TO homefit_ingest;
GRANT UPDATE (status, promoted_property_id, review_note, reviewed_at, updated_at)
  ON app.listing_submission TO homefit_ingest;   -- worker only flips status + links the result
```

---

## 10. Partitioning, retention, indexing

- **Partition** `flood_observation`, `climate_observation`, `air_quality_observation` by RANGE on their date column (e.g. yearly: `..._2025`, `..._2026`). Create next year's partition ahead of time via a scheduled job.
- **Retention:** detach and archive cold partitions to cheap storage; `cell_flood_summary` retains the derived signal, so raw history can age out without losing scores.
- **Indexes:** GIST on all geometry; GIN on every `tags`/`raw` jsonb that's queried; btree on FK and lookup columns. `cell_subscore` is small (cells × ~10 dims) and read-hot — the `cell_profile` materialized view serves the hottest read in one row.
- **Refresh** `cell_profile` (`REFRESH MATERIALIZED VIEW CONCURRENTLY`) at the end of each sub-score compute run.

---

## 11. Conventions

| Concern | Rule |
|---|---|
| Coordinates | SRID 4326 (WGS84); distances via `geography` cast or projected CRS, stored in metres |
| Time | `timestamptz`, UTC |
| Money | `numeric(15,2)` + ISO 4217 `char(3)`; never floating point |
| Scores | `smallint` 0–100 |
| Confidence | `real` 0–1 |
| H3 index | `bigint` |
| Soft enums | `text` + `jsonb` params where values evolve (deal-breakers, thresholds) |

---

## 12. How tables feed the scoring engine

| Dimension | Source tables → derived | Normalizer | Runs |
|---|---|---|---|
| flood | `flood_observation` → `cell_flood_summary` | FloodNormalizer | offline → `cell_subscore` |
| schools / worship / markets / hospital / transit / parks | `amenity` → `cell_proximity` | ProximityNormalizer | offline → `cell_subscore` |
| air_quality | `air_quality_observation` (aggregated) | AirQualityNormalizer | offline → `cell_subscore` |
| safety | `conflict_event` + `safety_observation` | SafetyNormalizer | offline → `cell_subscore` |
| other_hazards | `hazard_observation` | HazardNormalizer | offline → `cell_subscore` |
| **affordability** | `property.price` + `region_price_stats` + `user_profile.budget` | PriceNormalizer | **request-time** |
| **Fit Score** | `cell_subscore` (all) + affordability + `user_weight` + `user_dealbreaker` | WeightedAggregator + HardFilter + Confidence | **request-time** → `fit_score_cache` |

Everything location-based is precomputed and cached; only the person-dependent parts (affordability, weighting, filtering, confidence) run per request — exactly the architecture's offline/request-time split.

---

## 13. Migrations

Schema is versioned with **Flyway**, split by owner:

- `ingest` migrations live in the **ingestion Maven module** (`src/main/resources/db/migration/ingest`).
- `app` migrations live in the **api Maven module** (`.../app`).
- The `ingest` schema is the cross-module contract — version bumps there are reviewed deliberately, since the API reads against them. Treat an `ingest` schema change like an API change.
```

-- V4: amenities, properties (incl. agent submissions), and the precomputed read model.

CREATE TABLE ingest.amenity (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  osm_type        char(1),
  osm_id          bigint,
  amenity_type    amenity_type NOT NULL,
  subtype         text,
  name            text,
  geom            geometry(Point,4326) NOT NULL,
  cell_h3         bigint,
  admin_region_id bigint,
  source_id       smallint NOT NULL,
  tags            jsonb,
  ingested_at     timestamptz NOT NULL DEFAULT now(),
  UNIQUE (osm_type, osm_id, amenity_type)
);
CREATE INDEX ON ingest.amenity USING gist (geom);
CREATE INDEX ON ingest.amenity (amenity_type);
CREATE INDEX ON ingest.amenity (cell_h3, amenity_type);
CREATE INDEX ON ingest.amenity USING gin (tags);

CREATE TABLE ingest.property (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  external_id     text,
  source_id       smallint NOT NULL,
  title           text,
  tenure          tenure NOT NULL,
  property_type   property_type,
  bedrooms        smallint,
  bathrooms       smallint,
  size_sqm        numeric(10,2),
  price           numeric(15,2),
  price_currency  char(3) NOT NULL DEFAULT 'NGN',
  price_period    text,
  address         text,
  geom            geometry(Point,4326),
  cell_h3         bigint,
  admin_region_id bigint,
  status          text NOT NULL DEFAULT 'active',
  listing_url     text,
  photos          jsonb,
  attributes      jsonb,
  raw             jsonb,
  origin          text NOT NULL DEFAULT 'sourced',  -- 'sourced' | 'agent'
  submitted_by    uuid,
  submission_id   uuid,
  listed_at       timestamptz,
  ingested_at     timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (source_id, external_id)
);
CREATE INDEX ON ingest.property USING gist (geom);
CREATE INDEX ON ingest.property (cell_h3);
CREATE INDEX ON ingest.property (admin_region_id, property_type, status);
CREATE INDEX ON ingest.property (price);

-- Derived / precomputed read model (written by the offline sub-score job)
CREATE TABLE ingest.cell_flood_summary (
  cell_h3        bigint PRIMARY KEY,
  years_observed real NOT NULL,
  event_count    integer NOT NULL,
  annual_freq    real NOT NULL,
  tier_used      source_tier NOT NULL,
  computed_at    timestamptz NOT NULL DEFAULT now()
);

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

CREATE TABLE ingest.cell_subscore (
  cell_h3      bigint NOT NULL,
  dimension    dimension NOT NULL,           -- excludes 'affordability' (request-time)
  subscore     smallint NOT NULL CHECK (subscore BETWEEN 0 AND 100),
  confidence   real     NOT NULL CHECK (confidence BETWEEN 0 AND 1),
  source_tier  source_tier NOT NULL,
  inputs       jsonb,
  computed_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (cell_h3, dimension)
);
CREATE INDEX ON ingest.cell_subscore (dimension, subscore);

CREATE MATERIALIZED VIEW ingest.cell_profile AS
SELECT cell_h3,
       jsonb_object_agg(dimension,
         jsonb_build_object('s',subscore,'c',confidence,'t',source_tier)) AS dims,
       min(confidence) AS min_confidence,
       max(computed_at) AS computed_at
FROM ingest.cell_subscore
GROUP BY cell_h3
WITH NO DATA;
CREATE UNIQUE INDEX ON ingest.cell_profile (cell_h3);

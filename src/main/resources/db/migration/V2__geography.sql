-- V2: geography backbone (admin regions, H3 cells, regional threshold overrides).

CREATE TABLE ingest.admin_region (
  id            bigserial PRIMARY KEY,
  parent_id     bigint REFERENCES ingest.admin_region(id),
  level         smallint NOT NULL,
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

CREATE TABLE ingest.geo_cell (
  h3              bigint PRIMARY KEY,
  resolution      smallint NOT NULL,
  centroid        geometry(Point,4326)   NOT NULL,
  boundary        geometry(Polygon,4326) NOT NULL,
  admin_region_id bigint REFERENCES ingest.admin_region(id),
  country_code    char(2) NOT NULL,
  elevation_m     real,
  slope_deg       real,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON ingest.geo_cell USING gist (centroid);
CREATE INDEX ON ingest.geo_cell USING gist (boundary);
CREATE INDEX ON ingest.geo_cell (admin_region_id);
CREATE INDEX ON ingest.geo_cell (resolution);

CREATE TABLE ingest.region_threshold_override (
  id              bigserial PRIMARY KEY,
  admin_region_id bigint REFERENCES ingest.admin_region(id),
  country_code    char(2),
  dimension       dimension NOT NULL,
  params          jsonb NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CHECK (admin_region_id IS NOT NULL OR country_code IS NOT NULL)
);

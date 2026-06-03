-- V3: provenance + raw observation layers (time-series tables are partitioned by date,
-- each with a DEFAULT partition so inserts work out of the box).

CREATE TABLE ingest.data_source (
  id            smallint PRIMARY KEY,
  code          text UNIQUE NOT NULL,
  name          text NOT NULL,
  group_tier    text NOT NULL,
  default_tier  source_tier NOT NULL,
  license       text,
  base_url      text,
  auth_type     text,
  refresh_cron  text,
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

CREATE TABLE ingest.flood_observation (
  cell_h3      bigint NOT NULL,
  observed_on  date   NOT NULL,
  flooded      boolean NOT NULL,
  extent_frac  real,
  source_id    smallint NOT NULL,
  source_tier  source_tier NOT NULL,
  run_id       bigint,
  ingested_at  timestamptz NOT NULL DEFAULT now(),
  raw          jsonb,
  PRIMARY KEY (cell_h3, observed_on, source_id)
) PARTITION BY RANGE (observed_on);
CREATE TABLE ingest.flood_observation_default PARTITION OF ingest.flood_observation DEFAULT;
CREATE INDEX ON ingest.flood_observation (cell_h3, observed_on);

CREATE TABLE ingest.climate_observation (
  cell_h3      bigint NOT NULL,
  observed_on  date   NOT NULL,
  metric       text   NOT NULL,
  value        double precision NOT NULL,
  source_id    smallint NOT NULL,
  ingested_at  timestamptz NOT NULL DEFAULT now(),
  raw          jsonb,
  PRIMARY KEY (cell_h3, observed_on, metric, source_id)
) PARTITION BY RANGE (observed_on);
CREATE TABLE ingest.climate_observation_default PARTITION OF ingest.climate_observation DEFAULT;

CREATE TABLE ingest.air_quality_observation (
  cell_h3        bigint NOT NULL,
  observed_at    timestamptz NOT NULL,
  pm25 real, pm10 real, no2 real, o3 real, aqi integer,
  station_dist_m integer,
  source_id      smallint NOT NULL,
  source_tier    source_tier NOT NULL,
  ingested_at    timestamptz NOT NULL DEFAULT now(),
  raw            jsonb,
  PRIMARY KEY (cell_h3, observed_at, source_id)
) PARTITION BY RANGE (observed_at);
CREATE TABLE ingest.air_quality_observation_default PARTITION OF ingest.air_quality_observation DEFAULT;

CREATE TABLE ingest.hazard_observation (
  cell_h3      bigint NOT NULL,
  hazard_type  hazard_type NOT NULL,
  observed_on  date NOT NULL DEFAULT 'epoch',
  severity     real,
  metric       text,
  value        double precision,
  source_id    smallint NOT NULL,
  source_tier  source_tier NOT NULL,
  ingested_at  timestamptz NOT NULL DEFAULT now(),
  raw          jsonb,
  PRIMARY KEY (cell_h3, hazard_type, observed_on, source_id)
);

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

CREATE TABLE ingest.safety_observation (
  admin_region_id bigint NOT NULL REFERENCES ingest.admin_region(id),
  period          daterange NOT NULL,
  safety_index    real,
  crime_rate      real,
  metric          text,
  source_id       smallint NOT NULL,
  source_tier     source_tier NOT NULL,
  ingested_at     timestamptz NOT NULL DEFAULT now(),
  raw             jsonb,
  PRIMARY KEY (admin_region_id, period, source_id)
);

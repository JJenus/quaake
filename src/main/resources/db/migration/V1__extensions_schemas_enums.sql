-- V1: extensions, schemas, shared enums.
-- NOTE: native H3 extension is intentionally omitted so these migrations run on a
-- stock postgis/postgis image. H3 indexes are computed in the app via h3-java.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS citext;     -- case-insensitive email

CREATE SCHEMA IF NOT EXISTS ingest;   -- ingestion-owned (write)
CREATE SCHEMA IF NOT EXISTS app;      -- api-owned (write)

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

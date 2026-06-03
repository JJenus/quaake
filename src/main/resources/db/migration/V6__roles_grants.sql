-- V6: database roles that make the ownership boundary physical.
-- Ingestion writes ingest.*, reads nothing of app except the one documented exception.
-- API reads ingest.* (read-only) and read-writes app.*.

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'homefit_ingest') THEN
    CREATE ROLE homefit_ingest;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'homefit_api') THEN
    CREATE ROLE homefit_api;
  END IF;
END $$;

-- ingestion: full control of ingest
GRANT USAGE ON SCHEMA ingest TO homefit_ingest;
GRANT ALL ON ALL TABLES IN SCHEMA ingest TO homefit_ingest;
GRANT ALL ON ALL SEQUENCES IN SCHEMA ingest TO homefit_ingest;
ALTER DEFAULT PRIVILEGES IN SCHEMA ingest GRANT ALL ON TABLES TO homefit_ingest;
ALTER DEFAULT PRIVILEGES IN SCHEMA ingest GRANT ALL ON SEQUENCES TO homefit_ingest;

-- api: read-only on ingest, read-write on app
GRANT USAGE ON SCHEMA ingest TO homefit_api;
GRANT SELECT ON ALL TABLES IN SCHEMA ingest TO homefit_api;
ALTER DEFAULT PRIVILEGES IN SCHEMA ingest GRANT SELECT ON TABLES TO homefit_api;
GRANT USAGE, CREATE ON SCHEMA app TO homefit_api;
GRANT ALL ON ALL TABLES IN SCHEMA app TO homefit_api;
GRANT ALL ON ALL SEQUENCES IN SCHEMA app TO homefit_api;
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT ALL ON TABLES TO homefit_api;
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT ALL ON SEQUENCES TO homefit_api;

-- Documented exception: the worker reads submissions and writes back only their status.
GRANT USAGE ON SCHEMA app TO homefit_ingest;
GRANT SELECT (id, agent_user_id, status, title, tenure, property_type, bedrooms,
              bathrooms, size_sqm, price, price_currency, price_period, address,
              lat, lng, photos, attributes)
  ON app.listing_submission TO homefit_ingest;
GRANT UPDATE (status, promoted_property_id, review_note, reviewed_at, updated_at)
  ON app.listing_submission TO homefit_ingest;

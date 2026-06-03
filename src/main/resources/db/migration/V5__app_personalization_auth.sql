-- V5: api-owned schema — users, personalization, agent submissions, auth.

CREATE TABLE app.app_user (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email        citext UNIQUE,
  display_name text,
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app.user_profile (
  user_id          uuid PRIMARY KEY REFERENCES app.app_user(id) ON DELETE CASCADE,
  intent           user_intent NOT NULL DEFAULT 'buy',
  search_region_id bigint,
  budget           numeric(15,2),
  budget_currency  char(3) NOT NULL DEFAULT 'NGN',
  updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app.user_weight (
  user_id   uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  dimension dimension NOT NULL,
  weight    real NOT NULL CHECK (weight >= 0),
  PRIMARY KEY (user_id, dimension)
);

CREATE TABLE app.user_dealbreaker (
  id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  type       text NOT NULL,
  params     jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON app.user_dealbreaker (user_id);

CREATE TABLE app.saved_property (
  user_id     uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  property_id uuid NOT NULL,
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
  property_id uuid NOT NULL,
  position    smallint,
  PRIMARY KEY (set_id, property_id)
);

CREATE TABLE app.fit_score_cache (
  user_id      uuid NOT NULL,
  property_id  uuid NOT NULL,
  fit_score    smallint NOT NULL,
  confidence   real NOT NULL,
  breakdown    jsonb NOT NULL,
  weights_hash text NOT NULL,
  computed_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, property_id)
);
CREATE INDEX ON app.fit_score_cache (user_id, fit_score DESC);

-- Agent listing submissions (promoted into ingest.property by an ingestion adapter)
CREATE TABLE app.listing_submission (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_user_id   uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  status          text NOT NULL DEFAULT 'draft',
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
  promoted_property_id uuid,
  review_note     text,
  submitted_at    timestamptz,
  reviewed_at     timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON app.listing_submission (agent_user_id, status);
CREATE INDEX ON app.listing_submission (status);

-- Auth (see auth-security-design.md)
CREATE TABLE app.auth_identity (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  provider      text NOT NULL,
  provider_uid  text NOT NULL,
  password_hash text,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_uid)
);

CREATE TABLE app.refresh_token (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  token_hash  text NOT NULL UNIQUE,
  family_id   uuid NOT NULL,
  issued_at   timestamptz NOT NULL DEFAULT now(),
  expires_at  timestamptz NOT NULL,
  revoked_at  timestamptz,
  user_agent  text,
  ip_hash     text
);
CREATE INDEX ON app.refresh_token (user_id);
CREATE INDEX ON app.refresh_token (family_id);

CREATE TABLE app.user_role (
  user_id uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  role    text NOT NULL,
  PRIMARY KEY (user_id, role)
);

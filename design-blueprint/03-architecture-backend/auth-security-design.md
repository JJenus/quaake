# Authentication & Security Design — v1

**Status:** Design spec (v1) · **Audience:** backend engineers, security reviewers.
**Companion docs:** *API Surface*, *Backend Systems Design*, *Database Schema*.

Scope: identity, authentication, authorization, session/token model, and the baseline security controls for the platform. Built on Spring Security within the `api` module.

---

## 1. Guiding principles

1. **Anonymous-first, per the UX.** Discovery, property detail, scoring, and compare must work with no account. Auth is required only to *persist* (`me/*`: profile, weights, saved, comparisons). The design must never force a wall before value.
2. **Identity lives only in the `api` side.** The `ingestion` worker has no users and no auth surface — it's not internet-facing. All authn/authz is an `api`-module concern; `core` and `ingestion` stay identity-free.
3. **Stateless verification, revocable sessions.** Short-lived access tokens (stateless, fast) + long-lived refresh tokens (revocable, stored). Best of both.
4. **Least privilege at every layer** — including the database roles already defined (`homefit_api` cannot write `ingest.*`).
5. **Don't build crypto or raw OAuth yourself.** Use vetted libraries / a provider for the hard parts.

---

## 2. Identity model

A user can hold multiple login methods; the account is the stable identity.

```sql
-- app schema (api-owned)
CREATE TABLE app.auth_identity (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  provider      text NOT NULL,                 -- 'password','google','apple'
  provider_uid  text NOT NULL,                 -- email (password) or provider subject
  password_hash text,                          -- only for provider='password' (Argon2id)
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_uid)
);

CREATE TABLE app.refresh_token (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  token_hash    text NOT NULL,                 -- SHA-256 of the opaque token; never store raw
  family_id     uuid NOT NULL,                 -- rotation lineage (reuse detection)
  issued_at     timestamptz NOT NULL DEFAULT now(),
  expires_at    timestamptz NOT NULL,
  revoked_at    timestamptz,
  user_agent    text,
  ip_hash       text,                          -- hashed, for anomaly review (privacy)
  UNIQUE (token_hash)
);
CREATE INDEX ON app.refresh_token (user_id);
CREATE INDEX ON app.refresh_token (family_id);

CREATE TABLE app.user_role (
  user_id uuid NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
  role    text NOT NULL,                        -- 'user','agent','admin'
  PRIMARY KEY (user_id, role)
);
```

`app_user` (from the schema doc) gains nothing here — identity attaches to it. Roles for v1: `user` (everyone), **`agent`** (can submit listings — now active, since agents submit properties), and `admin` (moderation, incl. approving agent submissions). Ownership checks always run regardless of role.

---

## 3. Token model

| Token | Lifetime | Form | Storage | Purpose |
|---|---|---|---|---|
| **Access** | 15 min | JWT (signed) | client memory | Sent as `Authorization: Bearer`; verified statelessly |
| **Refresh** | 30 days | opaque random (256-bit) | httpOnly cookie (web) / secure storage (mobile); **hash** in `refresh_token` | Mint new access tokens; revocable |

**Access JWT claims:** `sub` (userId), `roles`, `iat`, `exp`, `jti`, `iss`, `aud`. Signed with **RS256/EdDSA** (asymmetric) so the public key can be published at `/.well-known/jwks.json` and verification needs no shared secret. Keys are rotated; `kid` in the header selects the key.

**Why this split:** access tokens are short-lived and stateless, so the hot path (every `me/*` request) verifies a signature with zero DB hits. Refresh tokens are long-lived but stored hashed, so a logout or breach can revoke them — you get revocability without checking the DB on every request.

### 3.1 Refresh rotation + reuse detection

Every refresh **rotates**: using a refresh token issues a new one and revokes the old, both sharing a `family_id`. If a *already-revoked* token from a family is presented (the signature of a stolen, replayed token), the **entire family is revoked** and the user is forced to re-authenticate. This is the standard defense against refresh-token theft.

---

## 4. Authentication flows

### 4.1 Anonymous → authenticated (the UX path)
A visitor uses the app with an **inline `scoringContext`** (API §1.1). When they choose to save, they sign up; the client may send their in-progress profile/weights so the first thing the account holds is what they already built. No friction before value.

### 4.2 Password
- **Hashing: Argon2id** (memory-hard) via Spring Security's `Argon2PasswordEncoder`. Never MD5/SHA for passwords.
- Registration: `POST /api/v1/auth/register { email, password, profile? }` → creates `app_user` + `auth_identity(provider='password')`, returns token pair.
- Login: `POST /api/v1/auth/login { email, password }` → token pair.
- Rate-limited and subject to lockout/backoff (§7).

### 4.3 Social (Google, Apple) — recommended primary for mobile
OIDC authorization-code + PKCE via **Spring Security OAuth2 Client**. The provider verifies identity; we map the verified `sub`/email to an `auth_identity`, linking to an existing `app_user` by verified email or creating one. Reduces password risk and is the lower-friction path on mobile.

### 4.4 Token lifecycle endpoints
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/register` | Create account → token pair |
| POST | `/api/v1/auth/login` | Password login → token pair |
| GET | `/api/v1/auth/oauth/{provider}` | Begin OIDC (PKCE) |
| POST | `/api/v1/auth/refresh` | Rotate refresh → new pair |
| POST | `/api/v1/auth/logout` | Revoke current refresh (family) |
| POST | `/api/v1/auth/password/forgot` · `/reset` | Single-use, time-boxed reset token (hashed, emailed) |

---

## 5. Authorization

- **Resource ownership is the core rule.** Every `me/*` resource is scoped to `sub`. A user can only read/write rows where `user_id = sub`. Enforced in the service layer; never trust a client-supplied `userId`.
- **Method security** with Spring Security: `@PreAuthorize` on `me/*` controllers; public endpoints explicitly permitted. Default-deny — a new endpoint is locked unless opted public.
- **Roles** drive coarse gates now: `agent` may create/manage listing submissions (`/me/listings`); `admin` approves/rejects them and moderates. An agent may only touch **their own** submissions (`agent_user_id = sub`); once promoted, the listing becomes ordinary `ingest.property` data the agent no longer owns directly. Fine-grained ownership checks always run regardless of role.
- **The DB role boundary stands:** even a fully compromised API process holds `homefit_api`, which is read-only on `ingest.*`. It cannot corrupt the ingested data or scores.

---

## 6. Transport & platform security

- **TLS everywhere**, HSTS; no plaintext.
- **Security headers** (Spring Security defaults + tuning): `Content-Security-Policy`, `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options: DENY`.
- **CORS:** allow-list the known web origins; mobile uses native tokens, not cookies.
- **CSRF:** the JWT-bearer API is not cookie-auth for state-changing calls, so it's CSRF-safe; *if* refresh lives in a cookie, that one endpoint uses `SameSite=Strict` + a CSRF token.
- **Secrets** in a manager (Vault / cloud secret store), never in source or images. Signing keys rotated; JWKS published.

---

## 7. Abuse, rate limiting & lockout

- **Tiered rate limits:** generous for anonymous read/scoring; strict on `auth/*` (login, register, refresh, reset). Per-IP + per-account.
- **Login protection:** exponential backoff + temporary lockout after repeated failures; constant-time credential comparison to avoid user-enumeration timing leaks. Identical responses for "no such user" and "wrong password."
- **Bot resistance** on register/login (e.g. challenge after N failures).
- Anonymous scoring is the heaviest unauthenticated surface — cap request size (compare ≤4 ids, batch ≤ N) and rate per IP.

---

## 8. Privacy & data protection

- **Minimize:** collect only email + the preferences needed to score. No special-category data.
- **Sensitive-at-rest:** password hashes (Argon2id), refresh tokens (SHA-256 hashed), IPs stored only as hashes for anomaly review.
- **User rights:** account deletion cascades all `app.*` rows (`ON DELETE CASCADE`); export endpoint returns the user's profile/weights/saved. Ingested public data is unaffected (it isn't personal).
- **Audit:** auth events (login, refresh, revoke, reset) logged with `userId`, `jti`, hashed IP, timestamp — never tokens or passwords.
- Align with **NDPR** (Nigeria Data Protection Regulation) for the launch market; the minimization above also eases GDPR if expanding.

---

## 9. What we deliberately don't build

- **No custom crypto, no hand-rolled JWT parsing** — Spring Security + a JOSE library.
- **No sessions table for access tokens** — statelessness is the point; revocation lives at the refresh layer.
- **MFA, device management, SSO/SAML** are out of v1 scope but the identity model (`auth_identity`, roles) leaves room. Add when an enterprise/agent tier demands it.

---

## 10. Library checklist (Spring)

| Concern | Choice |
|---|---|
| Framework | Spring Security 6 (resource server + OAuth2 client) |
| Access token | JWT, RS256/EdDSA, JWKS published |
| Refresh | opaque, hashed, rotating, family reuse-detection |
| Password hash | Argon2id (`Argon2PasswordEncoder`) |
| Social login | OIDC auth-code + PKCE (Google, Apple) |
| Method security | `@PreAuthorize`, default-deny |
| Rate limiting | Bucket4j or gateway-level |
| Secrets | Vault / cloud secret manager |
| Transport | TLS + HSTS + security headers |

---

## 11. One-line summary

**Anonymous-first; short-lived RS256 JWT access tokens verified statelessly + rotating, revocable, hashed refresh tokens; Argon2id passwords and OIDC social login; ownership-scoped `@PreAuthorize` authorization; and the database role boundary as a hard backstop — all confined to the `api` module so `core` and `ingestion` stay identity-free.**

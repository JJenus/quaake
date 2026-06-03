# Contributing to Quaake

This repo uses **branch-per-area**. That separation is deliberate, but it removes the
monolith's automatic drift-detection — so the rules below are what keep the parts in sync.
Read the "single source of truth" rule before changing anything.

## What goes where

| Branch | Holds | Never holds |
|---|---|---|
| `main` | Design blueprint, specs, **the API contract**, this file | Application code |
| `backend` | Java / Spring Boot code (orphan branch) | Specs (references `main`) |
| `frontend` | Nuxt / Vue code (orphan branch) | Specs (references `main`) |

`backend` and `frontend` are **orphan** branches: they share no history with `main` or each
other, and they are never merged into one another.

## The rule — single source of truth

**`main` is canonical for the design blueprint and the API contract**
(`design-blueprint/03-architecture-backend/api-surface.md`, plus the schema and auth specs).
The `backend` and `frontend` branches **consume** the contract; they do **not** redefine it.
If code and spec disagree, the spec on `main` wins until the spec is deliberately changed.

This rule exists because nothing *structural* keeps the two code branches aligned — this
document is that alignment. (If maintaining it becomes painful, see "Escape hatch".)

## Changing the API contract — always main-first

1. **Update the contract on `main`** (`api-surface.md`, and the schema/auth docs if affected)
   as its own commit/PR, so the change is reviewable in isolation.
2. **Update `backend`** to implement the new contract.
3. **Update `frontend`** to consume it.

Never change an endpoint's shape on `backend` without updating the contract on `main` first —
`frontend` has no other way to learn about it.

## Make drift catchable, not just documented

Discipline alone is fragile. As soon as the backend is real:

- Generate an **OpenAPI 3.1 spec** from the Spring controllers (springdoc-openapi); publish it
  as the machine-readable form of the contract.
- On `frontend`, **generate TypeScript types** from that OpenAPI spec (e.g. `openapi-typescript`)
  into `shared/types`. A contract change the frontend hasn't adopted then becomes a **build/type
  error**, not a silent runtime bug.
- Treat `api-surface.md` as the intent and the generated OpenAPI as the checkable form; keep them
  in step.

## Day-to-day

- Do feature work on short-lived branches off the relevant area branch
  (e.g. `backend/feature-x` → merge into `backend`). Merge back into the same area only.
- Keep `main` docs-only. Code never lands on `main`.
- Commit messages: `area: what changed` — e.g. `backend: add /properties/{id}/fit`,
  `frontend: wire property profile`, `docs: revise flood normalization`.

## Escape hatch (almost never)

Branch-per-area trades the monorepo's automatic drift-detection for clean separation. If the
contract-sync cost ever outweighs that separation, collapse to a **monorepo**: a single `main`
with `backend/`, `frontend/`, and `design-blueprint/` folders, so one PR can touch both sides
and diffs surface drift. That's a one-time `git` restructure, not a rewrite.

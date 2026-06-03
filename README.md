# Quaake — Frontend (Nuxt 4)

Nuxt 4 / Vue 3 application for HomeFit. This branch is a scaffold — implementation to follow.

The design system, component library, and Nuxt architecture live on the `main` branch:
`design-blueprint/04-frontend/frontend-design-system.md`.

## Setup (when implementing)
```bash
pnpm create nuxt@latest .      # Node: active LTS (22.x+)
```

**Stack:** Nuxt 4, Vue 3, TypeScript, Pinia, MapLibre GL, lucide-vue-next.
**Key idea:** the Pinia priorities store is the API's scoring context; pages map to the
five screens (onboarding, discovery, property profile, comparison, saved).

Route layout (see design system §8.1): public routes top-level; `(app)/` group for the
signed-in user area; `agent/` and `admin/` prefixed + role-guarded.

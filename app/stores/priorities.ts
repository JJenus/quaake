import { defineStore } from 'pinia'
import type { ScoringContext, WeightDto } from '~/shared/types/api'

/** The user's priorities. This store IS the request-time scoring context. */
export const usePriorities = defineStore('priorities', {
  state: () => ({
    weights: {
      flood: 0.30, schools: 0.25, affordability: 0.20, worship: 0.15, air_quality: 0.10,
    } as Record<string, number>,
    budget: { amount: 100_000_000, currency: 'NGN' },
  }),
  getters: {
    scoringContext(state): ScoringContext {
      const weights: WeightDto[] = Object.entries(state.weights)
        .filter(([, w]) => w > 0)
        .map(([dimension, weight]) => ({ dimension, weight }))
      return { weights, budget: state.budget }
    },
  },
  actions: {
    setWeight(dimension: string, weight: number) { this.weights[dimension] = weight },
    setBudget(amount: number) { this.budget.amount = amount },
  },
})

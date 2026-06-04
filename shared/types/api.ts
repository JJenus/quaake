// Mirrors the backend contract (design-blueprint/03-architecture-backend/api-surface.md).
// When the backend publishes an OpenAPI spec, generate this file with openapi-typescript instead.

export interface Money { amount: number; currency: string }

export interface LayerItem {
  key: string
  label: string
  subScore: number | null
  confidence: number | null
  sourceTier: string | null
  travelMinutes: number | null
  travelMode: string | null
}
export interface Layer {
  layer: string
  label: string
  layerScore: number | null
  confidence: number | null
  items: LayerItem[]
}
export interface PropertyDetail {
  id: string
  title: string
  areaLabel: string
  price: Money
  propertyType: string | null
  bedrooms: number | null
  bathrooms: number | null
  sizeSqm: number | null
  photos: string[]
  cellH3: string | null
  layers: Layer[]
}

export interface WeightDto { dimension: string; weight: number }
export interface BudgetDto { amount: number; currency: string }
export interface DealBreakerDto { type: string; params: Record<string, unknown> }
export interface ScoringContext {
  weights: WeightDto[]
  budget?: BudgetDto
  dealbreakers?: DealBreakerDto[]
}

export interface ContributionDto {
  dimension: string
  weight: number
  subScore: number
  contribution: number
}
export interface FitResponse {
  score: number
  confidence: number
  breakdown: ContributionDto[]
}

export type Dimension =
  | 'flood' | 'schools' | 'affordability' | 'worship' | 'air_quality'
  | 'safety' | 'markets' | 'hospital' | 'transit' | 'parks' | 'other_hazards'

import type { FitResponse, ScoringContext } from '~/shared/types/api'

/** POST /api/v1/properties/{id}/fit */
export function useFitScore() {
  const api = useApi()
  return (propertyId: string, context: ScoringContext) =>
    api.post<FitResponse>(`/api/v1/properties/${propertyId}/fit`, context)
}

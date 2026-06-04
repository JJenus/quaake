// Typed thin wrapper over $fetch using the configured API base.
export function useApi() {
  const base = useRuntimeConfig().public.apiBase as string
  return {
    get: <T>(path: string) => $fetch<T>(path, { baseURL: base }),
    post: <T>(path: string, body: unknown) =>
      $fetch<T>(path, { baseURL: base, method: 'POST', body }),
  }
}

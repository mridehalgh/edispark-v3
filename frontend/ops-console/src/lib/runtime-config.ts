export const DEFAULT_BACKEND_ORIGIN = 'http://localhost:8080'

export const normalizeOrigin = (origin: string) => origin.replace(/\/$/, '')

const backendOrigin = normalizeOrigin(
  import.meta.env.VITE_BACKEND_ORIGIN || DEFAULT_BACKEND_ORIGIN,
)

export const runtimeConfig = {
  backendOrigin,
  openApiUrl: `${backendOrigin}/api-docs`,
  swaggerUiUrl: `${backendOrigin}/swagger-ui.html`,
} as const

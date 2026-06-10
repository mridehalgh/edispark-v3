export type LocalBackendConfig = {
  defaultBaseUrl: 'http://localhost:8080'
  openApiPath: '/api-docs'
  overrideEnvVarName: 'VITE_API_BASE_URL'
}

export type ResolvedLocalBackendConfig = {
  baseUrl: string
  openApiUrl: string
}

export const localBackendConfig: LocalBackendConfig = {
  defaultBaseUrl: 'http://localhost:8080',
  openApiPath: '/api-docs',
  overrideEnvVarName: 'VITE_API_BASE_URL'
}

function normaliseBaseUrl(baseUrl: string) {
  return baseUrl.replace(/\/+$/, '')
}

export function resolveLocalBackendBaseUrl(overrideBaseUrl = import.meta.env.VITE_API_BASE_URL) {
  if (!overrideBaseUrl?.trim()) {
    return localBackendConfig.defaultBaseUrl
  }

  return normaliseBaseUrl(overrideBaseUrl.trim())
}

export function resolveLocalOpenApiUrl(overrideBaseUrl = import.meta.env.VITE_API_BASE_URL) {
  return `${resolveLocalBackendBaseUrl(overrideBaseUrl)}${localBackendConfig.openApiPath}`
}

export function resolveLocalBackendConfig(overrideBaseUrl = import.meta.env.VITE_API_BASE_URL): ResolvedLocalBackendConfig {
  const baseUrl = resolveLocalBackendBaseUrl(overrideBaseUrl)

  return {
    baseUrl,
    openApiUrl: `${baseUrl}${localBackendConfig.openApiPath}`
  }
}

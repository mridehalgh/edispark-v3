import { DEFAULT_BACKEND_ORIGIN, normalizeOrigin, runtimeConfig } from '@/lib/runtime-config'
import { buildEndpointCatalogue } from '@/lib/openapi-catalogue'
import type { BackendOrigin, DiscoveryResult, OpenApiDocument } from '@/lib/models'

export const resolveBackendOrigin = (
  configuredOrigin = runtimeConfig.backendOrigin,
  documentedDefault = DEFAULT_BACKEND_ORIGIN,
): BackendOrigin => {
  if (configuredOrigin) {
    return {
      baseUrl: normalizeOrigin(configuredOrigin),
      source: configuredOrigin === documentedDefault ? 'default' : 'env',
    }
  }

  return {
    baseUrl: normalizeOrigin(documentedDefault),
    source: 'default',
  }
}

export const loadOpenApiDocument = async (
  origin: BackendOrigin,
  fetchImpl: typeof fetch = fetch,
): Promise<OpenApiDocument> => {
  const response = await fetchImpl(`${origin.baseUrl}/api-docs`, {
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    throw new Error(`DISCOVERY_UNREACHABLE: ${response.status} ${response.statusText}`)
  }

  const payload = (await response.json()) as OpenApiDocument
  if (!payload || typeof payload !== 'object' || !payload.paths) {
    throw new Error('DISCOVERY_INVALID_CONTRACT: /api-docs did not return an OpenAPI path map')
  }

  return payload
}

export const discoverBackend = async (fetchImpl: typeof fetch = fetch): Promise<DiscoveryResult> => {
  const origin = resolveBackendOrigin()
  const openApiUrl = `${origin.baseUrl}/api-docs`

  try {
    const document = await loadOpenApiDocument(origin, fetchImpl)
    const catalogue = buildEndpointCatalogue(document)

    return {
      connection: {
        status: 'healthy',
        baseUrl: origin.baseUrl,
        openApiUrl,
        sourceTitle: catalogue.sourceTitle,
        sourceVersion: catalogue.sourceVersion,
        message: `Connected through ${origin.source} discovery`,
      },
      catalogue,
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Discovery failed'

    return {
      connection: {
        status: 'degraded',
        baseUrl: origin.baseUrl,
        openApiUrl,
        message: `${message}. Start the Spring Boot backend on ${origin.baseUrl} and make sure /api-docs is published.`,
      },
    }
  }
}

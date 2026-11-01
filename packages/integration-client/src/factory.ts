import { createClient } from './generated/client'
import { IntegrationClient } from './generated/sdk.gen'

export interface IntegrationClientConfig {
  baseUrl: string
  accessToken?: string | (() => string | Promise<string>)
  headers?: HeadersInit
  fetch?: typeof globalThis.fetch
}

export class IntegrationClientError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message)
    this.name = 'IntegrationClientError'
  }
}

/** Creates an isolated integration client. Keep bearer tokens in a server-side BFF. */
export function createIntegrationClient(config: IntegrationClientConfig): IntegrationClient {
  const transport = createClient({
    auth: config.accessToken,
    baseUrl: config.baseUrl,
    fetch: config.fetch,
    headers: config.headers,
    throwOnError: true,
  })
  transport.interceptors.error.use((error, response) => {
    const payload = error as { code?: unknown; message?: unknown } | undefined
    const code = typeof payload?.code === 'string' ? payload.code : 'INTEGRATION_UPSTREAM_ERROR'
    const message = typeof payload?.message === 'string' ? payload.message : 'The integration request could not be completed.'
    return new IntegrationClientError(response?.status ?? 502, code, message)
  })
  return new IntegrationClient({ client: transport })
}

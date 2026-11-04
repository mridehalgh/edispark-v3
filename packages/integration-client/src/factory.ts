import { createClient } from './generated/client'
import { IntegrationClient } from './generated/sdk.gen'

export interface IntegrationClientConfig {
  baseUrl: string
  accessToken?: string | (() => string | Promise<string>)
  headers?: HeadersInit
  fetch?: typeof globalThis.fetch
}

export class IntegrationClientError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly stage?: string,
    readonly retryable?: boolean,
    readonly recommendedAction?: string,
    readonly correlationId?: string,
    readonly details?: Record<string, unknown>,
  ) {
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
    const payload = error as {
      code?: unknown
      message?: unknown
      stage?: unknown
      retryable?: unknown
      recommendedAction?: unknown
      correlationId?: unknown
      details?: unknown
    } | undefined
    const code = typeof payload?.code === 'string' ? payload.code : 'INTEGRATION_UPSTREAM_ERROR'
    const message = typeof payload?.message === 'string' ? payload.message : 'The integration request could not be completed.'
    return new IntegrationClientError(
      response?.status ?? 502,
      code,
      message,
      typeof payload?.stage === 'string' ? payload.stage : undefined,
      typeof payload?.retryable === 'boolean' ? payload.retryable : undefined,
      typeof payload?.recommendedAction === 'string' ? payload.recommendedAction : undefined,
      typeof payload?.correlationId === 'string' ? payload.correlationId : undefined,
      isRecord(payload?.details) ? payload.details : undefined,
    )
  })
  return new IntegrationClient({ client: transport })
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

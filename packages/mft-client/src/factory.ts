import { createClient } from './generated/client'
import { MftClient } from './generated/sdk.gen'

export interface MftClientConfig {
  baseUrl: string
  accessToken?: string | (() => string | Promise<string>)
  headers?: HeadersInit
  fetch?: typeof globalThis.fetch
}

export class MftClientError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly stage?: string,
    readonly retryable?: boolean,
    readonly recommendedAction?: string,
    readonly correlationId?: string,
  ) {
    super(message)
    this.name = 'MftClientError'
  }
}

/** Creates an isolated MFT client. Keep bearer tokens in a server-side BFF. */
export function createMftClient(config: MftClientConfig): MftClient {
  const transport = createClient({
    auth: config.accessToken,
    baseUrl: config.baseUrl,
    fetch: config.fetch,
    headers: config.headers,
    throwOnError: true,
  })
  transport.interceptors.error.use((error, response) => {
    const payload = error as { code?: unknown; message?: unknown; stage?: unknown; retryable?: unknown; recommendedAction?: unknown; correlationId?: unknown } | undefined
    const code = typeof payload?.code === 'string' ? payload.code : 'MFT_UPSTREAM_ERROR'
    const message = typeof payload?.message === 'string' ? payload.message : 'The MFT request could not be completed.'
    return new MftClientError(
      response?.status ?? 502,
      code,
      message,
      typeof payload?.stage === 'string' ? payload.stage : undefined,
      typeof payload?.retryable === 'boolean' ? payload.retryable : undefined,
      typeof payload?.recommendedAction === 'string' ? payload.recommendedAction : undefined,
      typeof payload?.correlationId === 'string' ? payload.correlationId : undefined,
    )
  })
  return new MftClient({
    client: transport,
  })
}

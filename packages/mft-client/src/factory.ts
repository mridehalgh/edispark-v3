import { createClient } from './generated/client'
import { MftClient } from './generated/sdk.gen'

export interface MftClientConfig {
  baseUrl: string
  accessToken?: string | (() => string | Promise<string>)
  headers?: HeadersInit
  fetch?: typeof globalThis.fetch
}

/** Creates an isolated MFT client. Keep bearer tokens in a server-side BFF. */
export function createMftClient(config: MftClientConfig): MftClient {
  return new MftClient({
    client: createClient({
      auth: config.accessToken,
      baseUrl: config.baseUrl,
      fetch: config.fetch,
      headers: config.headers,
      throwOnError: true,
    }),
  })
}

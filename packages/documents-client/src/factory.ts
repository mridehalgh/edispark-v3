import { createClient } from "./generated/client"
import { DocumentsClient } from "./generated/sdk.gen"

export interface DocumentsClientConfig {
  /** Base URL of the Documents API or same-origin BFF route. */
  baseUrl: string
  /** OIDC access token, supplied lazily so refreshed tokens are always used. */
  accessToken?: string | (() => string | Promise<string>)
  /** Additional non-sensitive request headers, such as a correlation ID. */
  headers?: HeadersInit
  fetch?: typeof globalThis.fetch
}

export class DocumentsClientError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details?: Record<string, unknown>,
  ) {
    super(message)
    this.name = "DocumentsClientError"
  }
}

/**
 * Creates an isolated, typed Documents API client. Prefer creating this in a
 * server-side BFF so an access token never reaches browser JavaScript.
 */
export function createDocumentsClient(
  config: DocumentsClientConfig,
): DocumentsClient {
  const client = createClient({
    auth: config.accessToken,
    baseUrl: config.baseUrl,
    fetch: config.fetch,
    headers: config.headers,
    throwOnError: true,
  })
  client.interceptors.error.use((error, response) => {
    const payload = error as
      | { code?: unknown; message?: unknown; details?: unknown }
      | undefined
    const status = response && response.status >= 400 ? response.status : 502
    return new DocumentsClientError(
      status,
      typeof payload?.code === "string"
        ? payload.code
        : "DOCUMENTS_REQUEST_FAILED",
      status < 500 && typeof payload?.message === "string"
        ? payload.message
        : "The document request could not be completed.",
      status < 500 && payload?.details && typeof payload.details === "object"
        ? (payload.details as Record<string, unknown>)
        : undefined,
    )
  })
  return new DocumentsClient({ client })
}

import { createClient } from './generated/client';
import { DocumentsClient } from './generated/sdk.gen';

export interface DocumentsClientConfig {
  /** Base URL of the Documents API or same-origin BFF route. */
  baseUrl: string;
  /** OIDC access token, supplied lazily so refreshed tokens are always used. */
  accessToken?: string | (() => string | Promise<string>);
  /** Additional non-sensitive request headers, such as a correlation ID. */
  headers?: HeadersInit;
  fetch?: typeof globalThis.fetch;
}

/**
 * Creates an isolated, typed Documents API client. Prefer creating this in a
 * server-side BFF so an access token never reaches browser JavaScript.
 */
export function createDocumentsClient(config: DocumentsClientConfig): DocumentsClient {
  return new DocumentsClient({
    client: createClient({
      auth: config.accessToken,
      baseUrl: config.baseUrl,
      fetch: config.fetch,
      headers: config.headers,
      throwOnError: true,
    }),
  });
}

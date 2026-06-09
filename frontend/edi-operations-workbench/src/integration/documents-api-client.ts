import type { components } from '@/lib/api/generated/contract'

type FetchLike = typeof fetch

export type SchemaResponse = components['schemas']['SchemaResponse']
export type SchemaVersionResponse = components['schemas']['SchemaVersionResponse']
export type CreateSchemaRequest = components['schemas']['CreateSchemaRequest']
export type AddSchemaVersionRequest = components['schemas']['AddSchemaVersionRequest']
export type CreateDocumentSetRequest = components['schemas']['CreateDocumentSetRequest']
export type DocumentSetResponse = components['schemas']['DocumentSetResponse']
export type DocumentSetPageResponse = components['schemas']['DocumentSetPageResponse']
export type ErrorResponse = components['schemas']['ErrorResponse']

export class DocumentsApiError extends Error {
  readonly operationId: string
  readonly status?: number
  readonly details?: Record<string, unknown>
  readonly retryable: boolean

  constructor(options: {
    operationId: string
    message: string
    status?: number
    details?: Record<string, unknown>
    retryable?: boolean
  }) {
    super(options.message)
    this.name = 'DocumentsApiError'
    this.operationId = options.operationId
    this.status = options.status
    this.details = options.details
    this.retryable = options.retryable ?? false
  }
}

type RequestOptions = {
  operationId: string
  path: string
  init?: RequestInit
}

function buildUrl(baseUrl: string, path: string) {
  return `${baseUrl}${path}`
}

function isErrorResponse(value: unknown): value is ErrorResponse {
  return typeof value === 'object' && value !== null && 'message' in value && 'code' in value
}

async function parseErrorResponse(response: Response, operationId: string): Promise<DocumentsApiError> {
  const contentType = response.headers.get('content-type') ?? ''

  if (contentType.includes('application/json')) {
    const body = await response.json()

    if (isErrorResponse(body)) {
      return new DocumentsApiError({
        operationId,
        message: body.message,
        status: response.status,
        details: body.details,
        retryable: response.status >= 500
      })
    }
  }

  const message = await response.text()

  return new DocumentsApiError({
    operationId,
    message: message || `${operationId} failed with status ${response.status}`,
    status: response.status,
    retryable: response.status >= 500
  })
}

async function requestJson<TData>(baseUrl: string, fetcher: FetchLike, options: RequestOptions): Promise<TData> {
  try {
    const response = await fetcher(buildUrl(baseUrl, options.path), {
      ...options.init,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        ...(options.init?.headers ?? {})
      }
    })

    if (!response.ok) {
      throw await parseErrorResponse(response, options.operationId)
    }

    return response.json() as Promise<TData>
  } catch (error) {
    if (error instanceof DocumentsApiError) {
      throw error
    }

    throw new DocumentsApiError({
      operationId: options.operationId,
      message: `Could not reach the local backend at ${buildUrl(baseUrl, options.path)}.`,
      retryable: true
    })
  }
}

export function createDocumentsApiClient(options: {
  baseUrl: string
  fetch?: FetchLike
}) {
  const fetcher = options.fetch ?? fetch

  return {
    createSchema(request: CreateSchemaRequest) {
      return requestJson<SchemaResponse>(options.baseUrl, fetcher, {
        operationId: 'createSchema',
        path: '/api/schemas',
        init: {
          method: 'POST',
          body: JSON.stringify(request)
        }
      })
    },
    getSchema(schemaId: string) {
      return requestJson<SchemaResponse>(options.baseUrl, fetcher, {
        operationId: 'getSchema',
        path: `/api/schemas/${schemaId}`
      })
    },
    addSchemaVersion(schemaId: string, request: AddSchemaVersionRequest) {
      return requestJson<SchemaVersionResponse>(options.baseUrl, fetcher, {
        operationId: 'addSchemaVersion',
        path: `/api/schemas/${schemaId}/versions`,
        init: {
          method: 'POST',
          body: JSON.stringify(request)
        }
      })
    },
    getSchemaVersion(schemaId: string, versionId: string) {
      return requestJson<SchemaVersionResponse>(options.baseUrl, fetcher, {
        operationId: 'getSchemaVersion',
        path: `/api/schemas/${schemaId}/versions/${versionId}`
      })
    },
    listDocumentSets(params?: {
      limit?: number
      nextToken?: string
    }) {
      const searchParams = new URLSearchParams()

      if (params?.limit !== undefined) {
        searchParams.set('limit', `${params.limit}`)
      }
      if (params?.nextToken) {
        searchParams.set('nextToken', params.nextToken)
      }

      const suffix = searchParams.size ? `?${searchParams.toString()}` : ''

      return requestJson<DocumentSetPageResponse>(options.baseUrl, fetcher, {
        operationId: 'listDocumentSets',
        path: `/api/document-sets${suffix}`
      })
    },
    createDocumentSet(request: CreateDocumentSetRequest) {
      return requestJson<DocumentSetResponse>(options.baseUrl, fetcher, {
        operationId: 'createDocumentSet',
        path: '/api/document-sets',
        init: {
          method: 'POST',
          body: JSON.stringify(request)
        }
      })
    },
    getDocumentSet(documentSetId: string) {
      return requestJson<DocumentSetResponse>(options.baseUrl, fetcher, {
        operationId: 'getDocumentSet',
        path: `/api/document-sets/${documentSetId}`
      })
    }
  }
}

export type DocumentsApiClient = ReturnType<typeof createDocumentsApiClient>

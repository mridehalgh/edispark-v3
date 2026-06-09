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
export type DocumentType = components['schemas']['DocumentType']
export type Format = components['schemas']['Format']

export type AddDocumentRequest = {
  documentType: DocumentType
  schemaId: string
  schemaVersion: string
  content: string
  createdBy: string
  relatedDocumentId?: string
}

export type AddVersionRequest = {
  content: string
  createdBy: string
}

export type CreateDerivativeRequest = {
  sourceVersionNumber: number
  targetFormat: Format
}

export type SchemaRefResponse = {
  schemaId: string
  version: string
}

export type DocumentVersionResponse = {
  id: string
  versionNumber: number
  contentHash: string
  createdAt: string
  createdBy: string
  format: Format
  parseStatus?: string
  messageType?: string
  parseErrors: string[]
}

export type DerivativeResponse = {
  id: string
  sourceVersionId: string
  targetFormat: Format
  contentHash: string
  transformationMethod: string
  createdAt: string
}

export type DocumentResponse = {
  id: string
  type: DocumentType
  schemaRef: SchemaRefResponse
  versionCount: number
  currentVersion: DocumentVersionResponse
  derivatives: DerivativeResponse[]
}

export type ValidationResultResponse = {
  valid: boolean
  errors: Array<{
    path: string
    message: string
  }>
  warnings: Array<{
    path: string
    message: string
  }>
}

export type VersionContentResponse = {
  bytes: Uint8Array
  fileName: string
  mediaType: string
  documentSetId: string
  documentId: string
  versionNumber: number
  contentHash?: string
  format?: string
}

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

function parseContentDispositionFileName(value: string | null) {
  if (!value) {
    return 'document-content.bin'
  }

  const utf8Match = value.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }

  const quotedMatch = value.match(/filename="([^"]+)"/i)
  if (quotedMatch?.[1]) {
    return quotedMatch[1]
  }

  const plainMatch = value.match(/filename=([^;]+)/i)
  return plainMatch?.[1]?.trim() ?? 'document-content.bin'
}

async function requestBinary(baseUrl: string, fetcher: FetchLike, options: RequestOptions): Promise<VersionContentResponse> {
  try {
    const response = await fetcher(buildUrl(baseUrl, options.path), {
      ...options.init,
      headers: {
        Accept: 'application/octet-stream,application/json',
        ...(options.init?.headers ?? {})
      }
    })

    if (!response.ok) {
      throw await parseErrorResponse(response, options.operationId)
    }

    return {
      bytes: new Uint8Array(await response.arrayBuffer()),
      fileName: parseContentDispositionFileName(response.headers.get('content-disposition')),
      mediaType: response.headers.get('content-type') ?? 'application/octet-stream',
      documentSetId: response.headers.get('X-Document-Set-Id') ?? '',
      documentId: response.headers.get('X-Document-Id') ?? '',
      versionNumber: Number(response.headers.get('X-Document-Version') ?? '0'),
      contentHash: response.headers.get('X-Content-Hash') ?? undefined,
      format: response.headers.get('X-Document-Format') ?? undefined
    }
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

async function requestValidationResult(baseUrl: string, fetcher: FetchLike, options: RequestOptions): Promise<ValidationResultResponse> {
  try {
    const response = await fetcher(buildUrl(baseUrl, options.path), {
      ...options.init,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        ...(options.init?.headers ?? {})
      }
    })

    if (response.status !== 200 && response.status !== 422) {
      throw await parseErrorResponse(response, options.operationId)
    }

    return response.json() as Promise<ValidationResultResponse>
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
    },
    addDocument(documentSetId: string, request: AddDocumentRequest) {
      return requestJson<DocumentResponse>(options.baseUrl, fetcher, {
        operationId: 'addDocument',
        path: `/api/document-sets/${documentSetId}/documents`,
        init: {
          method: 'POST',
          body: JSON.stringify(request)
        }
      })
    },
    getDocument(documentSetId: string, documentId: string) {
      return requestJson<DocumentResponse>(options.baseUrl, fetcher, {
        operationId: 'getDocument',
        path: `/api/document-sets/${documentSetId}/documents/${documentId}`
      })
    },
    addVersion(documentSetId: string, documentId: string, request: AddVersionRequest) {
      return requestJson<DocumentVersionResponse>(options.baseUrl, fetcher, {
        operationId: 'addVersion',
        path: `/api/document-sets/${documentSetId}/documents/${documentId}/versions`,
        init: {
          method: 'POST',
          body: JSON.stringify(request)
        }
      })
    },
    getVersion(documentSetId: string, documentId: string, versionNumber: number) {
      return requestJson<DocumentVersionResponse>(options.baseUrl, fetcher, {
        operationId: 'getVersion',
        path: `/api/document-sets/${documentSetId}/documents/${documentId}/versions/${versionNumber}`
      })
    },
    createDerivative(documentSetId: string, documentId: string, request: CreateDerivativeRequest) {
      return requestJson<DerivativeResponse>(options.baseUrl, fetcher, {
        operationId: 'createDerivative',
        path: `/api/document-sets/${documentSetId}/documents/${documentId}/derivatives`,
        init: {
          method: 'POST',
          body: JSON.stringify(request)
        }
      })
    },
    validateDocument(documentSetId: string, documentId: string, versionNumber: number) {
      return requestValidationResult(options.baseUrl, fetcher, {
        operationId: 'validateDocument',
        path: `/api/document-sets/${documentSetId}/documents/${documentId}/versions/${versionNumber}/validate`,
        init: {
          method: 'POST',
          body: JSON.stringify({})
        }
      })
    },
    getVersionContent(documentSetId: string, documentId: string, versionNumber: number) {
      return requestBinary(options.baseUrl, fetcher, {
        operationId: 'getVersionContent',
        path: `/api/document-sets/${documentSetId}/documents/${documentId}/versions/${versionNumber}/content`
      })
    }
  }
}

export type DocumentsApiClient = ReturnType<typeof createDocumentsApiClient>

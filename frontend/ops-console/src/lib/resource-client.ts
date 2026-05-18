import type {
  DerivativeResponse,
  DocumentResponse,
  DocumentSetResponse,
  DocumentVersionResponse,
  PaginatedResponse,
  SchemaResponse,
  SchemaVersionResponse,
  ValidationResultResponse,
  VersionContentResponse,
} from '@/lib/models'

type ClientOptions = {
  baseUrl: string
  fetchImpl?: typeof fetch
}

export class ResourceClientError extends Error {
  status: number
  body: unknown

  constructor(message: string, status: number, body: unknown) {
    super(message)
    this.status = status
    this.body = body
  }
}

const readJson = async <T>(response: Response): Promise<T> => {
  const payload = (await response.json()) as T
  if (!response.ok) {
    throw new ResourceClientError(`RESOURCE_REQUEST_FAILED: ${response.status} ${response.statusText}`, response.status, payload)
  }
  return payload
}

const readTextPayload = async (response: Response) => {
  const text = await response.text()
  if (!response.ok) {
    throw new ResourceClientError(`RESOURCE_REQUEST_FAILED: ${response.status} ${response.statusText}`, response.status, text)
  }
  return text
}

const buildFileName = (contentDisposition: string | null) => {
  if (!contentDisposition) {
    return null
  }

  const match = contentDisposition.match(/filename="?([^";]+)"?/) 
  return match?.[1] || null
}

export const createResourceClient = ({ baseUrl, fetchImpl = fetch }: ClientOptions) => {
  const request = async <T>(path: string, init?: RequestInit) => {
    const response = await fetchImpl(`${baseUrl}${path}`, {
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        ...(init?.headers || {}),
      },
      ...init,
    })
    return readJson<T>(response)
  }

  return {
    listDocumentSets: (limit = 10, nextToken?: string | null) => {
      const query = new URLSearchParams({ limit: `${limit}` })
      if (nextToken) {
        query.set('nextToken', nextToken)
      }
      return request<PaginatedResponse<DocumentSetResponse>>(`/api/document-sets?${query.toString()}`)
    },
    getDocumentSet: (id: string) => request<DocumentSetResponse>(`/api/document-sets/${id}`),
    createDocumentSet: (payload: unknown) =>
      request<DocumentSetResponse>('/api/document-sets', { method: 'POST', body: JSON.stringify(payload) }),
    addDocument: (setId: string, payload: unknown) =>
      request<DocumentResponse>(`/api/document-sets/${setId}/documents`, { method: 'POST', body: JSON.stringify(payload) }),
    getDocument: (setId: string, docId: string) =>
      request<DocumentResponse>(`/api/document-sets/${setId}/documents/${docId}`),
    addVersion: (setId: string, docId: string, payload: unknown) =>
      request<DocumentVersionResponse>(`/api/document-sets/${setId}/documents/${docId}/versions`, { method: 'POST', body: JSON.stringify(payload) }),
    getVersion: (setId: string, docId: string, versionNumber: number) =>
      request<DocumentVersionResponse>(`/api/document-sets/${setId}/documents/${docId}/versions/${versionNumber}`),
    getVersionContent: async (setId: string, docId: string, versionNumber: number): Promise<VersionContentResponse> => {
      const response = await fetchImpl(`${baseUrl}/api/document-sets/${setId}/documents/${docId}/versions/${versionNumber}/content`)
      const text = await readTextPayload(response)
      return {
        endpoint: `/api/document-sets/${setId}/documents/${docId}/versions/${versionNumber}/content`,
        contentType: response.headers.get('content-type') || 'application/octet-stream',
        fileName: buildFileName(response.headers.get('content-disposition')),
        headers: Object.fromEntries(response.headers.entries()),
        text,
      }
    },
    createDerivative: (setId: string, docId: string, payload: unknown) =>
      request<DerivativeResponse>(`/api/document-sets/${setId}/documents/${docId}/derivatives`, { method: 'POST', body: JSON.stringify(payload) }),
    getDerivatives: (setId: string, docId: string) =>
      request<DerivativeResponse[]>(`/api/document-sets/${setId}/documents/${docId}/derivatives`),
    validateDocument: (setId: string, docId: string, versionNumber: number) =>
      request<ValidationResultResponse>(`/api/document-sets/${setId}/documents/${docId}/versions/${versionNumber}/validate`, { method: 'POST' }),
    createSchema: (payload: unknown) => request<SchemaResponse>('/api/schemas', { method: 'POST', body: JSON.stringify(payload) }),
    getSchema: (id: string) => request<SchemaResponse>(`/api/schemas/${id}`),
    addSchemaVersion: (schemaId: string, payload: unknown) =>
      request<SchemaVersionResponse>(`/api/schemas/${schemaId}/versions`, { method: 'POST', body: JSON.stringify(payload) }),
    getSchemaVersion: (schemaId: string, versionId: string) =>
      request<SchemaVersionResponse>(`/api/schemas/${schemaId}/versions/${versionId}`),
  }
}

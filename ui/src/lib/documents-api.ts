import type {
  DocumentResponse,
  DocumentSetResponse,
  PaginatedResponseDocumentSetResponse,
} from "@edispark/documents-client"

export type DocumentSetsPage = PaginatedResponseDocumentSetResponse

export interface DocumentSetDetail {
  documentSet: DocumentSetResponse
  documents: DocumentResponse[]
}

interface BffErrorBody {
  error?: {
    code?: string
    message?: string
  }
}

async function readJson<T>(response: Response): Promise<T> {
  if (response.ok) {
    return response.json() as Promise<T>
  }

  const body = await response.json().catch(() => null) as BffErrorBody | null
  throw new Error(body?.error?.message ?? "The documents request failed.")
}

export async function getDocumentSets(
  options: { limit?: number; nextToken?: string; signal?: AbortSignal } = {},
) {
  const search = new URLSearchParams()
  if (options.limit) search.set("limit", String(options.limit))
  if (options.nextToken) search.set("nextToken", options.nextToken)

  const query = search.size ? `?${search.toString()}` : ""
  const response = await fetch(`/api/documents${query}`, {
    credentials: "include",
    headers: { Accept: "application/json" },
    signal: options.signal,
  })

  return readJson<DocumentSetsPage>(response)
}

export async function getDocumentSetDetail(setId: string, signal?: AbortSignal) {
  const response = await fetch(`/api/documents/${encodeURIComponent(setId)}`, {
    credentials: "include",
    headers: { Accept: "application/json" },
    signal,
  })

  return readJson<DocumentSetDetail>(response)
}

export async function getDocumentVersionContent(
  setId: string,
  documentId: string,
  versionNumber: number,
  signal?: AbortSignal,
) {
  const path = [
    "/api/documents",
    encodeURIComponent(setId),
    "documents",
    encodeURIComponent(documentId),
    "versions",
    String(versionNumber),
    "content",
  ].join("/")
  const response = await fetch(path, {
    credentials: "include",
    headers: { Accept: "*/*" },
    signal,
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null) as BffErrorBody | null
    throw new Error(body?.error?.message ?? "Document content could not be loaded.")
  }

  return response.blob()
}

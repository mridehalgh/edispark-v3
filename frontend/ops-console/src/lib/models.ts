export type BackendOrigin = {
  baseUrl: string
  source: 'env' | 'default' | 'manual'
}

export type ConnectionState = {
  status: 'checking' | 'healthy' | 'degraded' | 'unavailable'
  baseUrl: string
  openApiUrl: string
  sourceTitle?: string
  sourceVersion?: string
  message?: string
}

export type EndpointCatalogueGroup =
  | 'document-sets'
  | 'documents'
  | 'schemas'
  | 'derivatives'
  | 'validation'
  | 'api-reference'

export type CapabilityKey =
  | 'listDocumentSets'
  | 'getDocumentSet'
  | 'createDocumentSet'
  | 'addDocument'
  | 'getDocument'
  | 'addVersion'
  | 'getVersion'
  | 'getVersionContent'
  | 'createDerivative'
  | 'getDerivatives'
  | 'validateDocument'
  | 'createSchema'
  | 'getSchema'
  | 'addSchemaVersion'
  | 'getSchemaVersion'

export type EndpointDescriptor = {
  capabilityKey?: CapabilityKey
  operationId?: string
  method: 'GET' | 'POST'
  path: string
  summary: string
  tag: string
  available: boolean
  group: EndpointCatalogueGroup
}

export type EndpointCatalogue = {
  sourceTitle: string
  sourceVersion: string
  groups: Record<EndpointCatalogueGroup, EndpointDescriptor[]>
  capabilities: Record<CapabilityKey, EndpointDescriptor>
}

export type OpenApiOperation = {
  operationId?: string
  summary?: string
  description?: string
  tags?: string[]
}

export type OpenApiDocument = {
  info?: {
    title?: string
    version?: string
  }
  paths?: Record<string, Partial<Record<'get' | 'post', OpenApiOperation>>>
}

export type PaginatedResponse<T> = {
  items: T[]
  pageSize: number
  hasPrevious: boolean
  hasNext: boolean
  previousToken?: string | null
  nextToken?: string | null
  previousUrl?: string | null
  nextUrl?: string | null
}

export type DocumentType =
  | 'INVOICE'
  | 'ORDER'
  | 'CREDIT_NOTE'
  | 'DEBIT_NOTE'
  | 'QUOTATION'
  | 'DESPATCH_ADVICE'
  | 'RECEIPT_ADVICE'
  | 'STATEMENT'
  | 'REMITTANCE_ADVICE'
  | 'CATALOGUE'
  | 'APPLICATION_RESPONSE'

export type SchemaFormat = 'XSD' | 'JSON_SCHEMA' | 'RELAXNG'
export type Format = 'XML' | 'JSON' | 'PDF' | 'EDI'

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
  parseStatus?: string | null
  messageType?: string | null
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

export type DocumentSetResponse = {
  id: string
  createdAt: string
  createdBy: string
  metadata?: Record<string, string> | null
  documents: Array<{
    id: string
    type: DocumentType
    versionCount: number
  }>
}

export type SchemaResponse = {
  id: string
  name: string
  format: SchemaFormat
  versions: Array<{
    id: string
    versionIdentifier: string
    createdAt: string
    deprecated: boolean
  }>
}

export type SchemaVersionResponse = {
  id: string
  versionIdentifier: string
  createdAt: string
  deprecated: boolean
}

export type ValidationResultResponse = {
  valid: boolean
  errors: Array<{ path: string; message: string }>
  warnings: Array<{ path: string; message: string }>
}

export type VersionContentResponse = {
  endpoint: string
  contentType: string
  fileName: string | null
  headers: Record<string, string>
  text: string
}

export type MutationDebugState<TInput, TRequest, TResponse> = {
  input: TInput
  requestPreview?: TRequest
  response?: TResponse
  status: 'idle' | 'submitting' | 'success' | 'error'
  errorMessage?: string
}

export type DiscoveryResult = {
  connection: ConnectionState
  catalogue?: EndpointCatalogue
}

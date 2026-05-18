import type {
  ConnectionState,
  DocumentResponse,
  DocumentSetResponse,
  EndpointCatalogue,
  SchemaResponse,
  SchemaVersionResponse,
} from '@/lib/models'

export const createConnectionViewModel = (
  connection: ConnectionState,
  catalogue?: EndpointCatalogue,
) => ({
  baseUrl: connection.baseUrl,
  sourceTitle: catalogue?.sourceTitle || connection.sourceTitle || 'Waiting for /api-docs discovery',
  sourceVersion: catalogue?.sourceVersion || connection.sourceVersion || 'unknown',
})

export const createDocumentSetListItemModel = (documentSet: DocumentSetResponse) => ({
  id: documentSet.id,
  createdAt: documentSet.createdAt,
  createdBy: documentSet.createdBy,
  documentIds: documentSet.documents.map((document) => document.id),
  documentTypes: documentSet.documents.map((document) => document.type),
})

export const createDocumentSetDetailModel = (documentSet: DocumentSetResponse) => ({
  id: documentSet.id,
  createdAt: documentSet.createdAt,
  createdBy: documentSet.createdBy,
  metadata: documentSet.metadata || {},
  documents: documentSet.documents.map((document) => ({
    id: document.id,
    type: document.type,
    versionCount: document.versionCount,
  })),
})

export const createDocumentDetailModel = (document: DocumentResponse) => ({
  id: document.id,
  type: document.type,
  schemaId: document.schemaRef.schemaId,
  schemaVersion: document.schemaRef.version,
  versionCount: document.versionCount,
  currentVersionId: document.currentVersion.id,
  currentVersionNumber: document.currentVersion.versionNumber,
  messageType: document.currentVersion.messageType,
  parseStatus: document.currentVersion.parseStatus,
  derivativeIds: document.derivatives.map((derivative) => derivative.id),
  derivativeFormats: document.derivatives.map((derivative) => derivative.targetFormat),
})

export const createSchemaDetailModel = (schema: SchemaResponse) => ({
  id: schema.id,
  name: schema.name,
  format: schema.format,
  versions: schema.versions.map((version) => ({
    id: version.id,
    versionIdentifier: version.versionIdentifier,
    createdAt: version.createdAt,
    deprecated: version.deprecated,
  })),
})

export const createSchemaVersionDetailModel = (schemaVersion: SchemaVersionResponse) => ({
  id: schemaVersion.id,
  versionIdentifier: schemaVersion.versionIdentifier,
  createdAt: schemaVersion.createdAt,
  deprecated: schemaVersion.deprecated,
})

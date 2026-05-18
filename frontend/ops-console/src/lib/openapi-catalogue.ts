import type {
  CapabilityKey,
  EndpointCatalogue,
  EndpointCatalogueGroup,
  EndpointDescriptor,
  OpenApiDocument,
} from '@/lib/models'

type ExpectedOperation = Omit<EndpointDescriptor, 'available' | 'operationId' | 'tag'> & {
  capabilityKey: CapabilityKey
  matchPath: RegExp
}

const groups = (): Record<EndpointCatalogueGroup, EndpointDescriptor[]> => ({
  'document-sets': [],
  documents: [],
  schemas: [],
  derivatives: [],
  validation: [],
  'api-reference': [],
})

const expectedOperations: ExpectedOperation[] = [
  { capabilityKey: 'listDocumentSets', method: 'GET', path: '/api/document-sets', summary: 'List document sets', group: 'document-sets', matchPath: /^\/api\/document-sets$/ },
  { capabilityKey: 'createDocumentSet', method: 'POST', path: '/api/document-sets', summary: 'Create document set', group: 'document-sets', matchPath: /^\/api\/document-sets$/ },
  { capabilityKey: 'getDocumentSet', method: 'GET', path: '/api/document-sets/{id}', summary: 'Get document set', group: 'document-sets', matchPath: /^\/api\/document-sets\/\{id\}$/ },
  { capabilityKey: 'addDocument', method: 'POST', path: '/api/document-sets/{setId}/documents', summary: 'Add document', group: 'documents', matchPath: /^\/api\/document-sets\/\{setId\}\/documents$/ },
  { capabilityKey: 'getDocument', method: 'GET', path: '/api/document-sets/{setId}/documents/{docId}', summary: 'Get document', group: 'documents', matchPath: /^\/api\/document-sets\/\{setId\}\/documents\/\{docId\}$/ },
  { capabilityKey: 'addVersion', method: 'POST', path: '/api/document-sets/{setId}/documents/{docId}/versions', summary: 'Add version', group: 'documents', matchPath: /^\/api\/document-sets\/\{setId\}\/documents\/\{docId\}\/versions$/ },
  { capabilityKey: 'getVersion', method: 'GET', path: '/api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}', summary: 'Get version', group: 'documents', matchPath: /^\/api\/document-sets\/\{setId\}\/documents\/\{docId\}\/versions\/\{versionNumber\}$/ },
  { capabilityKey: 'getVersionContent', method: 'GET', path: '/api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}/content', summary: 'Get version content', group: 'documents', matchPath: /^\/api\/document-sets\/\{setId\}\/documents\/\{docId\}\/versions\/\{versionNumber\}\/content$/ },
  { capabilityKey: 'createDerivative', method: 'POST', path: '/api/document-sets/{setId}/documents/{docId}/derivatives', summary: 'Create derivative', group: 'derivatives', matchPath: /^\/api\/document-sets\/\{setId\}\/documents\/\{docId\}\/derivatives$/ },
  { capabilityKey: 'getDerivatives', method: 'GET', path: '/api/document-sets/{setId}/documents/{docId}/derivatives', summary: 'List derivatives', group: 'derivatives', matchPath: /^\/api\/document-sets\/\{setId\}\/documents\/\{docId\}\/derivatives$/ },
  { capabilityKey: 'validateDocument', method: 'POST', path: '/api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}/validate', summary: 'Validate document', group: 'validation', matchPath: /^\/api\/document-sets\/\{setId\}\/documents\/\{docId\}\/versions\/\{versionNumber\}\/validate$/ },
  { capabilityKey: 'createSchema', method: 'POST', path: '/api/schemas', summary: 'Create schema', group: 'schemas', matchPath: /^\/api\/schemas$/ },
  { capabilityKey: 'getSchema', method: 'GET', path: '/api/schemas/{id}', summary: 'Get schema', group: 'schemas', matchPath: /^\/api\/schemas\/\{id\}$/ },
  { capabilityKey: 'addSchemaVersion', method: 'POST', path: '/api/schemas/{schemaId}/versions', summary: 'Add schema version', group: 'schemas', matchPath: /^\/api\/schemas\/\{schemaId\}\/versions$/ },
  { capabilityKey: 'getSchemaVersion', method: 'GET', path: '/api/schemas/{schemaId}/versions/{versionId}', summary: 'Get schema version', group: 'schemas', matchPath: /^\/api\/schemas\/\{schemaId\}\/versions\/\{versionId\}$/ },
]

const inferGroup = (path: string, tag: string): EndpointCatalogueGroup => {
  const normalizedTag = tag.toLowerCase()

  if (path.includes('/validate') || normalizedTag.includes('validation')) {
    return 'validation'
  }
  if (path.includes('/derivatives')) {
    return 'derivatives'
  }
  if (path.startsWith('/api/schemas')) {
    return 'schemas'
  }
  if (path.includes('/documents')) {
    return 'documents'
  }
  if (path.startsWith('/api/document-sets')) {
    return 'document-sets'
  }

  return 'api-reference'
}

const findCapability = (method: 'GET' | 'POST', path: string) =>
  expectedOperations.find((operation) => operation.method === method && operation.matchPath.test(path))

export const buildEndpointCatalogue = (document: OpenApiDocument): EndpointCatalogue => {
  if (!document.paths || typeof document.paths !== 'object') {
    throw new Error('DISCOVERY_INVALID_CONTRACT: OpenAPI document is missing paths')
  }

  const grouped = groups()
  const capabilities = {} as EndpointCatalogue['capabilities']

  Object.entries(document.paths).forEach(([path, operations]) => {
    ;(['get', 'post'] as const).forEach((methodKey) => {
      const operation = operations?.[methodKey]
      if (!operation) {
        return
      }

      const method = methodKey.toUpperCase() as 'GET' | 'POST'
      const tag = operation.tags?.[0] || 'API'
      const capability = findCapability(method, path)

      const descriptor: EndpointDescriptor = {
        capabilityKey: capability?.capabilityKey,
        operationId: operation.operationId,
        method,
        path,
        summary: operation.summary || capability?.summary || `${method} ${path}`,
        tag,
        available: true,
        group: capability?.group || inferGroup(path, tag),
      }

      grouped[descriptor.group].push(descriptor)

      if (capability) {
        capabilities[capability.capabilityKey] = descriptor
      }
    })
  })

  expectedOperations.forEach((operation) => {
    if (capabilities[operation.capabilityKey]) {
      return
    }

    capabilities[operation.capabilityKey] = {
      capabilityKey: operation.capabilityKey,
      method: operation.method,
      path: operation.path,
      summary: operation.summary,
      tag: 'Unavailable',
      available: false,
      group: operation.group,
    }
  })

  return {
    sourceTitle: document.info?.title || 'Local OpenAPI contract',
    sourceVersion: document.info?.version || 'unknown',
    groups: grouped,
    capabilities,
  }
}

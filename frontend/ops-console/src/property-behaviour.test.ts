import fc from 'fast-check'
import { describe, expect, it } from 'vitest'

import { supportedMessageTypes, messageFraming } from '@/lib/message-types'
import type {
  ConnectionState,
  DocumentType,
  Format,
  OpenApiDocument,
  PaginatedResponse,
  SchemaFormat,
} from '@/lib/models'
import {
  createConnectionViewModel,
  createDocumentDetailModel,
  createDocumentSetDetailModel,
  createDocumentSetListItemModel,
  createSchemaDetailModel,
  createSchemaVersionDetailModel,
} from '@/lib/ops-view-models'
import {
  buildAddDocumentRequest,
  buildAddSchemaVersionRequest,
  buildAddVersionRequest,
  buildCreateDerivativeRequest,
  buildCreateDocumentSetRequest,
  buildCreateSchemaRequest,
  buildMutationFailureState,
  buildValidateDocumentState,
  encodePayload,
  type AddDocumentFormInput,
  type AddSchemaVersionFormInput,
  type AddVersionFormInput,
  type CreateDocumentSetFormInput,
  type CreateSchemaFormInput,
  type CreateDerivativeFormInput,
  type ValidateDocumentFormInput,
} from '@/lib/payload-adapter'
import { resolveBackendOrigin } from '@/lib/discovery'
import { buildEndpointCatalogue, expectedOperations } from '@/lib/openapi-catalogue'
import { createResourceClient } from '@/lib/resource-client'
import { surfaceAvailable } from '@/lib/view-availability'

const propertyRuns = 100
const alphaNumeric = [...'abcdefghijklmnopqrstuvwxyz0123456789-']
const payloadCharacters = [..."ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 +-:_./'\n\r"]
const idArbitrary = fc.array(fc.constantFrom(...alphaNumeric), { minLength: 1, maxLength: 12 }).map((chars) => chars.join(''))
const optionalIdArbitrary = fc.option(idArbitrary, { nil: '' })
const isoDateArbitrary = fc.date().map((value) => value.toISOString())
const metadataArbitrary = fc.dictionary(idArbitrary, idArbitrary, { maxKeys: 4 })
const payloadTextArbitrary = fc.array(fc.constantFrom(...payloadCharacters), { maxLength: 240 }).map((chars) => chars.join(''))
const messageTypeArbitrary = fc.constantFrom(...supportedMessageTypes)
const documentTypeArbitrary = fc.constantFrom<DocumentType>(
  'INVOICE',
  'ORDER',
  'CREDIT_NOTE',
  'DEBIT_NOTE',
  'QUOTATION',
  'DESPATCH_ADVICE',
  'RECEIPT_ADVICE',
  'STATEMENT',
  'REMITTANCE_ADVICE',
  'CATALOGUE',
  'APPLICATION_RESPONSE',
)
const schemaFormatArbitrary = fc.constantFrom<SchemaFormat>('XSD', 'JSON_SCHEMA', 'RELAXNG')
const formatArbitrary = fc.constantFrom<Format>('XML', 'JSON', 'PDF', 'EDI')
const originArbitrary = fc.record({
  protocol: fc.constantFrom('http', 'https'),
  host: fc.array(fc.constantFrom(...alphaNumeric), { minLength: 3, maxLength: 10 }).map((chars) => chars.join('')),
  topLevelDomain: fc.constantFrom('local', 'test', 'example'),
  port: fc.option(fc.integer({ min: 1, max: 65535 }), { nil: undefined }),
  trailingSlash: fc.boolean(),
}).map(({ protocol, host, topLevelDomain, port, trailingSlash }) => {
  const suffix = port ? `:${port}` : ''
  return `${protocol}://${host}.${topLevelDomain}${suffix}${trailingSlash ? '/' : ''}`
})

const createSchemaInputArbitrary = fc.record<CreateSchemaFormInput>({
  name: idArbitrary,
  format: schemaFormatArbitrary,
})
const addSchemaVersionInputArbitrary = fc.record<AddSchemaVersionFormInput>({
  schemaId: idArbitrary,
  versionIdentifier: idArbitrary,
  definitionText: payloadTextArbitrary,
})
const createDocumentSetInputArbitrary = fc.record<CreateDocumentSetFormInput>({
  documentType: documentTypeArbitrary,
  schemaId: idArbitrary,
  schemaVersion: idArbitrary,
  createdBy: idArbitrary,
  messageType: messageTypeArbitrary,
  contentText: payloadTextArbitrary,
  metadataText: fc.array(fc.tuple(idArbitrary, idArbitrary), { maxLength: 4 }).map((entries) => entries.map(([key, value]) => `${key}=${value}`).join('\n')),
})
const addDocumentInputArbitrary = fc.record<AddDocumentFormInput>({
  setId: idArbitrary,
  documentType: documentTypeArbitrary,
  schemaId: idArbitrary,
  schemaVersion: idArbitrary,
  createdBy: idArbitrary,
  messageType: messageTypeArbitrary,
  relatedDocumentId: optionalIdArbitrary,
  contentText: payloadTextArbitrary,
})
const addVersionInputArbitrary = fc.record<AddVersionFormInput>({
  setId: idArbitrary,
  docId: idArbitrary,
  createdBy: idArbitrary,
  messageType: messageTypeArbitrary,
  contentText: payloadTextArbitrary,
})
const createDerivativeInputArbitrary = fc.record<CreateDerivativeFormInput>({
  setId: idArbitrary,
  docId: idArbitrary,
  sourceVersionNumber: fc.integer({ min: 1, max: 99 }),
  targetFormat: formatArbitrary,
})
const validateDocumentInputArbitrary = fc.record<ValidateDocumentFormInput>({
  setId: idArbitrary,
  docId: idArbitrary,
  versionNumber: fc.integer({ min: 1, max: 99 }),
})

const workflowInputArbitrary = fc.oneof(
  createDocumentSetInputArbitrary,
  addDocumentInputArbitrary,
  addVersionInputArbitrary,
)

const failedSubmissionCaseArbitrary = fc.oneof(
  createSchemaInputArbitrary.map((input) => ({ input, requestPreview: buildCreateSchemaRequest(input) })),
  addSchemaVersionInputArbitrary.map((input) => ({ input, requestPreview: buildAddSchemaVersionRequest(input) })),
  createDocumentSetInputArbitrary.map((input) => ({ input, requestPreview: buildCreateDocumentSetRequest(input) })),
  addDocumentInputArbitrary.map((input) => ({ input, requestPreview: buildAddDocumentRequest(input) })),
  addVersionInputArbitrary.map((input) => ({ input, requestPreview: buildAddVersionRequest(input) })),
  createDerivativeInputArbitrary.map((input) => ({ input, requestPreview: buildCreateDerivativeRequest(input) })),
  validateDocumentInputArbitrary.map((input) => ({ input, requestPreview: input })),
)

const documentSetPayloadArbitrary = fc.record({
  id: idArbitrary,
  createdAt: isoDateArbitrary,
  createdBy: idArbitrary,
  metadata: fc.option(metadataArbitrary, { nil: null }),
  documents: fc.array(fc.record({
    id: idArbitrary,
    type: documentTypeArbitrary,
    versionCount: fc.integer({ min: 0, max: 20 }),
  }), { maxLength: 5 }),
})

const documentPayloadArbitrary = fc.record({
  id: idArbitrary,
  type: documentTypeArbitrary,
  schemaRef: fc.record({ schemaId: idArbitrary, version: idArbitrary }),
  versionCount: fc.integer({ min: 0, max: 20 }),
  currentVersion: fc.record({
    id: idArbitrary,
    versionNumber: fc.integer({ min: 1, max: 20 }),
    contentHash: idArbitrary,
    createdAt: isoDateArbitrary,
    createdBy: idArbitrary,
    format: formatArbitrary,
    parseStatus: fc.option(idArbitrary, { nil: null }),
    messageType: fc.option(messageTypeArbitrary, { nil: null }),
    parseErrors: fc.array(fc.string(), { maxLength: 3 }),
  }),
  derivatives: fc.array(fc.record({
    id: idArbitrary,
    sourceVersionId: idArbitrary,
    targetFormat: formatArbitrary,
    contentHash: idArbitrary,
    transformationMethod: idArbitrary,
    createdAt: isoDateArbitrary,
  }), { maxLength: 4 }),
})

const schemaPayloadArbitrary = fc.record({
  id: idArbitrary,
  name: idArbitrary,
  format: schemaFormatArbitrary,
  versions: fc.array(fc.record({
    id: idArbitrary,
    versionIdentifier: idArbitrary,
    createdAt: isoDateArbitrary,
    deprecated: fc.boolean(),
  }), { maxLength: 5 }),
})

const schemaVersionPayloadArbitrary = fc.record({
  id: idArbitrary,
  versionIdentifier: idArbitrary,
  createdAt: isoDateArbitrary,
  deprecated: fc.boolean(),
})

const connectionArbitrary = fc.record<ConnectionState>({
  status: fc.constantFrom('healthy', 'degraded', 'unavailable', 'checking'),
  baseUrl: originArbitrary,
  openApiUrl: originArbitrary.map((origin) => `${origin.replace(/\/$/, '')}/api-docs`),
  sourceTitle: fc.option(fc.string(), { nil: undefined }),
  sourceVersion: fc.option(fc.string(), { nil: undefined }),
  message: fc.option(fc.string(), { nil: undefined }),
})

const capabilityKeys = expectedOperations.map((operation) => operation.capabilityKey)
const operatorTags = {
  'document-sets': 'Document Sets',
  documents: 'Documents',
  schemas: 'Schemas',
  derivatives: 'Derivatives',
  validation: 'Validation',
  'api-reference': 'API Reference',
} as const

const buildContractDocument = (operations: typeof expectedOperations): OpenApiDocument => {
  const paths: NonNullable<OpenApiDocument['paths']> = {}

  operations.forEach((operation) => {
    const method = operation.method.toLowerCase() as 'get' | 'post'
    const current = paths[operation.path] || {}
    current[method] = {
      operationId: `${operation.capabilityKey}-${method}`,
      summary: operation.summary,
      tags: [operatorTags[operation.group]],
    }
    paths[operation.path] = current
  })

  return {
    info: {
      title: 'Generated contract',
      version: '1.0.0',
    },
    paths,
  }
}

const contractSubsetArbitrary = fc.subarray(expectedOperations, { minLength: 1 }).map((operations) => ({
  operations,
  document: buildContractDocument(operations),
}))

const decodeBase64Payload = (base64: string) => {
  const binary = atob(base64)
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

describe('ops console property behaviour', () => {
  it('Feature: edi-operations-frontend, Property 1: Supported message types remain selectable and visible', () => {
    fc.assert(
      fc.property(messageTypeArbitrary, workflowInputArbitrary, (messageType, workflowInput) => {
        const selectedWorkflow = { ...workflowInput, messageType }
        const framing = messageFraming.find((entry) => entry.messageType === messageType)

        expect(selectedWorkflow.messageType).toBe(messageType)
        expect(supportedMessageTypes).toContain(messageType)
        expect(framing?.displayLabel).toBe(messageType)
      }),
      { numRuns: propertyRuns },
    )
  })

  it('Feature: edi-operations-frontend, Property 2: Rendered resource views reflect backend payloads', () => {
    fc.assert(
      fc.property(
        documentSetPayloadArbitrary,
        documentPayloadArbitrary,
        schemaPayloadArbitrary,
        schemaVersionPayloadArbitrary,
        connectionArbitrary,
        createDocumentSetInputArbitrary,
        (documentSet, document, schema, schemaVersion, connection, workflowInput) => {
          const listItem = createDocumentSetListItemModel(documentSet)
          const detailItem = createDocumentSetDetailModel(documentSet)
          const documentDetail = createDocumentDetailModel(document)
          const schemaDetail = createSchemaDetailModel(schema)
          const schemaVersionDetail = createSchemaVersionDetailModel(schemaVersion)
          const connectionView = createConnectionViewModel(connection)
          const requestPreview = buildCreateDocumentSetRequest(workflowInput)
          const responseState = buildValidateDocumentState(workflowInput, requestPreview, documentSet)

          expect(listItem.id).toBe(documentSet.id)
          expect(listItem.createdBy).toBe(documentSet.createdBy)
          expect(listItem.documentIds).toEqual(documentSet.documents.map((entry) => entry.id))
          expect(listItem.documentTypes).toEqual(documentSet.documents.map((entry) => entry.type))

          expect(detailItem.id).toBe(documentSet.id)
          expect(detailItem.documents).toEqual(documentSet.documents)
          expect(detailItem.metadata).toEqual(documentSet.metadata || {})

          expect(documentDetail.id).toBe(document.id)
          expect(documentDetail.schemaId).toBe(document.schemaRef.schemaId)
          expect(documentDetail.schemaVersion).toBe(document.schemaRef.version)
          expect(documentDetail.currentVersionId).toBe(document.currentVersion.id)
          expect(documentDetail.currentVersionNumber).toBe(document.currentVersion.versionNumber)
          expect(documentDetail.derivativeIds).toEqual(document.derivatives.map((entry) => entry.id))

          expect(schemaDetail.id).toBe(schema.id)
          expect(schemaDetail.name).toBe(schema.name)
          expect(schemaDetail.versions).toEqual(schema.versions)

          expect(schemaVersionDetail).toEqual(schemaVersion)
          expect(connectionView.baseUrl).toBe(connection.baseUrl)
          expect(responseState.requestPreview).toEqual(requestPreview)
          expect(responseState.response).toEqual(documentSet)
        },
      ),
      { numRuns: propertyRuns },
    )
  })

  it('Feature: edi-operations-frontend, Property 3: Pagination preserves continuation semantics', async () => {
    await fc.assert(
      fc.asyncProperty(fc.option(idArbitrary, { nil: null }), async (nextToken) => {
        const requestedUrls: string[] = []
        const page: PaginatedResponse<{ id: string }> = {
          items: [{ id: 'page-item' }],
          pageSize: 8,
          hasPrevious: false,
          hasNext: Boolean(nextToken),
          previousToken: null,
          nextToken,
          previousUrl: null,
          nextUrl: null,
        }
        const resourceClient = createResourceClient({
          baseUrl: 'http://localhost:8080',
          fetchImpl: async (input) => {
            requestedUrls.push(typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url)
            return new Response(JSON.stringify(page), {
              status: 200,
              headers: { 'Content-Type': 'application/json' },
            })
          },
        })

        const firstPage = await resourceClient.listDocumentSets(8, null)
        await resourceClient.listDocumentSets(8, firstPage.nextToken)

        const followUpRequest = new URL(requestedUrls[1])
        expect(followUpRequest.searchParams.get('nextToken')).toBe(firstPage.nextToken)
      }),
      { numRuns: propertyRuns },
    )
  })

  it('Feature: edi-operations-frontend, Property 4: Payload preview encoding is a round trip', () => {
    fc.assert(
      fc.property(payloadTextArbitrary, (payloadText) => {
        const encoded = encodePayload(payloadText)

        expect(encoded.rawText).toBe(payloadText)
        expect(decodeBase64Payload(encoded.base64)).toBe(payloadText)
      }),
      { numRuns: propertyRuns },
    )
  })

  it('Feature: edi-operations-frontend, Property 5: Failed submissions preserve operator inputs', () => {
    fc.assert(
      fc.property(
        failedSubmissionCaseArbitrary,
        fc.string(),
        ({ input, requestPreview }, errorMessage) => {
          const failureState = buildMutationFailureState(input, errorMessage, requestPreview)

          expect(failureState.input).toEqual(input)
          expect(failureState.status).toBe('error')
          expect(failureState.errorMessage).toBe(errorMessage)
          expect(failureState.requestPreview).toEqual(requestPreview)
        },
      ),
      { numRuns: propertyRuns },
    )
  })

  it('Feature: local-api-discovery, Property 6: Configured origin resolution is deterministic', () => {
    fc.assert(
      fc.property(fc.option(originArbitrary, { nil: '' }), originArbitrary, (configuredOrigin, documentedDefault) => {
        const firstResolution = resolveBackendOrigin(configuredOrigin, documentedDefault)
        const secondResolution = resolveBackendOrigin(configuredOrigin, documentedDefault)

        expect(firstResolution).toEqual(secondResolution)
        if (configuredOrigin) {
          expect(firstResolution.baseUrl).toBe(configuredOrigin.replace(/\/$/, ''))
          expect(firstResolution.source).toBe(
            configuredOrigin === documentedDefault ? 'default' : 'env',
          )
          return
        }

        expect(firstResolution.baseUrl).toBe(documentedDefault.replace(/\/$/, ''))
        expect(firstResolution.source).toBe('default')
      }),
      { numRuns: propertyRuns },
    )
  })

  it('Feature: local-api-discovery, Property 7: Endpoint catalogue entries are contract-derived', () => {
    fc.assert(
      fc.property(contractSubsetArbitrary, ({ document, operations }) => {
        const catalogue = buildEndpointCatalogue(document)
        const catalogueEntries = Object.values(catalogue.groups).flat()

        catalogueEntries.forEach((entry) => {
          const sourceOperation = document.paths?.[entry.path]?.[entry.method.toLowerCase() as 'get' | 'post']
          expect(sourceOperation).toBeDefined()

          const matchingEntries = catalogueEntries.filter((candidate) => candidate.method === entry.method && candidate.path === entry.path)
          expect(matchingEntries).toHaveLength(1)
        })

        operations.forEach((operation) => {
          const descriptor = catalogue.capabilities[operation.capabilityKey]
          expect(descriptor.available).toBe(true)
          expect(descriptor.group).toBe(operation.group)
          expect(descriptor.path).toBe(operation.path)
          expect(descriptor.method).toBe(operation.method)
        })
      }),
      { numRuns: propertyRuns },
    )
  })

  it('Feature: local-api-discovery, Property 8: Unavailable or invalid contract operations are rejected safely', () => {
    fc.assert(
      fc.property(contractSubsetArbitrary, ({ document, operations }) => {
        const catalogue = buildEndpointCatalogue(document)
        const includedCapabilities = new Set(operations.map((operation) => operation.capabilityKey))

        capabilityKeys.forEach((capabilityKey) => {
          expect(catalogue.capabilities[capabilityKey].available).toBe(includedCapabilities.has(capabilityKey))
        })
      }),
      { numRuns: propertyRuns },
    )

    fc.assert(
      fc.property(
        fc.oneof(
          fc.constant({}),
          fc.constant({ info: { title: 'Broken contract', version: '1.0.0' } }),
          fc.constant({ info: { title: 'Broken contract', version: '1.0.0' }, paths: null }),
        ),
        (document) => {
          expect(() => buildEndpointCatalogue(document as OpenApiDocument)).toThrow(/DISCOVERY_INVALID_CONTRACT/)
        },
      ),
      { numRuns: propertyRuns },
    )
  })

  it('Feature: local-api-discovery, Property 9: Degraded mode only disables dependent surfaces', () => {
    fc.assert(
      fc.property(
        fc.array(fc.record({
          requiresBackend: fc.boolean(),
          capabilityKey: fc.option(fc.constantFrom(...capabilityKeys), { nil: undefined }),
        }), { minLength: 1, maxLength: 20 }),
        fc.dictionary(fc.constantFrom(...capabilityKeys), fc.boolean()),
        (surfaces, capabilityStateMap) => {
          const catalogue = buildEndpointCatalogue(buildContractDocument(expectedOperations))
          capabilityKeys.forEach((capabilityKey) => {
            catalogue.capabilities[capabilityKey].available = capabilityStateMap[capabilityKey] ?? false
          })

          const degradedConnection: ConnectionState = {
            status: 'degraded',
            baseUrl: 'http://localhost:8080',
            openApiUrl: 'http://localhost:8080/api-docs',
            message: 'backend unavailable',
          }

          surfaces.forEach((surface) => {
            expect(surfaceAvailable(surface, catalogue, degradedConnection)).toBe(!surface.requiresBackend)
          })
        },
      ),
      { numRuns: propertyRuns },
    )
  })
})

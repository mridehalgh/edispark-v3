import fc from 'fast-check'
import { describe, expect, it } from 'vitest'

import { getApiOperationDetail, projectApiExplorer } from '@/integration/openapi-explorer-projection'
import type {
  HttpMethod,
  OpenApiDocument,
  OpenApiReferenceObject,
  OpenApiRequestBodyObject,
  OpenApiResponseObject,
  OpenApiSchemaObject
} from '@/integration/openapi-contract'

const textArb = fc.string({ minLength: 1, maxLength: 30 }).filter((value) => value.trim().length > 0)
const methodArb = fc.constantFrom<HttpMethod>('get', 'post', 'put', 'patch', 'delete', 'options', 'head')
const schemaRefArb = fc.constantFrom<OpenApiReferenceObject>(
  { $ref: '#/components/schemas/DocumentSetResponse' },
  { $ref: '#/components/schemas/SchemaResponse' },
  { $ref: '#/components/schemas/ErrorResponse' }
)
const schemaObjectArb: fc.Arbitrary<OpenApiSchemaObject> = fc.oneof(
  fc.record({ title: textArb, type: fc.constantFrom('object', 'string', 'number') }),
  fc.record({ type: fc.constant('array'), items: schemaRefArb }),
  fc.record({ type: fc.constant('string'), enum: fc.uniqueArray(textArb, { minLength: 1, maxLength: 3 }) })
)
const schemaMetadataArb: fc.Arbitrary<OpenApiReferenceObject | OpenApiSchemaObject> = fc.oneof(schemaRefArb, schemaObjectArb)

type OperationSeed = {
  path: string
  method: HttpMethod
  operationId: string
  summary?: string
  tag?: string
  requestBody?: OpenApiRequestBodyObject | OpenApiReferenceObject
  responses: Record<string, OpenApiResponseObject | OpenApiReferenceObject>
}

const operationSeedArb = fc
  .array(
    fc.record({
      pathSegment: textArb.map((value) => value.toLowerCase().replace(/[^a-z0-9]+/g, '-')),
      method: methodArb,
      summary: fc.option(textArb, { nil: undefined }),
      tag: fc.option(textArb, { nil: undefined }),
      includeRequestBody: fc.boolean(),
      requestBodyRequired: fc.boolean(),
      requestSchema: schemaMetadataArb,
      responseStatus: fc.constantFrom('200', '201', '202', '400', '404'),
      responseSchema: schemaMetadataArb,
      responseDescription: fc.option(textArb, { nil: undefined })
    }),
    { minLength: 1, maxLength: 10 }
  )
  .filter((entries) => new Set(entries.map((entry) => `${entry.method}:${entry.pathSegment}`)).size === entries.length)
  .map<OperationSeed[]>((entries) =>
    entries.map((entry) => ({
      path: `/api/${entry.pathSegment}`,
      method: entry.method,
      operationId: `${entry.method}-${entry.pathSegment}`,
      summary: entry.summary,
      tag: entry.tag,
      requestBody: entry.includeRequestBody
        ? {
            required: entry.requestBodyRequired,
            content: {
              'application/json': {
                schema: entry.requestSchema
              }
            }
          }
        : undefined,
      responses: {
        [entry.responseStatus]: {
          description: entry.responseDescription,
          content: {
            'application/json': {
              schema: entry.responseSchema
            }
          }
        }
      }
    }))
  )

function buildDocument(seeds: OperationSeed[]): OpenApiDocument {
  const paths = seeds.reduce<OpenApiDocument['paths']>((accumulator, seed) => {
    const existing = accumulator[seed.path] ?? {}

    return {
      ...accumulator,
      [seed.path]: {
        ...existing,
        [seed.method]: {
          operationId: seed.operationId,
          summary: seed.summary,
          tags: seed.tag ? [seed.tag] : undefined,
          requestBody: seed.requestBody,
          responses: seed.responses
        }
      }
    }
  }, {})

  return {
    openapi: '3.1.0',
    info: {
      title: 'Documents API',
      version: '0.1.0'
    },
    paths
  }
}

describe('openapi explorer projection properties', () => {
  it('Feature: local-openapi-api-integration, Property 8: OpenAPI explorer projection is contract-complete', () => {
    fc.assert(
      fc.property(operationSeedArb, (seeds) => {
        const document = buildDocument(seeds)
        const projection = projectApiExplorer(document)

        expect(projection.sourceLabel).toBe('live-openapi')
        expect(projection.operations).toHaveLength(seeds.length)
        expect(Object.keys(projection.operationDetails)).toHaveLength(seeds.length)

        for (const seed of seeds) {
          const detail = getApiOperationDetail(projection, seed.operationId)
          const summary = projection.operations.find((operation) => operation.operationId === seed.operationId)

          expect(summary).toEqual({
            operationId: seed.operationId,
            method: seed.method.toUpperCase(),
            path: seed.path,
            summary: seed.summary ?? seed.operationId,
            tag: seed.tag
          })
          expect(detail?.path).toBe(seed.path)
          expect(detail?.method).toBe(seed.method.toUpperCase())
          expect(detail?.summary).toBe(seed.summary ?? seed.operationId)
          expect(detail?.tag).toBe(seed.tag)
          expect(detail?.responseSchemas).toHaveLength(Object.keys(seed.responses).length)
          expect(detail?.requestSchemas.length ?? 0).toBe(seed.requestBody ? 1 : 0)
        }
      }),
      { numRuns: 100 }
    )
  })
})

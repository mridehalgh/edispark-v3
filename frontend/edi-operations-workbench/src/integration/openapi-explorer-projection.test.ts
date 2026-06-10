import { describe, expect, it } from 'vitest'

import { getApiOperationDetail, projectApiExplorer } from '@/integration/openapi-explorer-projection'
import type { OpenApiDocument } from '@/integration/openapi-contract'

const document: OpenApiDocument = {
  openapi: '3.1.0',
  info: {
    title: 'Documents API',
    version: '0.1.0'
  },
  paths: {
    '/api/document-sets': {
      get: {
        operationId: 'listDocumentSets',
        summary: 'List document sets',
        tags: ['Document Sets'],
        responses: {
          200: {
            description: 'OK',
            content: {
              'application/json': {
                schema: {
                  $ref: '#/components/schemas/DocumentSetPageResponse'
                }
              }
            }
          }
        }
      },
      post: {
        operationId: 'createDocumentSet',
        summary: 'Create a document set',
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                $ref: '#/components/schemas/CreateDocumentSetRequest'
              }
            }
          }
        },
        responses: {
          201: {
            description: 'Created',
            content: {
              'application/json': {
                schema: {
                  $ref: '#/components/schemas/DocumentSetResponse'
                }
              }
            }
          }
        }
      }
    }
  }
}

describe('API explorer projection', () => {
  it('projects every operation into summaries and detail models', () => {
    const projection = projectApiExplorer(document)

    expect(projection.operations).toHaveLength(2)
    expect(projection.operations.map((operation) => operation.operationId)).toEqual([
      'listDocumentSets',
      'createDocumentSet'
    ])

    const detail = getApiOperationDetail(projection, 'createDocumentSet')

    expect(detail?.requestSchemas).toEqual([
      {
        contentType: 'application/json',
        required: true,
        schemaSummary: 'CreateDocumentSetRequest',
        schemaRef: '#/components/schemas/CreateDocumentSetRequest'
      }
    ])
    expect(detail?.responseSchemas[0]?.schemas[0]?.schemaSummary).toBe('DocumentSetResponse')
  })
})

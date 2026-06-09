import { describe, expect, it, vi } from 'vitest'

import { DocumentsApiError, createDocumentsApiClient } from '@/integration/documents-api-client'

describe('documents api client', () => {
  it('forwards pagination data without reinterpretation', async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        items: [],
        pageSize: 10,
        hasPrevious: false,
        hasNext: true,
        nextToken: 'opaque-next-token',
        nextUrl: '/api/document-sets?limit=10&nextToken=opaque-next-token'
      })
    })
    const client = createDocumentsApiClient({
      baseUrl: 'http://localhost:8080',
      fetch: fetcher
    })

    const response = await client.listDocumentSets({
      limit: 10,
      nextToken: 'opaque-next-token'
    })

    expect(fetcher).toHaveBeenCalledWith(
      'http://localhost:8080/api/document-sets?limit=10&nextToken=opaque-next-token',
      expect.objectContaining({
        headers: expect.objectContaining({
          Accept: 'application/json'
        })
      })
    )
    expect(response.nextToken).toBe('opaque-next-token')
    expect(response.nextUrl).toBe('/api/document-sets?limit=10&nextToken=opaque-next-token')
  })

  it('surfaces backend status and message details when an operation fails', async () => {
    const client = createDocumentsApiClient({
      baseUrl: 'http://localhost:8080',
      fetch: vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        headers: new Headers({
          'content-type': 'application/json'
        }),
        json: async () => ({
          code: 'SCHEMA_NOT_FOUND',
          message: 'Schema not found',
          timestamp: '2026-06-09T00:00:00Z',
          details: {
            schemaId: '[schema_id]'
          }
        })
      })
    })

    await expect(client.getSchema('[schema_id]')).rejects.toEqual(
      expect.objectContaining<Partial<DocumentsApiError>>({
        operationId: 'getSchema',
        status: 404,
        message: 'Schema not found',
        details: {
          schemaId: '[schema_id]'
        }
      })
    )
  })
})

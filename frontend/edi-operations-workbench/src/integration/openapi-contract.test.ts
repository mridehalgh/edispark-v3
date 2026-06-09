import { describe, expect, it, vi } from 'vitest'

import { loadOpenApiContract } from '@/integration/openapi-contract'

const openApiDocument = {
  openapi: '3.1.0',
  info: {
    title: 'Documents API',
    version: '0.1.0'
  },
  paths: {
    '/api/schemas': {
      post: {
        operationId: 'createSchema',
        summary: 'Create schema',
        responses: {
          201: {
            description: 'Created'
          }
        }
      }
    }
  }
}

describe('OpenAPI contract loading', () => {
  it('returns a connected contract state when the local document is available', async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => openApiDocument
    })

    const result = await loadOpenApiContract({
      overrideBaseUrl: 'http://localhost:18080',
      fetch: fetcher
    })

    expect(result.state).toEqual({
      kind: 'connected',
      baseUrl: 'http://localhost:18080',
      openApiUrl: 'http://localhost:18080/api-docs',
      contractTitle: 'Documents API',
      contractVersion: '0.1.0',
      operationCount: 1
    })
    expect(result.explorer?.sourceLabel).toBe('live-openapi')
  })

  it('keeps the expected backend location visible when contract loading fails', async () => {
    const result = await loadOpenApiContract({
      overrideBaseUrl: 'http://localhost:19090',
      fetch: vi.fn().mockResolvedValue({
        ok: false,
        status: 503
      })
    })

    expect(result.state.kind).toBe('contract-failed')
    expect(result.state.baseUrl).toBe('http://localhost:19090')

    if (result.state.kind !== 'contract-failed') {
      throw new Error('Expected contract failure state')
    }

    expect(result.state.reason).toContain('http://localhost:19090/api-docs')
  })
})

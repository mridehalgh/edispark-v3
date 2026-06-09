import fc from 'fast-check'
import { describe, expect, it, vi } from 'vitest'

import { createDocumentsApiClient } from '@/integration/documents-api-client'
import type { DocumentSetPageResponse } from '@/integration/documents-api-client'

const textArb = fc.string({ minLength: 1, maxLength: 30 }).filter((value) => value.trim().length > 0)

describe('documents api client properties', () => {
  it('Feature: local-openapi-api-integration, Property 5: Pagination tokens are preserved as opaque continuation state', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.record({
          items: fc.array(
            fc.record({
              id: textArb,
              createdAt: fc.date().map((value) => value.toISOString()),
              createdBy: textArb,
              metadata: fc.option(fc.dictionary(textArb, textArb), { nil: undefined }),
              documents: fc.array(
                fc.record({
                  id: textArb,
                  type: fc.constantFrom('INVOICE', 'ORDER', 'DESPATCH_ADVICE', 'RECEIPT_ADVICE', 'REMITTANCE_ADVICE', 'APPLICATION_RESPONSE'),
                  versionCount: fc.integer({ min: 1, max: 10 })
                }),
                { maxLength: 4 }
              )
            }),
            { maxLength: 6 }
          ),
          pageSize: fc.integer({ min: 1, max: 50 }),
          hasPrevious: fc.boolean(),
          hasNext: fc.boolean(),
          previousToken: fc.option(textArb, { nil: undefined }),
          nextToken: fc.option(textArb, { nil: undefined }),
          previousUrl: fc.option(textArb.map((token) => `/api/document-sets?previousToken=${encodeURIComponent(token)}`), { nil: undefined }),
          nextUrl: fc.option(textArb.map((token) => `/api/document-sets?nextToken=${encodeURIComponent(token)}`), { nil: undefined })
        }) satisfies fc.Arbitrary<DocumentSetPageResponse>,
        fc.option(textArb, { nil: undefined }),
        async (page, nextToken) => {
          const fetcher = vi.fn().mockResolvedValue({
            ok: true,
            json: async () => page
          })
          const client = createDocumentsApiClient({
            baseUrl: 'http://localhost:8080',
            fetch: fetcher
          })

          const response = await client.listDocumentSets({ limit: page.pageSize, nextToken })
          const [requestedUrl] = fetcher.mock.calls[0] ?? []

          expect(response).toEqual(page)
          expect(response.items.map((item) => item.id)).toEqual(page.items.map((item) => item.id))

          if (nextToken) {
            expect(new URL(requestedUrl).searchParams.get('nextToken')).toBe(nextToken)
            return
          }

          expect(new URL(requestedUrl).searchParams.get('nextToken')).toBeNull()
        }
      ),
      { numRuns: 100 }
    )
  })
})

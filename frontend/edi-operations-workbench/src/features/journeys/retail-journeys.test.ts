import fc from 'fast-check'
import { describe, expect, it } from 'vitest'

import { getRetailJourney, projectRetailJourneys } from '@/features/journeys/retail-journeys'
import type { DocumentSetResponse, DocumentType } from '@/integration/documents-api-client'

const documentTypeArb = fc.constantFrom<DocumentType>(
  'INVOICE',
  'ORDER',
  'DESPATCH_ADVICE',
  'RECEIPT_ADVICE',
  'REMITTANCE_ADVICE',
  'APPLICATION_RESPONSE'
)

const textArb = fc.string({ minLength: 1, maxLength: 30 }).filter((value) => value.trim().length > 0)

const documentSetArb = fc.record({
  id: textArb,
  createdAt: fc.date().map((value) => value.toISOString()),
  createdBy: textArb,
  metadata: fc.option(fc.dictionary(textArb, textArb), { nil: undefined }),
  documents: fc.array(
    fc.record({
      id: textArb,
      type: documentTypeArb,
      versionCount: fc.integer({ min: 1, max: 20 })
    }),
    { maxLength: 5 }
  )
}) satisfies fc.Arbitrary<DocumentSetResponse>

describe('retail journey projection', () => {
  it('Feature: edi-operations-workbench, Property 2: Retail journey mapping reflects supported and planned steps', () => {
    fc.assert(
      fc.property(fc.array(documentSetArb, { maxLength: 12 }), (documentSets) => {
        const journeys = projectRetailJourneys(documentSets)
        const expectedJourneyKeys = ['orders', 'desadv', 'recadv', 'invoic']

        expect(journeys.map((journey) => journey.key)).toEqual(expectedJourneyKeys)

        for (const journeyKey of expectedJourneyKeys) {
          const journey = getRetailJourney(journeyKey, documentSets)
          expect(journey).toBeDefined()
          expect(journey?.recommendedActions.length).toBeGreaterThan(0)

          for (const step of journey?.steps ?? []) {
            const expectedCount = documentSets.filter((documentSet) =>
              documentSet.documents.some((document) => step.relatedDocumentTypes.includes(document.type))
            ).length

            expect(step.linkedRecordCount).toBe(expectedCount)
            expect(step.state).toBe(step.actionPath ? (expectedCount > 0 ? 'available' : 'empty') : 'planned')
          }

          const expectedLinkedRecords = documentSets.filter((documentSet) =>
            documentSet.documents.some((document) => journey?.relatedDocumentTypes.includes(document.type))
          )

          expect(journey?.linkedRecords).toHaveLength(expectedLinkedRecords.length)
        }
      }),
      { numRuns: 100 }
    )
  })
})

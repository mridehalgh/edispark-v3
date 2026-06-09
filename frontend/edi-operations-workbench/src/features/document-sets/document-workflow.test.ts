import fc from 'fast-check'
import { describe, expect, it } from 'vitest'

import {
  createDownloadDescriptor,
  projectDocumentSetEvidence,
  projectValidationEvidence,
  validateAddDocumentForm,
  validateAddVersionForm,
  validateDerivativeSourceVersion
} from '@/features/document-sets/document-workflow'
import type {
  DocumentResponse,
  DocumentSetResponse,
  DocumentType,
  Format,
  ValidationResultResponse,
  VersionContentResponse
} from '@/integration/documents-api-client'

const textArb = fc.string({ minLength: 1, maxLength: 30 }).filter((value) => value.trim().length > 0)
const maybeBlankTextArb = fc.oneof(textArb, fc.constant(''), fc.constant('   '))
const documentTypeArb = fc.constantFrom<DocumentType>('INVOICE', 'ORDER', 'DESPATCH_ADVICE', 'RECEIPT_ADVICE', 'REMITTANCE_ADVICE', 'APPLICATION_RESPONSE')
const formatArb = fc.constantFrom<Format>('XML', 'JSON', 'PDF', 'EDI')

const documentVersionArb = fc.record({
  id: textArb,
  versionNumber: fc.integer({ min: 1, max: 20 }),
  contentHash: textArb,
  createdAt: fc.date().map((value) => value.toISOString()),
  createdBy: textArb,
  format: formatArb,
  parseStatus: fc.option(textArb, { nil: undefined }),
  messageType: fc.option(textArb, { nil: undefined }),
  parseErrors: fc.array(textArb, { maxLength: 4 })
})

const derivativeArb = fc.record({
  id: textArb,
  sourceVersionId: textArb,
  targetFormat: formatArb,
  contentHash: textArb,
  transformationMethod: textArb,
  createdAt: fc.date().map((value) => value.toISOString())
})

const documentResponseArb = fc.record({
  id: textArb,
  type: documentTypeArb,
  schemaRef: fc.record({
    schemaId: textArb,
    version: textArb
  }),
  versionCount: fc.integer({ min: 1, max: 20 }),
  currentVersion: documentVersionArb,
  derivatives: fc.array(derivativeArb, { maxLength: 4 })
}) satisfies fc.Arbitrary<DocumentResponse>

describe('document workflow projection', () => {
  it('Feature: edi-operations-workbench, Property 4: Document workflow projection preserves document evidence', () => {
    const payloadArb = fc.record({
      documentSetId: textArb,
      createdAt: fc.date().map((value) => value.toISOString()),
      createdBy: textArb,
      metadata: fc.option(fc.dictionary(textArb, textArb), { nil: undefined }),
      documents: fc.array(documentResponseArb, { maxLength: 6 }),
      validation: fc.record({
        valid: fc.boolean(),
        errors: fc.array(fc.record({ path: textArb, message: textArb }), { maxLength: 4 }),
        warnings: fc.array(fc.record({ path: textArb, message: textArb }), { maxLength: 4 })
      }) satisfies fc.Arbitrary<ValidationResultResponse>,
      addDocumentForm: fc.record({
        schemaId: maybeBlankTextArb,
        schemaVersion: maybeBlankTextArb,
        contentText: maybeBlankTextArb,
        createdBy: maybeBlankTextArb
      }),
      addVersionForm: fc.record({
        contentText: maybeBlankTextArb,
        createdBy: maybeBlankTextArb
      }),
      derivativeSourceVersion: fc.oneof(fc.integer({ min: -2, max: 5 }).map(String), fc.constant('not-a-number'))
    })

    fc.assert(
      fc.property(payloadArb, ({ documentSetId, createdAt, createdBy, metadata, documents, validation, addDocumentForm, addVersionForm, derivativeSourceVersion }) => {
        const documentSet: DocumentSetResponse = {
          id: documentSetId,
          createdAt,
          createdBy,
          metadata,
          documents: documents.map((document) => ({
            id: document.id,
            type: document.type,
            versionCount: document.versionCount
          }))
        }

        const evidence = projectDocumentSetEvidence(documentSet, documents)
        expect(evidence.documentSetId).toBe(documentSet.id)
        expect(evidence.documentCount).toBe(documents.length)
        expect(evidence.documents).toHaveLength(documents.length)

        evidence.documents.forEach((projected, index) => {
          const source = documents[index]
          expect(projected.documentId).toBe(source.id)
          expect(projected.documentType).toBe(source.type)
          expect(projected.schemaId).toBe(source.schemaRef.schemaId)
          expect(projected.schemaVersion).toBe(source.schemaRef.version)
          expect(projected.currentVersionId).toBe(source.currentVersion.id)
          expect(projected.currentVersionNumber).toBe(source.currentVersion.versionNumber)
          expect(projected.derivativeCount).toBe(source.derivatives.length)
          expect(projected.parseErrors).toEqual(source.currentVersion.parseErrors)
        })

        const validationEvidence = projectValidationEvidence(validation)
        expect(validationEvidence.valid).toBe(validation.valid)
        expect(validationEvidence.statusLabel).toBe(validation.valid ? 'Pass' : 'Fail')
        expect(validationEvidence.errors).toEqual(validation.errors)
        expect(validationEvidence.warnings).toEqual(validation.warnings)

        const addDocumentErrors = validateAddDocumentForm(addDocumentForm)
        expect(addDocumentErrors.length === 0).toBe(
          Boolean(
            addDocumentForm.schemaId.trim() &&
              addDocumentForm.schemaVersion.trim() &&
              addDocumentForm.contentText.trim() &&
              addDocumentForm.createdBy.trim()
          )
        )

        const addVersionErrors = validateAddVersionForm(addVersionForm)
        expect(addVersionErrors.length === 0).toBe(Boolean(addVersionForm.contentText.trim() && addVersionForm.createdBy.trim()))

        const derivativeErrors = validateDerivativeSourceVersion(derivativeSourceVersion)
        expect(derivativeErrors.length === 0).toBe(Number.isInteger(Number(derivativeSourceVersion)) && Number(derivativeSourceVersion) >= 1)
      }),
      { numRuns: 100 }
    )
  })

  it('Feature: local-openapi-api-integration, Property 6: Download descriptors preserve backend content identity', () => {
    const versionContentArb = fc.record({
      bytes: fc.uint8Array({ maxLength: 1024 }).map((value) => new Uint8Array(value)),
      fileName: textArb,
      mediaType: fc.constantFrom('application/octet-stream', 'application/json', 'application/xml', 'text/plain'),
      documentSetId: textArb,
      documentId: textArb,
      versionNumber: fc.integer({ min: 1, max: 20 }),
      contentHash: fc.option(textArb, { nil: undefined }),
      format: fc.option(formatArb, { nil: undefined })
    }) satisfies fc.Arbitrary<VersionContentResponse>

    fc.assert(
      fc.property(versionContentArb, (content) => {
        const descriptor = createDownloadDescriptor(content)

        expect(Array.from(descriptor.bytes)).toEqual(Array.from(content.bytes))
        expect(descriptor.fileName).toBe(content.fileName)
        expect(descriptor.mediaType).toBe(content.mediaType)
        expect(descriptor.documentSetId).toBe(content.documentSetId)
        expect(descriptor.documentId).toBe(content.documentId)
        expect(descriptor.versionNumber).toBe(content.versionNumber)
        expect(descriptor.contentHash).toBe(content.contentHash)
        expect(descriptor.format).toBe(content.format)
      }),
      { numRuns: 100 }
    )
  })
})

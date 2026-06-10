import fc from 'fast-check'
import { describe, expect, it } from 'vitest'

import {
  projectAddSchemaVersionSubmission,
  projectCreateSchemaSubmission
} from '@/features/schemas/schema-workflow'
import type { RequestLifecycleState } from '@/integration/request-lifecycle'
import type { SchemaResponse, SchemaVersionResponse } from '@/integration/documents-api-client'

const textArb = fc.string({ minLength: 1, maxLength: 40 }).filter((value) => value.trim().length > 0)
const maybeBlankTextArb = fc.oneof(textArb, fc.constant(''), fc.constant('   '))
const formatArb: fc.Arbitrary<SchemaResponse['format']> = fc.constantFrom('XSD', 'JSON_SCHEMA', 'RELAXNG')

type CreateSchemaCase = {
  form: {
    name: string
    format: SchemaResponse['format']
  }
  success: SchemaResponse
  failureReason: string
}

type AddVersionCase = {
  form: {
    schemaId: string
    versionIdentifier: string
    definitionText: string
  }
  success: SchemaVersionResponse
  failureReason: string
}

describe('schema workflow projection', () => {
  it('Feature: edi-operations-workbench, Property 3: Schema workflow validation and recovery preserve user intent', () => {
    const createSchemaCaseArb: fc.Arbitrary<CreateSchemaCase> = fc.record({
      form: fc.record({
        name: maybeBlankTextArb,
        format: formatArb
      }),
      success: fc.record<SchemaResponse>({
        id: textArb,
        name: textArb,
        format: formatArb,
        versions: fc.constant([])
      }),
      failureReason: textArb
    })

    const addVersionCaseArb: fc.Arbitrary<AddVersionCase> = fc.record({
      form: fc.record({
        schemaId: maybeBlankTextArb,
        versionIdentifier: maybeBlankTextArb,
        definitionText: maybeBlankTextArb
      }),
      success: fc.record<SchemaVersionResponse>({
        id: textArb,
        versionIdentifier: textArb,
        createdAt: fc.date().map((value) => value.toISOString()),
        deprecated: fc.boolean()
      }),
      failureReason: textArb
    })

    fc.assert(
      fc.property(fc.oneof(createSchemaCaseArb, addVersionCaseArb), fc.boolean(), (testCase, shouldSucceed) => {
        if ('name' in testCase.form) {
          const success = testCase.success as SchemaResponse
          const initial = projectCreateSchemaSubmission(testCase.form)

          if (!testCase.form.name.trim()) {
            expect(initial.shouldSubmit).toBe(false)
            expect(initial.errors).toContain('Schema name is required.')
            return
          }

          const result: RequestLifecycleState<SchemaResponse> = shouldSucceed
            ? { status: 'succeeded', operationId: 'createSchemaWorkflow', data: success }
            : {
                status: 'failed',
                operationId: 'createSchemaWorkflow',
                reason: testCase.failureReason,
                retryable: true
              }
          const submission = projectCreateSchemaSubmission(testCase.form, result)

          expect(submission.form).toEqual(testCase.form)

          if (shouldSucceed) {
            expect(submission.summary).toContain(success.id)
            expect(submission.summary).toContain(success.name)
            expect(submission.summary).toContain(success.format)
            return
          }

          expect(submission.errors).toEqual([testCase.failureReason])
          return
        }

        const initial = projectAddSchemaVersionSubmission(testCase.form)
        const success = testCase.success as SchemaVersionResponse

        if (!testCase.form.schemaId.trim() || !testCase.form.versionIdentifier.trim() || !testCase.form.definitionText.trim()) {
          expect(initial.shouldSubmit).toBe(false)
          expect(initial.errors.length).toBeGreaterThan(0)
          return
        }

        const result: RequestLifecycleState<SchemaVersionResponse> = shouldSucceed
          ? { status: 'succeeded', operationId: 'addSchemaVersionWorkflow', data: success }
          : {
              status: 'failed',
              operationId: 'addSchemaVersionWorkflow',
              reason: testCase.failureReason,
              retryable: true
            }
        const submission = projectAddSchemaVersionSubmission(testCase.form, result)

        expect(submission.form).toEqual(testCase.form)

        if (shouldSucceed) {
          expect(submission.summary).toContain(success.id)
          expect(submission.summary).toContain(success.versionIdentifier)
          expect(submission.summary).toContain(success.deprecated ? 'yes' : 'no')
          return
        }

        expect(submission.errors).toEqual([testCase.failureReason])
      }),
      { numRuns: 100 }
    )
  })
})

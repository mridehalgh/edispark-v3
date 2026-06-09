import fc from 'fast-check'
import { describe, expect, it } from 'vitest'

import { createContractFailureState } from '@/integration/openapi-contract'
import { localBackendConfig, resolveLocalBackendConfig } from '@/integration/local-backend-config'

const overrideArb = fc.option(
  fc.integer({ min: 1024, max: 65535 }).map((port) => ` http://localhost:${port}/// `),
  { nil: undefined }
)

describe('local backend configuration properties', () => {
  it('Feature: local-openapi-api-integration, Property 7: Local backend configuration resolution is deterministic', () => {
    fc.assert(
      fc.property(overrideArb, fc.string({ minLength: 1, maxLength: 40 }), (overrideBaseUrl, reason) => {
        const config = resolveLocalBackendConfig(overrideBaseUrl)
        const expectedBaseUrl = overrideBaseUrl?.trim()
          ? overrideBaseUrl.trim().replace(/\/+$/, '')
          : localBackendConfig.defaultBaseUrl

        expect(config.baseUrl).toBe(expectedBaseUrl)
        expect(config.openApiUrl).toBe(`${expectedBaseUrl}/api-docs`)

        const failureState = createContractFailureState(config, reason)
        expect(failureState.baseUrl).toBe(expectedBaseUrl)
        expect(failureState.openApiUrl).toBe(`${expectedBaseUrl}/api-docs`)
      }),
      { numRuns: 100 }
    )
  })
})

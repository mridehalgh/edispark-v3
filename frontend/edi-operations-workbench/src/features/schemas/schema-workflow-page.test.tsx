import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { SchemaWorkflowPage } from '@/features/schemas/schema-workflow-page'
import type { ContractState } from '@/integration/openapi-contract'

type MockIntegrationValue = {
  contractState: ContractState
  runRequest: ReturnType<typeof vi.fn>
}

let currentIntegration: MockIntegrationValue

vi.mock('@/integration/integration-provider', () => ({
  useIntegration: () => currentIntegration
}))

describe('schema workflow page', () => {
  it('blocks schema submissions while the backend is unreachable', () => {
    currentIntegration = {
      contractState: {
        kind: 'contract-failed',
        baseUrl: 'http://localhost:18080',
        openApiUrl: 'http://localhost:18080/api-docs',
        reason: 'OpenAPI contract request failed with status 503. Expected local backend contract at http://localhost:18080/api-docs.'
      },
      runRequest: vi.fn()
    }

    render(<SchemaWorkflowPage />)

    expect(screen.getAllByText('Live submission is blocked until the backend connection recovers.')).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Create schema' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Add schema version' })).toBeDisabled()
  })
})

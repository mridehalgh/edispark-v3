import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { DocumentSetListPage } from '@/features/document-sets/document-set-list-page'
import type { ContractState } from '@/integration/openapi-contract'
import type { RequestLifecycleState } from '@/integration/request-lifecycle'

type MockIntegrationValue = {
  contractState: ContractState
  runRequest: ReturnType<typeof vi.fn>
}

let currentIntegration: MockIntegrationValue
let currentCatalogueState: RequestLifecycleState<{
  items: Array<{
    id: string
    createdAt: string
    createdBy: string
    documents: Array<{ id: string; type: string; versionCount: number }>
  }>
  nextToken?: string
  nextUrl?: string
}>

vi.mock('@/integration/integration-provider', () => ({
  useIntegration: () => currentIntegration
}))

vi.mock('@/features/document-sets/use-document-set-catalogue', () => ({
  useDocumentSetCatalogue: () => ({
    goToNextPage: vi.fn(),
    goToPreviousPage: vi.fn(),
    hasNextPage: false,
    hasPreviousPage: false,
    page: currentCatalogueState.status === 'succeeded' ? currentCatalogueState.data : undefined,
    refresh: vi.fn(),
    requestState: currentCatalogueState
  })
}))

describe('document-set list page', () => {
  it('shows an empty state when the live catalogue has no document sets', () => {
    currentIntegration = {
      contractState: {
        kind: 'connected',
        baseUrl: 'http://localhost:8080',
        openApiUrl: 'http://localhost:8080/api-docs',
        contractTitle: 'Documents API',
        contractVersion: '0.1.0',
        operationCount: 12
      },
      runRequest: vi.fn()
    }
    currentCatalogueState = {
      status: 'succeeded',
      operationId: 'listDocumentSets',
      data: {
        items: []
      }
    }

    render(<DocumentSetListPage />)

    expect(screen.getByText('No document sets yet')).toBeInTheDocument()
    expect(screen.getByText('Create a document set to start powering downstream document and validation workflows.')).toBeInTheDocument()
  })

  it('blocks live submission when the backend contract is unavailable', () => {
    currentIntegration = {
      contractState: {
        kind: 'contract-failed',
        baseUrl: 'http://localhost:18080',
        openApiUrl: 'http://localhost:18080/api-docs',
        reason: 'OpenAPI contract request failed with status 503. Expected local backend contract at http://localhost:18080/api-docs.'
      },
      runRequest: vi.fn()
    }
    currentCatalogueState = {
      status: 'succeeded',
      operationId: 'listDocumentSets',
      data: {
        items: []
      }
    }

    render(<DocumentSetListPage />)

    expect(screen.getByText('Live submission is blocked until the backend connection recovers.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create document set' })).toBeDisabled()
  })
})

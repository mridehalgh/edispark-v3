import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { workbenchRoutes } from '@/app/router'
import type { ContractState } from '@/integration/openapi-contract'
import type { RequestLifecycleState } from '@/integration/request-lifecycle'

type MockIntegrationValue = {
  contractState: ContractState
  refreshContract: () => Promise<void>
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

afterEach(() => {
  vi.clearAllMocks()
})

function renderRoute(path: string) {
  const router = createMemoryRouter(workbenchRoutes, {
    initialEntries: [path]
  })

  return render(<RouterProvider router={router} />)
}

describe('workbench routes', () => {
  it('renders the configured workflow and explorer routes', async () => {
    currentIntegration = {
      contractState: {
        kind: 'connected',
        baseUrl: 'http://localhost:8080',
        openApiUrl: 'http://localhost:8080/api-docs',
        contractTitle: 'Documents API',
        contractVersion: '0.1.0',
        operationCount: 12
      },
      refreshContract: vi.fn().mockResolvedValue(undefined),
      runRequest: vi.fn().mockResolvedValue({
        status: 'succeeded',
        operationId: 'documentSetDetail:set-123',
        data: {
          documentSet: {
            id: 'set-123',
            createdAt: '2026-01-01T00:00:00.000Z',
            createdBy: 'ops-user',
            metadata: { source: 'ORDERS' },
            documents: []
          },
          documents: []
        }
      })
    }
    currentCatalogueState = {
      status: 'succeeded',
      operationId: 'listDocumentSets',
      data: {
        items: [
          {
            id: 'set-123',
            createdAt: '2026-01-01T00:00:00.000Z',
            createdBy: 'ops-user',
            documents: []
          }
        ]
      }
    }

    renderRoute('/schemas')
    expect(await screen.findByRole('heading', { name: 'Create schema' })).toBeInTheDocument()

    renderRoute('/document-sets')
    expect(await screen.findByRole('heading', { name: 'Create document set' })).toBeInTheDocument()

    renderRoute('/document-sets/set-123')
    expect(await screen.findByRole('heading', { name: 'Add document' })).toBeInTheDocument()

    renderRoute('/retail-journeys')
    expect(await screen.findByRole('link', { name: 'Open detail view' })).toBeInTheDocument()

    renderRoute('/api-explorer')
    expect(await screen.findByText('API explorer view is planned')).toBeInTheDocument()
  })
})

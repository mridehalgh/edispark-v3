import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { DocumentVersionPage } from '@/features/document-sets/document-version-page'
import type { ContractState } from '@/integration/openapi-contract'

type MockIntegrationValue = {
  contractState: ContractState
  runRequest: ReturnType<typeof vi.fn>
}

let currentIntegration: MockIntegrationValue

vi.mock('@/integration/integration-provider', () => ({
  useIntegration: () => currentIntegration
}))

describe('document version page', () => {
  it('loads version detail on direct mount while the shell is degraded', async () => {
    currentIntegration = {
      contractState: {
        kind: 'request-failed',
        baseUrl: 'http://localhost:8080',
        openApiUrl: 'http://localhost:8080/api-docs',
        contractTitle: 'Documents API',
        contractVersion: '0.1.0',
        operationCount: 12,
        operationId: 'listDocumentSets',
        reason: 'Catalogue refresh failed.',
        status: 503
      },
      runRequest: vi.fn().mockResolvedValue({
        status: 'succeeded',
        operationId: 'documentVersion:set-123:doc-456:1',
        data: {
          id: 'version-1',
          versionNumber: 1,
          contentHash: 'hash-123',
          createdAt: '2026-01-01T00:00:00.000Z',
          createdBy: 'ops-user',
          format: 'EDI',
          parseStatus: 'SUCCESS',
          messageType: 'ORDERS',
          parseErrors: []
        }
      })
    }

    render(
      <MemoryRouter initialEntries={['/document-sets/set-123/documents/doc-456/versions/1']}>
        <Routes>
          <Route path="/document-sets/:setId/documents/:docId/versions/:versionNumber" element={<DocumentVersionPage />} />
        </Routes>
      </MemoryRouter>
    )

    expect(await screen.findByText('version-1')).toBeInTheDocument()
  })

  it('blocks live actions when the version view becomes non-runnable', async () => {
    currentIntegration = {
      contractState: {
        kind: 'connected',
        baseUrl: 'http://localhost:8080',
        openApiUrl: 'http://localhost:8080/api-docs',
        contractTitle: 'Documents API',
        contractVersion: '0.1.0',
        operationCount: 12
      },
      runRequest: vi.fn().mockResolvedValue({
        status: 'succeeded',
        operationId: 'documentVersion:set-123:doc-456:1',
        data: {
          id: 'version-1',
          versionNumber: 1,
          contentHash: 'hash-123',
          createdAt: '2026-01-01T00:00:00.000Z',
          createdBy: 'ops-user',
          format: 'EDI',
          parseStatus: 'SUCCESS',
          messageType: 'ORDERS',
          parseErrors: []
        }
      })
    }

    const view = render(
      <MemoryRouter initialEntries={['/document-sets/set-123/documents/doc-456/versions/1']}>
        <Routes>
          <Route path="/document-sets/:setId/documents/:docId/versions/:versionNumber" element={<DocumentVersionPage />} />
        </Routes>
      </MemoryRouter>
    )

    expect(await screen.findByText('version-1')).toBeInTheDocument()

    currentIntegration = {
      ...currentIntegration,
      contractState: {
        kind: 'loading-contract',
        baseUrl: 'http://localhost:8080',
        openApiUrl: 'http://localhost:8080/api-docs'
      }
    }
    view.rerender(
      <MemoryRouter initialEntries={['/document-sets/set-123/documents/doc-456/versions/1']}>
        <Routes>
          <Route path="/document-sets/:setId/documents/:docId/versions/:versionNumber" element={<DocumentVersionPage />} />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('Live actions are blocked until the backend connection recovers.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Validate version' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Download raw content' })).toBeDisabled()
  })

  it('shows a validation-specific recoverable error when validation fails', async () => {
    currentIntegration = {
      contractState: {
        kind: 'connected',
        baseUrl: 'http://localhost:8080',
        openApiUrl: 'http://localhost:8080/api-docs',
        contractTitle: 'Documents API',
        contractVersion: '0.1.0',
        operationCount: 12
      },
      runRequest: vi
        .fn()
        .mockResolvedValueOnce({
          status: 'succeeded',
          operationId: 'documentVersion:set-123:doc-456:1',
          data: {
            id: 'version-1',
            versionNumber: 1,
            contentHash: 'hash-123',
            createdAt: '2026-01-01T00:00:00.000Z',
            createdBy: 'ops-user',
            format: 'EDI',
            parseStatus: 'SUCCESS',
            messageType: 'ORDERS',
            parseErrors: []
          }
        })
        .mockResolvedValueOnce({
          status: 'failed',
          operationId: 'validateDocument:set-123:doc-456:1',
          reason: 'Validation endpoint returned 503.',
          retryable: true,
          statusCode: 503
        })
    }

    render(
      <MemoryRouter initialEntries={['/document-sets/set-123/documents/doc-456/versions/1']}>
        <Routes>
          <Route path="/document-sets/:setId/documents/:docId/versions/:versionNumber" element={<DocumentVersionPage />} />
        </Routes>
      </MemoryRouter>
    )

    expect(await screen.findByText('version-1')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Validate version' }))

    await waitFor(() => {
      expect(screen.getByText('Validation could not be completed')).toBeInTheDocument()
    })
    expect(screen.getByText('This is recoverable. Retry validation after the backend validation flow becomes available again.')).toBeInTheDocument()
    expect(screen.getByText('Validation endpoint returned 503.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry validation' })).toBeInTheDocument()
    expect(screen.queryByText('Raw content could not be downloaded')).not.toBeInTheDocument()
  })

  it('shows a recoverable missing-content error for failed downloads', async () => {
    currentIntegration = {
      contractState: {
        kind: 'connected',
        baseUrl: 'http://localhost:8080',
        openApiUrl: 'http://localhost:8080/api-docs',
        contractTitle: 'Documents API',
        contractVersion: '0.1.0',
        operationCount: 12
      },
      runRequest: vi
        .fn()
        .mockResolvedValueOnce({
          status: 'succeeded',
          operationId: 'documentVersion:set-123:doc-456:1',
          data: {
            id: 'version-1',
            versionNumber: 1,
            contentHash: 'hash-123',
            createdAt: '2026-01-01T00:00:00.000Z',
            createdBy: 'ops-user',
            format: 'EDI',
            parseStatus: 'SUCCESS',
            messageType: 'ORDERS',
            parseErrors: []
          }
        })
        .mockResolvedValueOnce({
          status: 'failed',
          operationId: 'downloadDocumentContent:set-123:doc-456:1',
          reason: 'Content store returned 404 for version-1.',
          retryable: true,
          statusCode: 404
        })
    }

    render(
      <MemoryRouter initialEntries={['/document-sets/set-123/documents/doc-456/versions/1']}>
        <Routes>
          <Route path="/document-sets/:setId/documents/:docId/versions/:versionNumber" element={<DocumentVersionPage />} />
        </Routes>
      </MemoryRouter>
    )

    expect(await screen.findByText('version-1')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Download raw content' }))

    await waitFor(() => {
      expect(screen.getByText('Raw content could not be downloaded')).toBeInTheDocument()
    })
    expect(screen.getByText('This is recoverable. Retry the same document version after the backend content becomes available again.')).toBeInTheDocument()
    expect(screen.getByText('Content for doc-456 version 1 is unavailable right now. Content store returned 404 for version-1.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry download' })).toBeInTheDocument()
  })
})

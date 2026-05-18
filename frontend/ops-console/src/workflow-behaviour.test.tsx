import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  createHealthyDiscoveryResult,
  jsonResponse,
  mockedDiscoverBackend,
  renderOpsConsole,
  textResponse,
} from '@/test/ops-console-test-support'

describe('ops console workflow behaviour', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    mockedDiscoverBackend.mockReset()
    vi.unstubAllGlobals()
  })

  it('renders the document-set inspection workflow with representative backend responses', async () => {
    const user = userEvent.setup()

    mockedDiscoverBackend.mockResolvedValue(createHealthyDiscoveryResult())
    vi.stubGlobal('fetch', vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url)
      const method = init?.method ?? 'GET'

      if (method === 'GET' && url.pathname === '/api/document-sets/set-001') {
        return jsonResponse({
          id: 'set-001',
          createdAt: '2026-05-18T08:00:00Z',
          createdBy: 'integration.analyst',
          metadata: { channel: 'ecommerce' },
          documents: [
            { id: 'doc-001', type: 'ORDER', versionCount: 1 },
            { id: 'doc-002', type: 'INVOICE', versionCount: 2 },
          ],
        })
      }

      if (method === 'GET' && url.pathname === '/api/document-sets/set-001/documents/doc-001') {
        return jsonResponse({
          id: 'doc-001',
          type: 'ORDER',
          schemaRef: { schemaId: 'schema-orders', version: '1.0' },
          versionCount: 1,
          currentVersion: {
            id: 'version-001',
            versionNumber: 1,
            contentHash: 'hash-001',
            createdAt: '2026-05-18T08:01:00Z',
            createdBy: 'integration.analyst',
            format: 'EDI',
            parseStatus: 'PARSED',
            messageType: 'ORDERS',
            parseErrors: [],
          },
          derivatives: [{ id: 'derivative-001', sourceVersionId: 'version-001', targetFormat: 'JSON', contentHash: 'hash-json', transformationMethod: 'EDI_TO_JSON', createdAt: '2026-05-18T08:02:00Z' }],
        })
      }

      if (method === 'GET' && url.pathname === '/api/document-sets/set-001/documents/doc-002') {
        return jsonResponse({
          id: 'doc-002',
          type: 'INVOICE',
          schemaRef: { schemaId: 'schema-invoice', version: '2.0' },
          versionCount: 2,
          currentVersion: {
            id: 'version-002',
            versionNumber: 2,
            contentHash: 'hash-002',
            createdAt: '2026-05-18T09:01:00Z',
            createdBy: 'operations.user',
            format: 'EDI',
            parseStatus: 'VALIDATED',
            messageType: 'INVOIC',
            parseErrors: [],
          },
          derivatives: [],
        })
      }

      if (method === 'GET' && url.pathname === '/api/document-sets/set-001/documents/doc-001/versions/1') {
        return jsonResponse({
          id: 'version-001',
          versionNumber: 1,
          contentHash: 'hash-001',
          createdAt: '2026-05-18T08:01:00Z',
          createdBy: 'integration.analyst',
          format: 'EDI',
          parseStatus: 'PARSED',
          messageType: 'ORDERS',
          parseErrors: [],
        })
      }

      if (method === 'GET' && url.pathname === '/api/document-sets/set-001/documents/doc-001/derivatives') {
        return jsonResponse([{ id: 'derivative-001', sourceVersionId: 'version-001', targetFormat: 'JSON', contentHash: 'hash-json', transformationMethod: 'EDI_TO_JSON', createdAt: '2026-05-18T08:02:00Z' }])
      }

      if (method === 'GET' && url.pathname === '/api/document-sets/set-001/documents/doc-001/versions/1/content') {
        return textResponse("UNH+1+ORDERS:D:03B:UN:EAN008'", { headers: { 'content-disposition': 'attachment; filename="order.edi"' } })
      }

      throw new Error(`Unhandled fetch for ${method} ${url.pathname}`)
    }))

    renderOpsConsole('/document-sets/set-001?docId=doc-001&versionNumber=1')

    await screen.findByRole('heading', { name: 'Set set-001' })
    expect(screen.getByText(/backend document ID doc-001/i)).toBeInTheDocument()
    expect(screen.getByText(/Message type ORDERS/i)).toBeInTheDocument()
    expect(screen.getByText(/Derivative ID: derivative-001/i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Load content' }))

    await screen.findByText(/"fileName": "order\.edi"/)
    expect(screen.getByText(/"text": "UNH\+1\+ORDERS:D:03B:UN:EAN008'"/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Document ID: doc-002/i }))
    await screen.findByDisplayValue('doc-002')
  })

  it('shows successful schema creation and schema-version submission results', async () => {
    const user = userEvent.setup()

    mockedDiscoverBackend.mockResolvedValue(createHealthyDiscoveryResult())
    vi.stubGlobal('fetch', vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url)
      const method = init?.method ?? 'GET'

      if (method === 'GET' && url.pathname === '/api/schemas/schema-uk') {
        return jsonResponse({
          id: 'schema-uk',
          name: 'Retail Orders XSD',
          format: 'XSD',
          versions: [{ id: 'schema-version-1', versionIdentifier: '1.0.0', createdAt: '2026-05-18T08:00:00Z', deprecated: false }],
        })
      }

      if (method === 'GET' && url.pathname === '/api/schemas/schema-uk/versions/1.0.0') {
        return jsonResponse({ id: 'schema-version-1', versionIdentifier: '1.0.0', createdAt: '2026-05-18T08:00:00Z', deprecated: false })
      }

      if (method === 'POST' && url.pathname === '/api/schemas') {
        return jsonResponse({ id: 'schema-created', name: 'Retail Orders XSD', format: 'XSD', versions: [] })
      }

      if (method === 'POST' && url.pathname === '/api/schemas/schema-uk/versions') {
        return jsonResponse({ id: 'schema-version-2', versionIdentifier: '1.1.0', createdAt: '2026-05-18T08:30:00Z', deprecated: false })
      }

      throw new Error(`Unhandled fetch for ${method} ${url.pathname}`)
    }))

    renderOpsConsole('/schemas/schema-uk?versionId=1.0.0')

    await screen.findByRole('heading', { name: 'Retail Orders XSD' })
    expect(screen.getByText(/Backend version ID schema-version-1/i)).toBeInTheDocument()
    expect(screen.getByDisplayValue('schema-uk')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Create schema' }))
    await screen.findByText(/schema-created/)

    await user.clear(screen.getByLabelText('Version identifier'))
    await user.type(screen.getByLabelText('Version identifier'), '1.1.0')
    await user.click(screen.getByRole('button', { name: 'Add schema version' }))

    await screen.findByText(/schema-version-2/)
  })
})

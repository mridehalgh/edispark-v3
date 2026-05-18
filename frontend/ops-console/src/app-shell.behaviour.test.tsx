import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  createDegradedDiscoveryResult,
  createHealthyDiscoveryResult,
  jsonResponse,
  mockedDiscoverBackend,
  renderOpsConsole,
} from '@/test/ops-console-test-support'

describe('ops console app shell behaviour', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    mockedDiscoverBackend.mockReset()
    vi.unstubAllGlobals()
  })

  it('renders the root route as the operations home with workflow navigation and a healthy connection banner', async () => {
    mockedDiscoverBackend.mockResolvedValue(createHealthyDiscoveryResult())
    vi.stubGlobal('fetch', vi.fn())

    renderOpsConsole('/')

    await screen.findByRole('heading', { name: 'Retail and e-commerce message desk' })
    await screen.findByText('Connection state: healthy')

    expect(screen.getByRole('link', { name: 'Operations home' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Document sets' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Schemas' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Validation' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'API explorer' })).toBeInTheDocument()
    expect(screen.getByText('Contract: EdiSpark Local API · version 1.0.0')).toBeInTheDocument()
    expect(screen.getAllByText('ORDERS').length).toBeGreaterThan(0)
    expect(screen.getAllByText('SLSRPT').length).toBeGreaterThan(0)
  })

  it('navigates between operator workflows from the shell navigation', async () => {
    const user = userEvent.setup()
    mockedDiscoverBackend.mockResolvedValue(createHealthyDiscoveryResult())
    vi.stubGlobal('fetch', vi.fn(async (input: string | URL | Request) => {
      const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url)

      if (url.pathname === '/api/document-sets') {
        return jsonResponse({ items: [], pageSize: 8, hasPrevious: false, hasNext: false, previousToken: null, nextToken: null })
      }

      throw new Error(`Unhandled fetch for ${url.pathname}`)
    }))

    renderOpsConsole('/')

    await screen.findByRole('heading', { name: 'Retail and e-commerce message desk' })

    await user.click(screen.getByRole('link', { name: 'Document sets' }))
    await screen.findByRole('heading', { name: 'Track retail message bundles' })

    await user.click(screen.getByRole('link', { name: 'Schemas' }))
    await screen.findByRole('heading', { name: 'Govern schema contracts for retail message validation' })

    await user.click(screen.getByRole('link', { name: 'Validation' }))
    await screen.findByRole('heading', { name: 'Run document checks against discovered schema bindings' })
  })

  it('shows list-request errors with a retry action and recovers on retry', async () => {
    const user = userEvent.setup()
    let listCalls = 0

    mockedDiscoverBackend.mockResolvedValue(createHealthyDiscoveryResult())
    vi.stubGlobal('fetch', vi.fn(async (input: string | URL | Request) => {
      const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url)

      if (url.pathname === '/api/document-sets') {
        listCalls += 1

        if (listCalls === 1) {
          return jsonResponse({ message: 'backend rejected request' }, { status: 500, statusText: 'Server Error' })
        }

        return jsonResponse({
          items: [{ id: 'set-100', createdAt: '2026-05-18T09:15:00Z', createdBy: 'ops.user', documents: [{ id: 'doc-1', type: 'ORDER', versionCount: 1 }] }],
          pageSize: 8,
          hasPrevious: false,
          hasNext: false,
          previousToken: null,
          nextToken: null,
        })
      }

      throw new Error(`Unhandled fetch for ${url.pathname}`)
    }))

    renderOpsConsole('/document-sets')

    await screen.findByText('Document-set list failed')

    await user.click(screen.getByRole('button', { name: 'Retry' }))

    await screen.findByText('Document set set-100')
    await waitFor(() => expect(listCalls).toBe(2))
  })

  it('presents degraded discovery guidance and refreshes discovery from the banner', async () => {
    const user = userEvent.setup()

    mockedDiscoverBackend
      .mockResolvedValueOnce(createDegradedDiscoveryResult())
      .mockResolvedValueOnce(createHealthyDiscoveryResult())

    vi.stubGlobal('fetch', vi.fn())

    renderOpsConsole('/document-sets')

    await screen.findByText('Connection state: degraded')
    expect(screen.getByText(/Start the Spring Boot backend on http:\/\/localhost:8080/)).toBeInTheDocument()
    expect(screen.getByText('Document-set listing unavailable')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Retry discovery' }))

    await screen.findByText('Connection state: healthy')
    expect(mockedDiscoverBackend).toHaveBeenCalledTimes(2)
  })
})

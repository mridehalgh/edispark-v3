import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import fc from 'fast-check'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

import { WorkbenchShell, primaryDestinations } from '@/app/workbench-shell'
import type { ContractState } from '@/integration/openapi-contract'

type MockIntegrationValue = {
  contractState: ContractState
  refreshContract: () => Promise<void>
}

let currentIntegration: MockIntegrationValue

vi.mock('@/integration/integration-provider', () => ({
  useIntegration: () => currentIntegration
}))

afterEach(() => {
  vi.clearAllMocks()
})

const routeArb = fc.constantFrom(...primaryDestinations.map((destination) => destination.path))
const baseUrlArb = fc.integer({ min: 1024, max: 65535 }).map((port) => `http://localhost:${port}`)
const textArb = fc.string({ minLength: 1, maxLength: 40 }).filter((value) => value.trim().length > 0)

const contractStateArb = baseUrlArb.chain((baseUrl) => {
  const openApiUrl = `${baseUrl}/api-docs`

  return fc.oneof(
    fc.constant<ContractState>({
      kind: 'loading-contract',
      baseUrl,
      openApiUrl
    }),
    fc.record({
      contractTitle: textArb,
      contractVersion: textArb,
      operationCount: fc.nat(200)
    }).map(
      ({ contractTitle, contractVersion, operationCount }) =>
        ({
          kind: 'connected',
          baseUrl,
          openApiUrl,
          contractTitle,
          contractVersion,
          operationCount
        }) satisfies ContractState
    ),
    fc.record({
      reason: textArb
    }).map(
      ({ reason }) =>
        ({
          kind: 'contract-failed',
          baseUrl,
          openApiUrl,
          reason
        }) satisfies ContractState
    ),
    fc.record({
      contractTitle: textArb,
      contractVersion: textArb,
      operationCount: fc.nat(200),
      operationId: textArb,
      reason: textArb,
      status: fc.option(fc.integer({ min: 400, max: 599 }), { nil: undefined })
    }).map(
      ({ contractTitle, contractVersion, operationCount, operationId, reason, status }) =>
        ({
          kind: 'request-failed',
          baseUrl,
          openApiUrl,
          contractTitle,
          contractVersion,
          operationCount,
          operationId,
          reason,
          status
        }) satisfies ContractState
    )
  )
})

function renderShell(route: string, contractState: ContractState) {
  currentIntegration = {
    contractState,
    refreshContract: vi.fn().mockResolvedValue(undefined)
  }

  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route element={<WorkbenchShell />} path="/">
          <Route element={<div>Dashboard child</div>} index />
          <Route element={<div>Schemas child</div>} path="schemas" />
          <Route element={<div>Document sets child</div>} path="document-sets" />
          <Route element={<div>Retail journeys child</div>} path="retail-journeys" />
          <Route element={<div>API explorer child</div>} path="api-explorer" />
        </Route>
      </Routes>
    </MemoryRouter>
  )
}

describe('workbench shell', () => {
  it('Feature: edi-operations-workbench, Property 1: Shell state projection remains consistent across routes', () => {
    fc.assert(
      fc.property(routeArb, contractStateArb, (route, contractState) => {
        cleanup()
        renderShell(route, contractState)

        expect(screen.getAllByText('EDI operations workbench').length).toBeGreaterThan(0)
        expect(screen.getByText('Page chrome')).toBeInTheDocument()
        expect(screen.getByText('Status region')).toBeInTheDocument()

        for (const destination of primaryDestinations) {
          expect(screen.getAllByText(destination.label).length).toBeGreaterThan(0)
        }

        if (contractState.kind === 'loading-contract') {
          expect(screen.getByText('Pending')).toBeInTheDocument()
          expect(screen.getByText(`Loading the live contract from ${contractState.openApiUrl}.`)).toBeInTheDocument()
          return
        }

        if (contractState.kind === 'connected') {
          expect(screen.getByText('Connected')).toBeInTheDocument()
          expect(
            screen.getByText(
              (_, element) =>
                element?.textContent ===
                `${contractState.contractTitle} v${contractState.contractVersion} with ${contractState.operationCount} live operations.`
            )
          ).toBeInTheDocument()
          return
        }

        if (contractState.kind === 'request-failed') {
          expect(screen.getByText('Degraded request')).toBeInTheDocument()
          expect(
            screen.getByText(
              (_, element) => element?.textContent === `${contractState.operationId} failed while the contract stayed connected.`
            )
          ).toBeInTheDocument()
          expect(screen.getByText((_, element) => element?.textContent === contractState.reason)).toBeInTheDocument()
          return
        }

        expect(screen.getByText('Initial application state is unavailable')).toBeInTheDocument()
        expect(screen.getByText('Backend connection unavailable')).toBeInTheDocument()
        expect(screen.getAllByText((_, element) => element?.textContent === contractState.reason).length).toBeGreaterThan(0)
      }),
      { numRuns: 100 }
    )
  })

  it('shows a recoverable error state when the contract fails to load', async () => {
    const refreshContract = vi.fn().mockResolvedValue(undefined)
    currentIntegration = {
      contractState: {
        kind: 'contract-failed',
        baseUrl: 'http://localhost:18080',
        openApiUrl: 'http://localhost:18080/api-docs',
        reason: 'OpenAPI contract request failed with status 503. Expected local backend contract at http://localhost:18080/api-docs.'
      },
      refreshContract
    }

    renderShell('/', currentIntegration.contractState)

    expect(screen.getByText('Initial application state is unavailable')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Retry contract load' })).toHaveLength(2)
    expect(screen.queryByText('Dashboard child')).not.toBeInTheDocument()

    await userEvent.click(screen.getAllByRole('button', { name: 'Retry contract load' })[0])

    expect(refreshContract).toHaveBeenCalledOnce()
  })
})

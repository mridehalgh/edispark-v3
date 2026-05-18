import type { ReactElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'

import App from '@/App'
import { discoverBackend } from '@/lib/discovery'
import type { DiscoveryResult, OpenApiDocument } from '@/lib/models'
import { buildEndpointCatalogue } from '@/lib/openapi-catalogue'

vi.mock('@/lib/discovery', async () => {
  const actual = await vi.importActual<typeof import('@/lib/discovery')>('@/lib/discovery')

  return {
    ...actual,
    discoverBackend: vi.fn(),
  }
})

const openApiDocument: OpenApiDocument = {
  info: {
    title: 'EdiSpark Local API',
    version: '1.0.0',
  },
  paths: {
    '/api/document-sets': { get: { summary: 'List document sets', tags: ['Document Sets'] }, post: { summary: 'Create document set', tags: ['Document Sets'] } },
    '/api/document-sets/{id}': { get: { summary: 'Get document set', tags: ['Document Sets'] } },
    '/api/document-sets/{setId}/documents': { post: { summary: 'Add document', tags: ['Documents'] } },
    '/api/document-sets/{setId}/documents/{docId}': { get: { summary: 'Get document', tags: ['Documents'] } },
    '/api/document-sets/{setId}/documents/{docId}/versions': { post: { summary: 'Add version', tags: ['Documents'] } },
    '/api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}': { get: { summary: 'Get version', tags: ['Documents'] } },
    '/api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}/content': { get: { summary: 'Get version content', tags: ['Documents'] } },
    '/api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}/validate': { post: { summary: 'Validate document', tags: ['Validation'] } },
    '/api/document-sets/{setId}/documents/{docId}/derivatives': {
      get: { summary: 'List derivatives', tags: ['Derivatives'] },
      post: { summary: 'Create derivative', tags: ['Derivatives'] },
    },
    '/api/schemas': { get: { summary: 'List schemas', tags: ['Schemas'] }, post: { summary: 'Create schema', tags: ['Schemas'] } },
    '/api/schemas/{id}': { get: { summary: 'Get schema', tags: ['Schemas'] } },
    '/api/schemas/{schemaId}/versions': { post: { summary: 'Add schema version', tags: ['Schemas'] } },
    '/api/schemas/{schemaId}/versions/{versionId}': { get: { summary: 'Get schema version', tags: ['Schemas'] } },
  },
}

export const mockedDiscoverBackend = vi.mocked(discoverBackend)

export const createHealthyDiscoveryResult = (): DiscoveryResult => {
  const catalogue = buildEndpointCatalogue(openApiDocument)

  return {
    connection: {
      status: 'healthy',
      baseUrl: 'http://localhost:8080',
      openApiUrl: 'http://localhost:8080/api-docs',
      sourceTitle: catalogue.sourceTitle,
      sourceVersion: catalogue.sourceVersion,
      message: 'Connected through default discovery',
    },
    catalogue,
  }
}

export const createDegradedDiscoveryResult = (): DiscoveryResult => ({
  connection: {
    status: 'degraded',
    baseUrl: 'http://localhost:8080',
    openApiUrl: 'http://localhost:8080/api-docs',
    message: 'DISCOVERY_UNREACHABLE: 503 Service Unavailable. Start the Spring Boot backend on http://localhost:8080 and make sure /api-docs is published.',
  },
})

const renderWithProviders = (route: string, element: ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>{element}</MemoryRouter>
    </QueryClientProvider>,
  )
}

export const renderOpsConsole = (route = '/') => renderWithProviders(route, <App />)

export const jsonResponse = (payload: unknown, init: ResponseInit = {}) =>
  new Response(JSON.stringify(payload), {
    status: init.status ?? 200,
    statusText: init.statusText,
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers ?? {}),
    },
  })

export const textResponse = (payload: string, init: ResponseInit = {}) =>
  new Response(payload, {
    status: init.status ?? 200,
    statusText: init.statusText,
    headers: {
      'Content-Type': 'text/plain',
      ...(init.headers ?? {}),
    },
  })

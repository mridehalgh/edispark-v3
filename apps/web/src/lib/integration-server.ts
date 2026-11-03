import 'server-only'

import { IntegrationClientError, createIntegrationClient } from '@edispark/integration-client'

import { canManageOrganization } from '@/lib/access'
import { auth, getRequestAccess } from '@/lib/auth'

const DEFAULT_INTEGRATION_API_URL = 'http://localhost:8083'

export class IntegrationBffError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message)
    this.name = 'IntegrationBffError'
  }
}

export async function createAuthenticatedIntegrationClient(request: Request, required: 'member' | 'admin' = 'admin') {
  const authHeaders = request.headers
  const session = await auth.api.getSession({ headers: authHeaders })
  if (!session) throw new IntegrationBffError(401, 'UNAUTHENTICATED', 'Sign in to manage your trading network.')
  const access = await getRequestAccess(authHeaders)
  if (!access || (required === 'admin' && !canManageOrganization(access.organizationRole))) {
    throw new IntegrationBffError(403, 'FORBIDDEN', 'Organization administrator access is required.')
  }

  let tokenPromise: Promise<string> | undefined
  const accessToken = () => {
    tokenPromise ??= auth.api.getToken({ headers: authHeaders }).then(({ token }) => token)
    return tokenPromise
  }

  return createIntegrationClient({
    baseUrl: (process.env.INTEGRATION_API_URL ?? DEFAULT_INTEGRATION_API_URL).replace(/\/+$/, ''),
    accessToken,
    headers: { 'X-Correlation-ID': request.headers.get('x-correlation-id') ?? crypto.randomUUID() },
  })
}

export function integrationErrorResponse(error: unknown) {
  if (error instanceof IntegrationBffError) {
    return Response.json({ error: { code: error.code, message: error.message } }, { status: error.status })
  }
  if (error instanceof IntegrationClientError) {
    const status = [400, 401, 403, 404, 409, 413, 422, 429, 503].includes(error.status) ? error.status : 502
    return Response.json({ error: { code: error.code, message: error.message } }, { status })
  }
  console.error('Integration API request failed', error)
  const message = process.env.NODE_ENV === 'development'
    ? 'The Integration API is not reachable. Start the complete local stack with `pnpm dev`.'
    : 'The trading network service is unavailable. Try again shortly.'
  return Response.json({ error: { code: 'INTEGRATION_API_UNAVAILABLE', message } }, { status: 502 })
}

export function isIntegrationResourceId(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
}

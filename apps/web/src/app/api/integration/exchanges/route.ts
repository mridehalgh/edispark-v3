import type { SubmitExchangeRequest } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse } from '@/lib/integration-server'

export const dynamic = 'force-dynamic'

export async function GET(request: Request) {
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.listExchanges({ throwOnError: true })
    return Response.json(data, { headers: { 'Cache-Control': 'private, no-store' } })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export async function POST(request: Request) {
  try {
    const body = await request.json() as SubmitExchangeRequest
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.submitOutboundExchange({ body, throwOnError: true })
    return Response.json(data, { status: 202 })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

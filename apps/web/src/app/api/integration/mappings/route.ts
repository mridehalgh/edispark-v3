import type { CreateMappingRequest } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse } from '@/lib/integration-server'

export const dynamic = 'force-dynamic'

export async function GET(request: Request) {
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.listMappings({ throwOnError: true })
    return Response.json(data, { headers: { 'Cache-Control': 'private, no-store' } })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export async function POST(request: Request) {
  try {
    const body = await request.json() as CreateMappingRequest
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.createMapping({ body, throwOnError: true })
    return Response.json(data, { status: 201 })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

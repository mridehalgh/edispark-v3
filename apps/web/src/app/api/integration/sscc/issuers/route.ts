import type { CreateSsccIssuerRequest } from "@edispark/integration-client"

import { createAuthenticatedIntegrationClient, integrationErrorResponse } from "@/lib/integration-server"

export const dynamic = "force-dynamic"

export async function GET(request: Request) {
  try {
    const client = await createAuthenticatedIntegrationClient(request, "member")
    const { data } = await client.listSsccIssuers({ throwOnError: true })
    return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export async function POST(request: Request) {
  try {
    const body = await request.json() as CreateSsccIssuerRequest
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.createSsccIssuer({ body, throwOnError: true })
    return Response.json(data, { status: 201 })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

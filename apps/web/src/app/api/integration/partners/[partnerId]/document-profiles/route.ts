import type { DocumentProfileRequest } from "@edispark/integration-client"

import { createAuthenticatedIntegrationClient, integrationErrorResponse } from "@/lib/integration-server"

export const dynamic = "force-dynamic"

export async function GET(request: Request, { params: pendingParams }: { params: Promise<{ partnerId: string }> }) {
  const params = await pendingParams
  try {
    const client = await createAuthenticatedIntegrationClient(request, "member")
    const { data } = await client.listPartnerDocumentProfiles({ path: params, throwOnError: true })
    return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export async function POST(request: Request, { params: pendingParams }: { params: Promise<{ partnerId: string }> }) {
  const params = await pendingParams
  try {
    const body = await request.json() as DocumentProfileRequest
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.createPartnerDocumentProfile({ path: params, body, throwOnError: true })
    return Response.json(data, { status: 201 })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

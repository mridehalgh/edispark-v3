import type { ReserveSsccRequest } from "@edispark/integration-client"

import { createAuthenticatedIntegrationClient, integrationErrorResponse } from "@/lib/integration-server"

export async function POST(request: Request) {
  try {
    const body = await request.json() as ReserveSsccRequest
    const client = await createAuthenticatedIntegrationClient(request, "member")
    const { data } = await client.reserveSsccs({ body, throwOnError: true })
    return Response.json(data, { status: 201 })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

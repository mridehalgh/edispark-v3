import type { ReadinessRequest } from "@edispark/integration-client"
import {
  createAuthenticatedIntegrationClient,
  integrationErrorResponse,
} from "@/lib/integration-server"

export const dynamic = "force-dynamic"

export async function POST(request: Request) {
  try {
    const client = await createAuthenticatedIntegrationClient(request, "member")
    const { data } = await client.checkExchangeReadiness({
      body: (await request.json()) as ReadinessRequest,
      throwOnError: true,
    })
    return Response.json(data, {
      headers: { "Cache-Control": "private, no-store" },
    })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

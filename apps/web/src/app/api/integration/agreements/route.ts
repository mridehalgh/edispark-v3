import { createAuthenticatedIntegrationClient, integrationErrorResponse } from '@/lib/integration-server'

export const dynamic = 'force-dynamic'

export async function GET(request: Request) {
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.listTradingAgreements({ throwOnError: true })
    return Response.json(data, { headers: { 'Cache-Control': 'private, no-store' } })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

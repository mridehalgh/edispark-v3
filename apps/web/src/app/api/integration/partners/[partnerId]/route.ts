import type { CreatePartnerRequest } from '@edispark/integration-client'
import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

interface RouteContext { params: { partnerId: string } }

export async function PUT(request: Request, { params }: RouteContext) {
  if (!isIntegrationResourceId(params.partnerId)) return Response.json({ error: { code: 'INVALID_PARTNER', message: 'The partner identifier is invalid.' } }, { status: 400 })
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.updateTradingPartnerDraft({ body: await request.json() as CreatePartnerRequest, path: { partnerId: params.partnerId }, throwOnError: true })
    return Response.json(data)
  } catch (error) { return integrationErrorResponse(error) }
}

export async function DELETE(request: Request, { params }: RouteContext) {
  if (!isIntegrationResourceId(params.partnerId)) return Response.json({ error: { code: 'INVALID_PARTNER', message: 'The partner identifier is invalid.' } }, { status: 400 })
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    await client.deleteTradingPartnerDraft({ path: { partnerId: params.partnerId }, throwOnError: true })
    return new Response(null, { status: 204 })
  } catch (error) { return integrationErrorResponse(error) }
}

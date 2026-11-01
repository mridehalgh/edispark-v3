import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

interface RouteContext { params: { agreementId: string } }

export async function DELETE(request: Request, { params }: RouteContext) {
  if (!isIntegrationResourceId(params.agreementId)) return Response.json({ error: { code: 'INVALID_AGREEMENT', message: 'The agreement identifier is invalid.' } }, { status: 400 })
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    await client.deleteTradingAgreementDraft({ path: { agreementId: params.agreementId }, throwOnError: true })
    return new Response(null, { status: 204 })
  } catch (error) { return integrationErrorResponse(error) }
}

export async function GET(request: Request, { params }: RouteContext) {
  if (!isIntegrationResourceId(params.agreementId)) return Response.json({ error: { code: 'INVALID_AGREEMENT', message: 'The agreement identifier is invalid.' } }, { status: 400 })
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.getTradingAgreementUsage({ path: { agreementId: params.agreementId }, throwOnError: true })
    return Response.json(data)
  } catch (error) { return integrationErrorResponse(error) }
}

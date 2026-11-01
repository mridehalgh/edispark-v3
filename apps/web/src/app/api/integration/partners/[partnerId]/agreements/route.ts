import type { CreateAgreementRequest } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

export async function POST(request: Request, context: { params: { partnerId: string } }) {
  if (!isIntegrationResourceId(context.params.partnerId)) {
    return Response.json({ error: { code: 'INVALID_PARTNER_ID', message: 'The trading partner identifier is invalid.' } }, { status: 400 })
  }
  try {
    const body = await request.json() as CreateAgreementRequest
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.createTradingAgreement({
      body,
      path: { partnerId: context.params.partnerId },
      throwOnError: true,
    })
    return Response.json(data, { status: 201 })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

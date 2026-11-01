import type { CapabilityRequest } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

export async function POST(request: Request, context: { params: { agreementId: string } }) {
  if (!isIntegrationResourceId(context.params.agreementId)) {
    return Response.json({ error: { code: 'INVALID_AGREEMENT_ID', message: 'The agreement identifier is invalid.' } }, { status: 400 })
  }
  try {
    const body = await request.json() as CapabilityRequest
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.addExchangeCapability({
      body,
      path: { agreementId: context.params.agreementId },
      throwOnError: true,
    })
    return Response.json(data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

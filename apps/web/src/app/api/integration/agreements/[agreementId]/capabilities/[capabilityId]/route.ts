import type { CapabilityRequest } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

type RouteContext = { params: Promise<{ agreementId: string; capabilityId: string }> }

async function invalidIdentifiers(context: RouteContext) {
  const { agreementId, capabilityId } = (await context.params)
  return !isIntegrationResourceId(agreementId) || !isIntegrationResourceId(capabilityId)
}

export async function PUT(request: Request, context: RouteContext) {
  if (await invalidIdentifiers(context)) {
    return Response.json({ error: { code: 'INVALID_CAPABILITY_ID', message: 'The agreement route identifier is invalid.' } }, { status: 400 })
  }
  try {
    const body = await request.json() as CapabilityRequest
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.replaceExchangeCapability({
      body,
      path: (await context.params),
      throwOnError: true,
    })
    return Response.json(data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export async function DELETE(request: Request, context: RouteContext) {
  if (await invalidIdentifiers(context)) {
    return Response.json({ error: { code: 'INVALID_CAPABILITY_ID', message: 'The agreement route identifier is invalid.' } }, { status: 400 })
  }
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.removeExchangeCapability({
      path: (await context.params),
      throwOnError: true,
    })
    return Response.json(data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

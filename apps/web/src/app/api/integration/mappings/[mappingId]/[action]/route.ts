import type { PublishMappingRequest, UpdateMappingRequest } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

const ACTIONS = ['draft', 'test', 'publish'] as const

export async function POST(request: Request, context: { params: { mappingId: string; action: string } }) {
  const { mappingId, action } = context.params
  if (!isIntegrationResourceId(mappingId) || !ACTIONS.includes(action as typeof ACTIONS[number])) {
    return Response.json({ error: { code: 'INVALID_MAPPING_ACTION', message: 'The mapping action is invalid.' } }, { status: 400 })
  }
  try {
    const body = await request.json()
    const client = await createAuthenticatedIntegrationClient(request)
    const result = action === 'test'
      ? await client.testMapping({ body, path: { mappingId }, throwOnError: true })
      : await client.publishMapping({ body: body as PublishMappingRequest, path: { mappingId }, throwOnError: true })
    return Response.json(result.data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export async function PUT(request: Request, context: { params: { mappingId: string; action: string } }) {
  const { mappingId, action } = context.params
  if (!isIntegrationResourceId(mappingId) || action !== 'draft') {
    return Response.json({ error: { code: 'INVALID_MAPPING_ACTION', message: 'The mapping action is invalid.' } }, { status: 400 })
  }
  try {
    const body = await request.json() as UpdateMappingRequest
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.updateMappingDraft({ body, path: { mappingId }, throwOnError: true })
    return Response.json(data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

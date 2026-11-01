import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

interface RouteContext { params: { mappingId: string } }

export async function DELETE(request: Request, { params }: RouteContext) {
  if (!isIntegrationResourceId(params.mappingId)) return Response.json({ error: { code: 'INVALID_MAPPING', message: 'The mapping identifier is invalid.' } }, { status: 400 })
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    await client.deleteMappingDraft({ path: { mappingId: params.mappingId }, throwOnError: true })
    return new Response(null, { status: 204 })
  } catch (error) { return integrationErrorResponse(error) }
}

export async function GET(request: Request, { params }: RouteContext) {
  if (!isIntegrationResourceId(params.mappingId)) return Response.json({ error: { code: 'INVALID_MAPPING', message: 'The mapping identifier is invalid.' } }, { status: 400 })
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.getMapping({ path: { mappingId: params.mappingId }, throwOnError: true })
    return Response.json(data)
  } catch (error) { return integrationErrorResponse(error) }
}

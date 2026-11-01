import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

export async function GET(request: Request, context: { params: { mappingId: string } }) {
  const { mappingId } = context.params
  if (!isIntegrationResourceId(mappingId)) {
    return Response.json({ error: { code: 'INVALID_MAPPING', message: 'The mapping identifier is invalid.' } }, { status: 400 })
  }
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.getMappingUsage({ path: { mappingId }, throwOnError: true })
    return Response.json(data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

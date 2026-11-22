import { zMappingValidationRequest } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

export async function POST(request: Request, { params }: { params: Promise<{ mappingId: string }> }) {
  const { mappingId } = await params
  if (!isIntegrationResourceId(mappingId)) {
    return Response.json({ error: { code: 'INVALID_MAPPING', message: 'The mapping identifier is invalid.' } }, { status: 400 })
  }

  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const body = zMappingValidationRequest.safeParse(await request.json().catch(() => undefined))
    if (!body.success) {
      return Response.json({ error: { code: 'INVALID_VALIDATION_DOCUMENT', message: 'Provide a mapping side and a document up to 1 MiB.' } }, { status: 400 })
    }
    const { data } = await client.validateMappingDocument({ path: { mappingId }, body: body.data, signal: request.signal, throwOnError: true })
    return Response.json(data, { headers: { 'Cache-Control': 'private, no-store' } })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

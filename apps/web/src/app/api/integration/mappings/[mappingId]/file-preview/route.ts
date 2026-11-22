import { zFilePreviewRequest } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

type RouteContext = { params: Promise<{ mappingId: string }> }

function invalidMapping() {
  return Response.json({ error: { code: 'INVALID_MAPPING', message: 'The mapping identifier is invalid.' } }, { status: 400 })
}

export async function GET(request: Request, { params }: RouteContext) {
  const { mappingId } = await params
  if (!isIntegrationResourceId(mappingId)) return invalidMapping()
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.getMappingExampleFiles({ path: { mappingId }, signal: request.signal, throwOnError: true })
    return Response.json(data, { headers: { 'Cache-Control': 'private, no-store' } })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export async function POST(request: Request, { params }: RouteContext) {
  const { mappingId } = await params
  if (!isIntegrationResourceId(mappingId)) return invalidMapping()
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const body = zFilePreviewRequest.safeParse(await request.json().catch(() => undefined))
    if (!body.success) return Response.json({ error: { code: 'INVALID_PREVIEW_FILE', message: 'Choose a text file of 1 MiB or smaller.' } }, { status: 400 })
    const { data } = await client.previewMappingFile({ path: { mappingId }, body: body.data, signal: request.signal, throwOnError: true })
    return Response.json(data, { headers: { 'Cache-Control': 'private, no-store' } })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

export const dynamic = 'force-dynamic'

export async function GET(request: Request) {
  const url = new URL(request.url)
  const documentSetId = url.searchParams.get('documentSetId') ?? ''
  const documentId = url.searchParams.get('documentId') ?? ''
  if (!isIntegrationResourceId(documentSetId) || !isIntegrationResourceId(documentId)) {
    return Response.json({ error: { code: 'INVALID_DOCUMENT_ID', message: 'The document identifiers are invalid.' } }, { status: 400 })
  }
  try {
    const client = await createAuthenticatedIntegrationClient(request, 'member')
    const { data } = await client.listEligibleExchangeRoutes({
      query: { documentSetId, documentId },
      throwOnError: true,
    })
    return Response.json(data, { headers: { 'Cache-Control': 'private, no-store' } })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

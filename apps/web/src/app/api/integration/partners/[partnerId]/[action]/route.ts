import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

const ACTIONS = ['revisions', 'activate', 'archive'] as const

export async function POST(request: Request, { params }: { params: { partnerId: string; action: string } }) {
  if (!isIntegrationResourceId(params.partnerId) || !ACTIONS.includes(params.action as typeof ACTIONS[number])) return Response.json({ error: { code: 'INVALID_PARTNER_ACTION', message: 'The partner action is invalid.' } }, { status: 400 })
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const path = { partnerId: params.partnerId }
    const result = params.action === 'revisions'
      ? await client.createTradingPartnerRevision({ path, throwOnError: true })
      : params.action === 'activate'
        ? await client.activateTradingPartnerRevision({ path, body: await request.json().catch(() => ({})), throwOnError: true })
        : await client.archiveTradingPartnerRevision({ path, throwOnError: true })
    return Response.json(result.data)
  } catch (error) { return integrationErrorResponse(error) }
}

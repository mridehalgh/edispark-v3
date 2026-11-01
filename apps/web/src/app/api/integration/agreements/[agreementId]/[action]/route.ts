import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from '@/lib/integration-server'

const ACTIONS = ['qualify', 'activate', 'suspend', 'revisions', 'archive'] as const
type AgreementAction = typeof ACTIONS[number]

export async function POST(request: Request, context: { params: { agreementId: string; action: string } }) {
  const { agreementId, action } = context.params
  if (!isIntegrationResourceId(agreementId) || !ACTIONS.includes(action as AgreementAction)) {
    return Response.json({ error: { code: 'INVALID_AGREEMENT_ACTION', message: 'The agreement action is invalid.' } }, { status: 400 })
  }
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const options = { path: { agreementId }, throwOnError: true as const }
    const result = action === 'qualify'
      ? await client.qualifyTradingAgreement(options)
      : action === 'activate'
        ? await client.activateTradingAgreement({ ...options, body: await request.json().catch(() => ({})) })
        : action === 'suspend'
          ? await client.suspendTradingAgreement(options)
          : action === 'revisions'
            ? await client.createTradingAgreementRevision(options)
            : await client.archiveTradingAgreementRevision(options)
    return Response.json(result.data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

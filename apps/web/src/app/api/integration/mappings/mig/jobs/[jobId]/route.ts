import { zCreateMigDraftBody, zReviewMigJobBody } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse, IntegrationBffError, isIntegrationResourceId } from '@/lib/integration-server'
import { boundedMigJson, migResponse, requireMigOrigin } from '@/lib/mig-server'

type Context = { params: Promise<{ jobId: string }> }

async function options(request: Request, context: Context) {
  const { jobId } = await context.params
  if (!isIntegrationResourceId(jobId)) throw new IntegrationBffError(400, 'INVALID_MIG_JOB', 'Choose a valid MIG job identifier.')
  return { path: { jobId }, signal: request.signal, throwOnError: true as const }
}

export async function GET(request: Request, context: Context) {
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.getMigJob(await options(request, context))
    return migResponse(data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export async function POST(request: Request, context: Context) {
  try {
    requireMigOrigin(request)
    const client = await createAuthenticatedIntegrationClient(request)
    const base = await options(request, context)
    const action = new URL(request.url).searchParams.get('action')
    switch (action) {
      case 'start': return migResponse((await client.startMigJob(base)).data)
      case 'retry': return migResponse((await client.retryMigJob(base)).data)
      case 'rerun': return migResponse((await client.rerunMigJob(base)).data, 201)
      case 'cancel': return migResponse((await client.cancelMigJob(base)).data)
      case 'review': {
        const body = zReviewMigJobBody.safeParse(await boundedMigJson(request))
        if (!body.success) throw new IntegrationBffError(400, 'INVALID_MIG_REVIEW', 'Provide valid review decisions and reference bindings.')
        return migResponse((await client.reviewMigJob({ ...base, body: body.data })).data)
      }
      case 'draft': {
        const body = zCreateMigDraftBody.safeParse(await boundedMigJson(request))
        if (!body.success) throw new IntegrationBffError(400, 'INVALID_MIG_DRAFT', 'Enter a name for the draft.')
        return migResponse((await client.createMigDraft({ ...base, body: body.data })).data)
      }
      default: throw new IntegrationBffError(400, 'INVALID_MIG_ACTION', 'Choose a MIG job action.')
    }
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

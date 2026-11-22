import { zEstimateMigJobBody } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse, IntegrationBffError } from '@/lib/integration-server'
import { boundedMigJson, migResponse, requireMigOrigin } from '@/lib/mig-server'

export async function GET(request: Request) {
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.getMigGenerationAvailability({ signal: request.signal, throwOnError: true })
    return migResponse(data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export async function POST(request: Request) {
  try {
    requireMigOrigin(request)
    const client = await createAuthenticatedIntegrationClient(request)
    const parsed = zEstimateMigJobBody.safeParse(await boundedMigJson(request))
    if (!parsed.success) throw new IntegrationBffError(400, 'INVALID_MIG_ESTIMATE', 'Select a primary guide and valid exchange coordinates.')
    const { data } = await client.estimateMigJob({ body: parsed.data, signal: request.signal, throwOnError: true })
    return migResponse(data, 201)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

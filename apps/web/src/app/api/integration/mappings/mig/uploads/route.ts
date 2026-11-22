import { zUploadMigFileBody } from '@edispark/integration-client'

import { createAuthenticatedIntegrationClient, integrationErrorResponse, IntegrationBffError } from '@/lib/integration-server'
import { boundedMigJson, migResponse, requireMigOrigin } from '@/lib/mig-server'

export async function POST(request: Request) {
  try {
    requireMigOrigin(request)
    const client = await createAuthenticatedIntegrationClient(request)
    const parsed = zUploadMigFileBody.safeParse(await boundedMigJson(request, 4_200_000))
    if (!parsed.success) throw new IntegrationBffError(400, 'INVALID_MIG_UPLOAD', 'Supply a guide up to 3 MiB or a UTF-8 fixture up to 1 MiB.')
    const { data } = await client.uploadMigFile({ body: parsed.data, signal: request.signal, throwOnError: true })
    return migResponse(data, 201)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

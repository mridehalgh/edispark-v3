import 'server-only'

import { IntegrationBffError } from './integration-server'

export function migResponse(data: unknown, status = 200) {
  return Response.json(data, { status, headers: { 'Cache-Control': 'private, no-store' } })
}

export function requireMigOrigin(request: Request) {
  const origin = new URL(process.env.BETTER_AUTH_URL ?? request.url).origin
  if (request.headers.get('origin') !== origin) {
    throw new IntegrationBffError(403, 'FORBIDDEN', 'Submit MIG changes from the workbench.')
  }
}

export async function boundedMigJson(request: Request, limit = 128 * 1024): Promise<unknown> {
  const reader = request.body?.getReader()
  const decoder = new TextDecoder('utf-8', { fatal: true })
  if (!reader) return undefined
  let received = 0
  const parts: string[] = []
  try {
    for (let chunk = await reader.read(); !chunk.done; chunk = await reader.read()) {
      received += chunk.value.byteLength
      if (received > limit) {
        await reader.cancel()
        throw new IntegrationBffError(413, 'MIG_REQUEST_TOO_LARGE', 'The MIG request exceeds its size limit.')
      }
      parts.push(decoder.decode(chunk.value, { stream: true }))
    }
    parts.push(decoder.decode())
    return JSON.parse(parts.join(''))
  } catch (error) {
    if (error instanceof IntegrationBffError) throw error
    throw new IntegrationBffError(400, 'INVALID_MIG_REQUEST', 'Submit well-formed MIG settings.')
  } finally {
    reader.releaseLock()
  }
}

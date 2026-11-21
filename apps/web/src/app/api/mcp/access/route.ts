import { timingSafeEqual } from "node:crypto"
import { z } from "zod"
import { authReady, hasMcpAccess, mcpEnabled, mcpScopes } from "@/lib/auth"

const id = z.string().regex(/^[A-Za-z0-9_:.~-]{1,128}$/)
const identity = z.object({
  organizationId: id,
  tenantId: z.string().regex(/^[A-Za-z0-9]{27}$/),
  subject: id,
  clientId: id,
  scopes: z.array(z.enum(mcpScopes)).min(1).max(mcpScopes.length),
}).strict()

export async function POST(request: Request) {
  const secret = process.env.EDISPARK_MCP_ACCESS_SECRET
  if (!mcpEnabled || !secret || secret.length < 32) return new Response(null, { status: 404 })
  const expected = Buffer.from(`Bearer ${secret}`)
  const supplied = Buffer.from(request.headers.get("Authorization") ?? "")
  if (supplied.length !== expected.length || !timingSafeEqual(supplied, expected)) {
    return new Response(null, { status: 403 })
  }
  const parsed = identity.safeParse(await request.json().catch(() => null))
  if (!parsed.success) return new Response(null, { status: 403 })
  await authReady
  const allowed = await hasMcpAccess(parsed.data)
  return new Response(null, { status: allowed ? 204 : 403, headers: { "Cache-Control": "no-store" } })
}

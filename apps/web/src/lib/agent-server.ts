import "server-only"
import { createHash } from "node:crypto"
import { auth, getRequestAccess } from "./auth"

export const agentBase = (process.env.EDISPARK_API_URL ?? process.env.DOCUMENTS_API_URL ?? "http://localhost:8080") + "/api/agent"

export async function agentIdentity(request: Request) {
  const session = await auth.api.getSession({ headers: request.headers })
  const access = await getRequestAccess(request.headers)
  const workspace = (session?.session as { activeOrganizationId?: string } | undefined)?.activeOrganizationId
  if (!access || !session || !workspace) return null
  const { token } = await auth.api.getToken({ headers: request.headers })
  const claims = JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"))
  if (claims.sub !== session.user.id || claims.organizationId !== workspace ||
      claims.organization_role !== access.organizationRole) return null
  return {
    token,
    id: createHash("sha256").update(JSON.stringify([session.user.id, workspace, access.organizationRole,
      claims.agent_scopes])).digest("hex"),
  }
}

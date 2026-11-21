import { oauthProviderAuthServerMetadata } from "@better-auth/oauth-provider"
import { auth, mcpEnabled } from "@/lib/auth"

export async function GET(request: Request) {
  if (!mcpEnabled) return new Response(null, { status: 404 })
  return oauthProviderAuthServerMetadata(auth)(request)
}

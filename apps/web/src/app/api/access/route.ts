import { getRequestAccess, getRequestPlatformRole } from "@/lib/auth"

export const dynamic = "force-dynamic"

export async function GET(request: Request) {
  const workspace = await getRequestAccess(request.headers, { includeMcpSetup: true })
  const platformRole = workspace ? undefined : await getRequestPlatformRole(request.headers)
  const access = workspace ?? (platformRole ? { platformRole } : undefined)
  if (!access) {
    return Response.json(
      { error: { code: "UNAUTHENTICATED", message: "Sign in to continue." } },
      { status: 401 },
    )
  }

  return Response.json(access, { headers: { "Cache-Control": "private, no-store" } })
}

import { getRequestAccess } from "@/lib/auth"

export const dynamic = "force-dynamic"

export async function GET(request: Request) {
  const access = await getRequestAccess(request.headers)
  if (!access) {
    return Response.json(
      { error: { code: "UNAUTHENTICATED", message: "Sign in to continue." } },
      { status: 401 },
    )
  }

  return Response.json(access, { headers: { "Cache-Control": "private, no-store" } })
}

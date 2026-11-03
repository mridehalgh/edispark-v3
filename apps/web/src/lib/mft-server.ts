import "server-only"

import { MftClientError, createMftClient } from "@edispark/mft-client"

import { canManageOrganization } from "@/lib/access"
import { auth, getRequestAccess } from "@/lib/auth"

const DEFAULT_MFT_API_URL = "http://localhost:8081"

export class MftBffError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message)
    this.name = "MftBffError"
  }
}

export async function createAuthenticatedMftClient(request: Request) {
  const authHeaders = request.headers
  const session = await auth.api.getSession({ headers: authHeaders })
  if (!session) throw new MftBffError(401, "UNAUTHENTICATED", "Sign in to access managed file transfer.")
  const access = await getRequestAccess(authHeaders)
  if (!access || !canManageOrganization(access.organizationRole)) {
    throw new MftBffError(403, "FORBIDDEN", "Organization administrator access is required.")
  }

  let tokenPromise: Promise<string> | undefined
  const accessToken = () => {
    tokenPromise ??= auth.api.getToken({ headers: authHeaders }).then(({ token }) => token)
    return tokenPromise
  }

  return createMftClient({
    baseUrl: (process.env.MFT_API_URL ?? DEFAULT_MFT_API_URL).replace(/\/+$/, ""),
    accessToken,
    headers: { "X-Correlation-ID": request.headers.get("x-correlation-id") ?? crypto.randomUUID() },
  })
}

export function mftErrorResponse(error: unknown) {
  if (error instanceof MftBffError) {
    return Response.json({ error: { code: error.code, message: error.message } }, { status: error.status })
  }
  if (error instanceof MftClientError) {
    const status = [400, 401, 403, 404, 409, 413, 429, 503].includes(error.status) ? error.status : 502
    return Response.json({ error: { code: error.code, message: error.message } }, { status })
  }
  console.error("MFT API request failed", error)
  const message = process.env.NODE_ENV === "development"
    ? "The MFT API is not reachable. Start it with `pnpm dev:mft-api`, or run the complete stack with `pnpm dev`."
    : "The MFT service is unavailable. Try again shortly."
  return Response.json(
    { error: { code: "MFT_API_UNAVAILABLE", message } },
    { status: 502 },
  )
}

export function isMftResourceId(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
}

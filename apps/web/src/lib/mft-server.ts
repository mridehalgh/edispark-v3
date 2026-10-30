import "server-only"

import { createMftClient } from "@edispark/mft-client"

import { auth } from "@/lib/auth"

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
  console.error("MFT API request failed", error)
  const message = process.env.NODE_ENV === "development"
    ? "The MFT API is not reachable. Start it with `pnpm dev:mft-api`, or run the complete stack with `pnpm dev`."
    : "The MFT service is unavailable. Try again shortly."
  return Response.json(
    { error: { code: "MFT_API_UNAVAILABLE", message } },
    { status: 502 },
  )
}

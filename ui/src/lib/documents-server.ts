import "server-only"

import { createDocumentsClient } from "@edispark/documents-client"

import { auth } from "@/lib/auth"

const DEFAULT_DOCUMENTS_API_URL = "http://localhost:8080"

export class DocumentsBffError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message)
    this.name = "DocumentsBffError"
  }
}

export async function createAuthenticatedDocumentsClient(request: Request) {
  const authHeaders = request.headers
  const session = await auth.api.getSession({ headers: authHeaders })

  if (!session) {
    throw new DocumentsBffError(401, "UNAUTHENTICATED", "Sign in to access documents.")
  }

  let tokenPromise: Promise<string> | undefined
  const accessToken = () => {
    tokenPromise ??= auth.api.getToken({ headers: authHeaders }).then(({ token }) => token)
    return tokenPromise
  }

  return createDocumentsClient({
    baseUrl: (process.env.DOCUMENTS_API_URL ?? DEFAULT_DOCUMENTS_API_URL).replace(/\/+$/, ""),
    accessToken,
    headers: {
      "X-Correlation-ID": request.headers.get("x-correlation-id") ?? crypto.randomUUID(),
    },
  })
}

export function documentsErrorResponse(error: unknown) {
  if (error instanceof DocumentsBffError) {
    return Response.json(
      { error: { code: error.code, message: error.message } },
      { status: error.status },
    )
  }

  console.error("Documents API request failed", error)
  return Response.json(
    {
      error: {
        code: "DOCUMENTS_API_UNAVAILABLE",
        message: "Documents could not be loaded. Try again in a moment.",
      },
    },
    { status: 502 },
  )
}

export function isUuid(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
}

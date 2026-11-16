import type { CreateDerivativeRequest } from "@edispark/documents-client"

import { createAuthenticatedDocumentsClient, documentsErrorResponse } from "@/lib/documents-server"

export async function POST(request: Request, { params: pendingParams }: { params: Promise<{ setId: string; documentId: string }> }) {
  const params = await pendingParams
  try {
    const body = await request.json() as CreateDerivativeRequest
    const client = await createAuthenticatedDocumentsClient(request)
    const result = await client.createDerivative({
      path: { setId: params.setId, docId: params.documentId }, body,
    })
    if (result.error || !result.data) {
      const upstream = result.error as { code?: string; message?: string } | undefined
      return Response.json({ error: {
        code: upstream?.code ?? "DERIVATIVE_CREATION_FAILED",
        message: upstream?.message ?? "The requested document could not be generated.",
      } }, { status: result.response?.status ?? 502 })
    }
    return Response.json(result.data, { status: 201 })
  } catch (error) {
    return documentsErrorResponse(error)
  }
}

import {
  createAuthenticatedDocumentsClient,
  documentsErrorResponse,
  isUuid,
} from "@/lib/documents-server"

interface RouteContext {
  params: { setId: string; documentId: string }
}

/** Finalization proves document validity. Pinned partner requirements are enforced by route readiness. */
export async function POST(request: Request, { params }: RouteContext) {
  if (!isUuid(params.setId) || !isUuid(params.documentId)) {
    return Response.json(
      {
        error: {
          code: "INVALID_DOCUMENT_ID",
          message: "The document identifier is invalid.",
        },
      },
      { status: 400 },
    )
  }
  try {
    const client = await createAuthenticatedDocumentsClient(request)
    const { data } = await client.finalizeDocument({
      path: { setId: params.setId, docId: params.documentId },
      throwOnError: true,
    })
    return Response.json(data)
  } catch (error) {
    return documentsErrorResponse(error)
  }
}

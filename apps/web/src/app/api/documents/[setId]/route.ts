import {
  createAuthenticatedDocumentsClient,
  documentsErrorResponse,
  isUuid,
} from "@/lib/documents-server"

interface RouteContext {
  params: {
    setId: string
  }
}

export const dynamic = "force-dynamic"

export async function GET(request: Request, { params }: RouteContext) {
  if (!isUuid(params.setId)) {
    return Response.json(
      { error: { code: "INVALID_DOCUMENT_SET_ID", message: "The document set identifier is invalid." } },
      { status: 400 },
    )
  }

  try {
    const client = await createAuthenticatedDocumentsClient(request)
    const { data: documentSet } = await client.getDocumentSet({
      path: { id: params.setId },
      throwOnError: true,
    })
    const documentIds = (documentSet.documents ?? [])
      .flatMap((document) => document.id ? [document.id] : [])
    const documents = await Promise.all(
      documentIds.map(async (documentId) => {
        const { data } = await client.getDocument({
          path: { setId: params.setId, docId: documentId },
          throwOnError: true,
        })
        return data
      }),
    )

    return Response.json(
      { documentSet, documents },
      { headers: { "Cache-Control": "private, no-store" } },
    )
  } catch (error) {
    return documentsErrorResponse(error)
  }
}

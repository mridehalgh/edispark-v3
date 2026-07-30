import {
  createAuthenticatedDocumentsClient,
  documentsErrorResponse,
  isUuid,
} from "@/lib/documents-server"

interface RouteContext {
  params: {
    setId: string
    documentId: string
    versionNumber: string
  }
}

export const dynamic = "force-dynamic"

export async function GET(request: Request, { params }: RouteContext) {
  const versionNumber = Number(params.versionNumber)
  if (
    !isUuid(params.setId)
    || !isUuid(params.documentId)
    || !Number.isInteger(versionNumber)
    || versionNumber < 1
  ) {
    return Response.json(
      { error: { code: "INVALID_DOCUMENT_VERSION", message: "The document version identifier is invalid." } },
      { status: 400 },
    )
  }

  try {
    const client = await createAuthenticatedDocumentsClient(request)
    const { data } = await client.getDocumentVersionContent({
      path: {
        setId: params.setId,
        docId: params.documentId,
        versionNumber,
      },
      parseAs: "blob",
      throwOnError: true,
    })

    return new Response(data, {
      headers: {
        "Cache-Control": "private, no-store",
        "Content-Disposition": "inline",
        "Content-Type": data.type || "application/octet-stream",
      },
    })
  } catch (error) {
    return documentsErrorResponse(error)
  }
}

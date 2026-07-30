import {
  createAuthenticatedDocumentsClient,
  documentsErrorResponse,
  isUuid,
} from "@/lib/documents-server"

interface RouteContext {
  params: {
    setId: string
    documentId: string
    derivativeId: string
  }
}

export const dynamic = "force-dynamic"

export async function GET(request: Request, { params }: RouteContext) {
  if (
    !isUuid(params.setId)
    || !isUuid(params.documentId)
    || !isUuid(params.derivativeId)
  ) {
    return Response.json(
      { error: { code: "INVALID_DERIVATIVE", message: "The derivative identifier is invalid." } },
      { status: 400 },
    )
  }

  try {
    const client = await createAuthenticatedDocumentsClient(request)
    const { data } = await client.getDerivativeContent({
      path: {
        setId: params.setId,
        docId: params.documentId,
        derivativeId: params.derivativeId,
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

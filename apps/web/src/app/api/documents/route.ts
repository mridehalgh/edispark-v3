import {
  createAuthenticatedDocumentsClient,
  documentsErrorResponse,
} from "@/lib/documents-server"

const DEFAULT_PAGE_SIZE = 20
const MAX_PAGE_SIZE = 100

export const dynamic = "force-dynamic"

export async function GET(request: Request) {
  try {
    const url = new URL(request.url)
    const requestedLimit = url.searchParams.get("limit")
    const limit = requestedLimit === null ? DEFAULT_PAGE_SIZE : Number(requestedLimit)

    if (!Number.isInteger(limit) || limit < 1 || limit > MAX_PAGE_SIZE) {
      return Response.json(
        { error: { code: "INVALID_PAGE_SIZE", message: "Page size must be between 1 and 100." } },
        { status: 400 },
      )
    }

    const nextToken = url.searchParams.get("nextToken") || undefined
    const client = await createAuthenticatedDocumentsClient(request)
    const { data } = await client.listDocumentSets({
      query: { limit, nextToken },
      throwOnError: true,
    })

    return Response.json(data, {
      headers: { "Cache-Control": "private, no-store" },
    })
  } catch (error) {
    return documentsErrorResponse(error)
  }
}

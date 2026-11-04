import {
  createAuthenticatedDocumentsClient,
  documentsErrorResponse,
} from "@/lib/documents-server"
import { auth } from "@/lib/auth"

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

const AUTHORABLE_TYPES = new Set([
  "ORDER",
  "ORDER_RESPONSE",
  "DESPATCH_ADVICE",
  "INVOICE",
  "REMITTANCE_ADVICE",
  "RECEIPT_ADVICE",
])

const SCHEMA_ROOTS: Record<string, string> = {
  ORDER: "Order",
  ORDER_RESPONSE: "OrderResponse",
  DESPATCH_ADVICE: "DespatchAdvice",
  INVOICE: "Invoice",
  REMITTANCE_ADVICE: "RemittanceAdvice",
  RECEIPT_ADVICE: "ReceiptAdvice",
}

interface AuthorDocumentRequest {
  documentType?: string
  content?: unknown
  metadata?: Record<string, string>
  related?: { setId?: string; documentId?: string; relationType?: string }
}

export async function POST(request: Request) {
  try {
    const session = await auth.api.getSession({ headers: request.headers })
    if (!session) {
      return Response.json({ error: { code: "UNAUTHENTICATED", message: "Sign in to create documents." } }, { status: 401 })
    }
    const body = await request.json() as AuthorDocumentRequest
    if (!body.documentType || !AUTHORABLE_TYPES.has(body.documentType) || !body.content) {
      return Response.json({ error: { code: "INVALID_DOCUMENT", message: "Choose a supported document type and complete its required fields." } }, { status: 400 })
    }
    const client = await createAuthenticatedDocumentsClient(request)
    const { data: schemas } = await client.listSchemas({ throwOnError: true })
    const expectedName = `OASIS UBL 2.1 ${SCHEMA_ROOTS[body.documentType]}`
    const schema = schemas.find((item) => item.name === expectedName)
    const schemaVersion = schema?.versions?.find((item) => item.versionIdentifier === "2.1")
    if (!schema?.id || !schemaVersion) {
      return Response.json({ error: { code: "UBL_SCHEMA_UNAVAILABLE", message: `${expectedName} is not provisioned for this workspace.` } }, { status: 409 })
    }

    const content = Buffer.from(JSON.stringify(body.content), "utf8").toString("base64")
    const createdBy = session.user.id
    if (body.related?.setId && body.related.documentId) {
      const { data } = await client.addDocument({
        path: { setId: body.related.setId },
        body: {
          documentType: body.documentType as never,
          schemaId: schema.id,
          schemaVersion: "2.1",
          content,
          createdBy,
          relatedDocumentId: body.related.documentId,
          relationType: (body.related.relationType ?? "RELATED_TO") as never,
          format: "JSON",
          lifecycleStatus: "DRAFT",
        },
        throwOnError: true,
      })
      return Response.json({ setId: body.related.setId, document: data }, { status: 201 })
    }

    const { data } = await client.createDocumentSet({
      body: {
        documentType: body.documentType as never,
        schemaId: schema.id,
        schemaVersion: "2.1",
        content,
        createdBy,
        metadata: body.metadata,
        format: "JSON",
        lifecycleStatus: "DRAFT",
      },
      throwOnError: true,
    })
    return Response.json({ setId: data.id, document: data.documents?.[0] }, { status: 201 })
  } catch (error) {
    return documentsErrorResponse(error)
  }
}

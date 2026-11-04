import type { DocumentResponse } from "@edispark/documents-client"
import type { TradingAgreement } from "@edispark/integration-client"

import {
  createAuthenticatedDocumentsClient,
  documentsErrorResponse,
  isUuid,
} from "@/lib/documents-server"
import { createAuthenticatedIntegrationClient } from "@/lib/integration-server"
import { resolveSmartCopyTradingPartner } from "@/lib/smart-copy-partner"
import {
  buildSmartCopyDraft,
  isCanonicalSmartCopyContent,
  smartCopyTransitions,
  type SmartCopySourceInput,
} from "@/lib/ubl-smart-copy"
import type { AuthorableDocumentType } from "@/lib/ubl-authoring"

interface RouteContext {
  params: { setId: string }
}

const MAX_CHAIN_DOCUMENTS = 12
const supportedTypes = new Set<AuthorableDocumentType>(Object.keys(smartCopyTransitions) as AuthorableDocumentType[])

export const dynamic = "force-dynamic"

function isAuthorableType(value: string | null | undefined): value is AuthorableDocumentType {
  return Boolean(value && supportedTypes.has(value as AuthorableDocumentType))
}

async function loadCanonicalJson(
  client: Awaited<ReturnType<typeof createAuthenticatedDocumentsClient>>,
  setId: string,
  document: DocumentResponse,
) {
  const candidates: Array<() => Promise<Blob>> = []
  if (document.id && document.currentVersion?.format === "JSON" && document.currentVersion.versionNumber) {
    candidates.push(async () => {
      const { data } = await client.getDocumentVersionContent({
        path: { setId, docId: document.id!, versionNumber: document.currentVersion!.versionNumber! },
        parseAs: "blob",
        throwOnError: true,
      })
      return data
    })
  }
  ;[...(document.derivatives ?? [])]
    .filter((derivative) => derivative.targetFormat === "JSON" && derivative.id && document.id)
    .sort((left, right) => (right.createdAt ?? "").localeCompare(left.createdAt ?? ""))
    .forEach((derivative) => candidates.push(async () => {
      const { data } = await client.getDerivativeContent({
        path: { setId, docId: document.id!, derivativeId: derivative.id! },
        parseAs: "blob",
        throwOnError: true,
      })
      return data
    }))
  for (const candidate of candidates) {
    try {
      return JSON.parse(await (await candidate()).text()) as unknown
    } catch {
      // Try the next retained canonical representation.
    }
  }
  throw new Error(`No readable canonical UBL JSON is available for document ${document.id}.`)
}

export async function GET(request: Request, { params }: RouteContext) {
  const url = new URL(request.url)
  const sourceDocumentId = url.searchParams.get("sourceDocumentId")
  const targetType = url.searchParams.get("targetType")
  if (!isUuid(params.setId) || !sourceDocumentId || !isUuid(sourceDocumentId) || !isAuthorableType(targetType)) {
    return Response.json(
      { error: { code: "INVALID_SMART_COPY_REQUEST", message: "The smart-copy source or target is invalid." } },
      { status: 400 },
    )
  }

  try {
    const client = await createAuthenticatedDocumentsClient(request)
    const { data: documentSet } = await client.getDocumentSet({ path: { id: params.setId }, throwOnError: true })
    const documents = await Promise.all((documentSet.documents ?? []).flatMap((summary) => summary.id ? [
      client.getDocument({ path: { setId: params.setId, docId: summary.id }, throwOnError: true }).then(({ data }) => data),
    ] : []))
    const byId = new Map(documents.flatMap((document) => document.id ? [[document.id, document] as const] : []))
    const primary = byId.get(sourceDocumentId)
    if (!primary || !isAuthorableType(primary.type)) {
      return Response.json(
        { error: { code: "SMART_COPY_SOURCE_UNAVAILABLE", message: "The source document is not available in this document chain." } },
        { status: 404 },
      )
    }
    if (!smartCopyTransitions[primary.type].includes(targetType)) {
      return Response.json(
        { error: { code: "UNSUPPORTED_DOCUMENT_TRANSITION", message: `${targetType} cannot be created from ${primary.type}.` } },
        { status: 422 },
      )
    }

    const chain: DocumentResponse[] = []
    const queue = [primary]
    const visited = new Set<string>()
    while (queue.length > 0 && chain.length < MAX_CHAIN_DOCUMENTS) {
      const document = queue.shift()!
      if (!document.id || visited.has(document.id)) continue
      visited.add(document.id)
      if (isAuthorableType(document.type)) chain.push(document)
      for (const relation of document.relations ?? []) {
        if (!relation.targetDocumentId) continue
        const ancestor = byId.get(relation.targetDocumentId)
        if (ancestor && !visited.has(relation.targetDocumentId)) queue.push(ancestor)
      }
    }

    const inputs: SmartCopySourceInput[] = []
    let unavailableAncestors = 0
    for (const [index, document] of chain.entries()) {
      try {
        const type = document.type as AuthorableDocumentType
        const content = await loadCanonicalJson(client, params.setId, document)
        if (!isCanonicalSmartCopyContent(type, content)) throw new Error("The retained JSON has a different UBL document type.")
        inputs.push({ documentId: document.id!, type, content })
      } catch (error) {
        if (index === 0) throw error
        unavailableAncestors += 1
      }
    }
    let agreements: TradingAgreement[] = []
    let partnerResolutionUnavailable = false
    if (!documentSet.metadata?.partnerId && documentSet.metadata?.mftPartnerId) {
      try {
        const integration = await createAuthenticatedIntegrationClient(request, "member")
        agreements = (await integration.listTradingAgreements({ throwOnError: true })).data
      } catch {
        partnerResolutionUnavailable = true
      }
    }
    const retainedPartnerId = resolveSmartCopyTradingPartner(documentSet.metadata, agreements)
    const draft = buildSmartCopyDraft(targetType, inputs, {
      partnerId: retainedPartnerId && isUuid(retainedPartnerId) ? retainedPartnerId : undefined,
    })
    if (partnerResolutionUnavailable || (documentSet.metadata?.mftPartnerId && !retainedPartnerId)) {
      draft.warnings.push("The trading partner could not be resolved from the retained connection; choose it before saving.")
    }
    if (unavailableAncestors > 0) draft.warnings.push(`${unavailableAncestors} earlier chain ${unavailableAncestors === 1 ? "document was" : "documents were"} unavailable, so only retained values were copied.`)
    return Response.json(draft, {
      headers: { "Cache-Control": "private, no-store" },
    })
  } catch (error) {
    return documentsErrorResponse(error)
  }
}

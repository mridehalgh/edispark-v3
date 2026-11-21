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
  params: Promise<{ setId: string }>
}

const MAX_CHAIN_DOCUMENTS = 12
const supportedTypes = new Set<AuthorableDocumentType>(
  Object.keys(smartCopyTransitions) as AuthorableDocumentType[],
)

export const dynamic = "force-dynamic"

function isAuthorableType(
  value: string | null | undefined,
): value is AuthorableDocumentType {
  return Boolean(value && supportedTypes.has(value as AuthorableDocumentType))
}

async function loadCanonicalJson(
  client: Awaited<ReturnType<typeof createAuthenticatedDocumentsClient>>,
  setId: string,
  document: DocumentResponse,
  representationId?: string,
) {
  const candidates: Array<() => Promise<Blob>> = []
  if (
    document.id &&
    document.currentVersion?.format === "JSON" &&
    document.currentVersion.versionNumber &&
    (!representationId || document.currentVersion.id === representationId)
  ) {
    candidates.push(async () => {
      const { data } = await client.getDocumentVersionContent({
        path: {
          setId,
          docId: document.id!,
          versionNumber: document.currentVersion!.versionNumber!,
        },
        parseAs: "blob",
        throwOnError: true,
      })
      return data
    })
  }
  ;[...(document.derivatives ?? [])]
    .filter(
      (derivative) =>
        derivative.targetFormat === "JSON" && derivative.id && document.id &&
        (!representationId || derivative.id === representationId),
    )
    .sort((left, right) =>
      (right.createdAt ?? "").localeCompare(left.createdAt ?? ""),
    )
    .forEach((derivative) =>
      candidates.push(async () => {
        const { data } = await client.getDerivativeContent({
          path: { setId, docId: document.id!, derivativeId: derivative.id! },
          parseAs: "blob",
          throwOnError: true,
        })
        return data
      }),
    )
  for (const candidate of candidates) {
    try {
      return JSON.parse(await (await candidate()).text()) as unknown
    } catch {
      // Try the next retained canonical representation.
    }
  }
  throw new Error(
    `No readable canonical UBL JSON is available for document ${document.id}.`,
  )
}

export async function GET(request: Request, { params: pendingParams }: RouteContext) {
  const params = await pendingParams
  const url = new URL(request.url)
  const sourceDocumentId = url.searchParams.get("sourceDocumentId")
  const targetType = url.searchParams.get("targetType")
  const representationId = url.searchParams.get("representationId")
  const contentHash = url.searchParams.get("contentHash")
  if (
    !isUuid(params.setId) ||
    !sourceDocumentId ||
    !isUuid(sourceDocumentId) ||
    !isAuthorableType(targetType) ||
    (representationId !== null || contentHash !== null) &&
      (!representationId || !isUuid(representationId) || !contentHash || !/^sha-256:[0-9a-f]{64}$/i.test(contentHash))
  ) {
    return Response.json(
      {
        error: {
          code: "INVALID_SMART_COPY_REQUEST",
          message: "The smart-copy source or target is invalid.",
        },
      },
      { status: 400 },
    )
  }

  try {
    const client = await createAuthenticatedDocumentsClient(request)
    const { data: documentSet } = await client.getDocumentSet({
      path: { id: params.setId },
      throwOnError: true,
    })
    const documents = await Promise.all(
      (documentSet.documents ?? []).flatMap((summary) =>
        summary.id
          ? [
              client
                .getDocument({
                  path: { setId: params.setId, docId: summary.id },
                  throwOnError: true,
                })
                .then(({ data }) => data),
            ]
          : [],
      ),
    )
    const byId = new Map(
      documents.flatMap((document) =>
        document.id ? [[document.id, document] as const] : [],
      ),
    )
    const primary = byId.get(sourceDocumentId)
    if (!primary || !isAuthorableType(primary.type)) {
      return Response.json(
        {
          error: {
            code: "SMART_COPY_SOURCE_UNAVAILABLE",
            message:
              "The source document is not available in this document chain.",
          },
        },
        { status: 404 },
      )
    }
    const correction = url.searchParams.get("mode") === "correct" && primary.type === targetType
    if (representationId && !matchesCurrentRepresentation(primary, representationId, contentHash!)) {
      return Response.json({ error: {
        code: "STALE_SOURCE", message: "The source document has changed. Reopen Create related from the current document.",
      } }, { status: 409 })
    }
    if (!correction && !smartCopyTransitions[primary.type].includes(targetType)) {
      return Response.json(
        {
          error: {
            code: "UNSUPPORTED_DOCUMENT_TRANSITION",
            message: `${targetType} cannot be created from ${primary.type}.`,
          },
        },
        { status: 422 },
      )
    }

    const chain: DocumentResponse[] = []
    const setByDocument = new Map(
      documents.flatMap((document) =>
        document.id ? [[document.id, params.setId] as const] : [],
      ),
    )
    const queue = [primary]
    const visited = new Set<string>()
    let unavailableAncestors = 0
    while (queue.length > 0 && chain.length < MAX_CHAIN_DOCUMENTS) {
      const document = queue.shift()!
      if (!document.id || visited.has(document.id)) continue
      visited.add(document.id)
      if (isAuthorableType(document.type)) chain.push(document)
      for (const relation of document.relations ?? []) {
        if (!relation.targetDocumentId) continue
        let ancestor = byId.get(relation.targetDocumentId)
        if (
          !ancestor &&
          relation.targetDocumentSetId &&
          byId.size < MAX_CHAIN_DOCUMENTS
        ) {
          try {
            ancestor = (
              await client.getDocument({
                path: {
                  setId: relation.targetDocumentSetId,
                  docId: relation.targetDocumentId,
                },
                throwOnError: true,
              })
            ).data
            byId.set(relation.targetDocumentId, ancestor)
            setByDocument.set(
              relation.targetDocumentId,
              relation.targetDocumentSetId,
            )
          } catch {
            unavailableAncestors += 1
          }
        }
        if (ancestor && !visited.has(relation.targetDocumentId))
          queue.push(ancestor)
      }
    }

    const inputs: SmartCopySourceInput[] = []
    for (const [index, document] of chain.entries()) {
      try {
        const type = document.type as AuthorableDocumentType
        const content = await loadCanonicalJson(
          client,
          setByDocument.get(document.id!) ?? params.setId,
          document,
          index === 0 ? representationId ?? undefined : undefined,
        )
        if (!isCanonicalSmartCopyContent(type, content))
          throw new Error(
            "The retained JSON has a different UBL document type.",
          )
        inputs.push({ documentId: document.id!, type, content })
      } catch (error) {
        if (index === 0) throw error
        unavailableAncestors += 1
      }
    }
    let agreements: TradingAgreement[] = []
    let partnerResolutionUnavailable = false
    if (
      !documentSet.metadata?.partnerId &&
      documentSet.metadata?.mftPartnerId
    ) {
      try {
        const integration = await createAuthenticatedIntegrationClient(
          request,
          "member",
        )
        agreements = (
          await integration.listTradingAgreements({ throwOnError: true })
        ).data
      } catch {
        partnerResolutionUnavailable = true
      }
    }
    const retainedPartnerId = resolveSmartCopyTradingPartner(
      documentSet.metadata,
      agreements,
    )
    const draft = buildSmartCopyDraft(targetType, inputs, {
      correction,
      partnerId:
        retainedPartnerId && isUuid(retainedPartnerId)
          ? retainedPartnerId
          : undefined,
    })
    if (
      partnerResolutionUnavailable ||
      (documentSet.metadata?.mftPartnerId && !retainedPartnerId)
    ) {
      draft.warnings.push(
        "The trading partner could not be resolved from the retained connection; choose it before saving.",
      )
    }
    if (unavailableAncestors > 0)
      draft.warnings.push(
        `${unavailableAncestors} earlier chain ${unavailableAncestors === 1 ? "document was" : "documents were"} unavailable, so only retained values were copied.`,
      )
    return Response.json(draft, {
      headers: { "Cache-Control": "private, no-store" },
    })
  } catch (error) {
    return documentsErrorResponse(error)
  }
}

function matchesCurrentRepresentation(document: DocumentResponse, id: string, hash: string) {
  const current = document.currentVersion
  if (current?.format === "JSON") return current.id === id && current.contentHash === hash
  return Boolean(current?.id && document.derivatives?.some(derivative =>
    derivative.id === id && derivative.contentHash === hash && derivative.targetFormat === "JSON" &&
    derivative.sourceVersionId === current.id && ["canonical", "default"].includes(derivative.variantKey ?? ""),
  ))
}

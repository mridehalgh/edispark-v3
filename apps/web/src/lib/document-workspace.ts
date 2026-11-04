import type {
  DocumentResponse,
  DocumentSetResponse,
} from "@edispark/documents-client"
import type { ExchangeResponse } from "@edispark/integration-client"
import { attentionReason } from "./attention-reason"

export const documentLabels: Record<string, string> = {
  ORDER: "Order",
  ORDER_RESPONSE: "Order response",
  DESPATCH_ADVICE: "Advance shipping notice",
  INVOICE: "Invoice",
  CREDIT_NOTE: "Credit note",
  REMITTANCE_ADVICE: "Remittance advice",
  RECEIPT_ADVICE: "Receipt advice",
}

export type WorkspaceSet = DocumentSetResponse & {
  workspaceDocuments?: DocumentResponse[]
}
export interface WorkspaceDocument {
  key: string
  setId: string
  id: string
  type: string
  reference: string
  partner: string
  direction: string
  createdAt?: string
  updatedAt?: string
  status: string
  reason?: string
  relations: NonNullable<DocumentResponse["relations"]>
  exchange?: ExchangeResponse
}

export function needsAttention(document: WorkspaceDocument) {
  return document.status === "FAILED" || document.status === "NEEDS_REVIEW"
}

export function workspaceDocuments(
  sets: WorkspaceSet[],
  exchanges: ExchangeResponse[],
  partners: Map<string, string>,
) {
  return sets
    .flatMap((set): WorkspaceDocument[] => {
      if (!set.id) return []
      const metadata = set.metadata ?? {}
      // The API's documents map has no ordering guarantee. Identify the source
      // from relation-free metadata, rather than assigning its reference to index 0.
      const summaries = set.workspaceDocuments ?? set.documents ?? []
      const sourceId = [...summaries]
        .filter(
          (document) =>
            !(document as DocumentResponse).relations?.some(
              (relation) =>
                !relation.targetDocumentSetId ||
                relation.targetDocumentSetId === set.id,
            ),
        )
        .sort((a, b) =>
          (
            (a as DocumentResponse).currentVersion?.createdAt ?? ""
          ).localeCompare(
            (b as DocumentResponse).currentVersion?.createdAt ?? "",
          ),
        )[0]?.id
      return (set.workspaceDocuments ?? set.documents ?? []).flatMap(
        (summary) => {
          if (!summary.id || !summary.type || !documentLabels[summary.type])
            return []
          const detail = summary as DocumentResponse
          const exchange = exchanges
            .filter(
              (item) =>
                item.sourceDocument?.documentSetId === set.id &&
                item.sourceDocument?.documentId === summary.id,
            )
            .sort((a, b) =>
              (b.updatedAt ?? "").localeCompare(a.updatedAt ?? ""),
            )[0]
          // Set metadata describes the source, not every follow-up added to that set.
          const source = sourceId === summary.id
          const reference = source
            ? (metadata.businessDocumentNumber ?? metadata.name)
            : undefined
          return [
            {
              key: `${set.id}:${summary.id}`,
              setId: set.id!,
              id: summary.id,
              type: summary.type,
              reference:
                reference ||
                `${documentLabels[summary.type]} · ${summary.id.slice(0, 8)}`,
              partner:
                partners.get(exchange?.partnerId ?? metadata.partnerId) ??
                metadata.partnerName ??
                "Partner not recorded",
              direction:
                exchange?.direction ??
                (source ? metadata.direction : undefined) ??
                "Not recorded",
              createdAt: detail.currentVersion?.createdAt ?? set.createdAt,
              updatedAt:
                exchange?.updatedAt ??
                detail.currentVersion?.createdAt ??
                set.createdAt,
              status:
                summary.lifecycleStatus === "DRAFT"
                  ? "DRAFT"
                  : summary.lifecycleStatus === "CANCELLED"
                    ? "CANCELLED"
                    : (exchange?.status ?? "STORED"),
              reason: attentionReason(exchange ?? {}),
              relations: detail.relations ?? [],
              exchange,
            },
          ]
        },
      )
    })
    .sort((a, b) => (b.updatedAt ?? "").localeCompare(a.updatedAt ?? ""))
}

export interface DocumentGroup {
  key: string
  documents: WorkspaceDocument[]
  root: WorkspaceDocument
}

export function relationTargetKey(
  document: WorkspaceDocument,
  relation: WorkspaceDocument["relations"][number],
) {
  return `${relation.targetDocumentSetId ?? document.setId}:${relation.targetDocumentId}`
}

export function groupDocuments(
  documents: WorkspaceDocument[],
): DocumentGroup[] {
  const byKey = new Map(documents.map((document) => [document.key, document]))
  const adjacent = new Map(
    documents.map((document) => [document.key, new Set<string>()]),
  )
  for (const document of documents) {
    for (const relation of document.relations) {
      const target = relationTargetKey(document, relation)
      if (!byKey.has(target) || target === document.key) continue
      adjacent.get(document.key)!.add(target)
      adjacent.get(target)!.add(document.key)
    }
  }
  const visited = new Set<string>()
  return documents.flatMap((document) => {
    if (visited.has(document.key)) return []
    const members: WorkspaceDocument[] = []
    const queue = [document.key]
    while (queue.length) {
      const key = queue.pop()!
      if (visited.has(key)) continue
      visited.add(key)
      members.push(byKey.get(key)!)
      queue.push(...adjacent.get(key)!)
    }
    const root =
      members.find((member) => member.type === "ORDER") ??
      members.find(
        (member) =>
          !member.relations.some((relation) =>
            members.some(
              (target) => target.key === relationTargetKey(member, relation),
            ),
          ),
      ) ??
      members[0]
    return [{ key: root.key, documents: members, root }]
  })
}

export function lineageRows(group: DocumentGroup) {
  const stage: Record<string, number> = {
    ORDER: 0,
    ORDER_RESPONSE: 1,
    DESPATCH_ADVICE: 2,
    RECEIPT_ADVICE: 3,
    INVOICE: 4,
    REMITTANCE_ADVICE: 5,
    CREDIT_NOTE: 6,
  }
  const visited = new Set<string>()
  const rows: { document: WorkspaceDocument; depth: number }[] = []
  function visit(document: WorkspaceDocument, depth: number) {
    if (visited.has(document.key)) return
    visited.add(document.key)
    rows.push({ document, depth })
    group.documents
      .filter((child) =>
        child.relations.some(
          (relation) => relationTargetKey(child, relation) === document.key,
        ),
      )
      .sort(
        (left, right) =>
          (stage[left.type] ?? 99) - (stage[right.type] ?? 99) ||
          (left.createdAt ?? "").localeCompare(right.createdAt ?? "") ||
          left.reference.localeCompare(right.reference),
      )
      .forEach((child) => visit(child, Math.min(depth + 1, 5)))
  }
  visit(group.root, 0)
  group.documents.forEach((document) => visit(document, 0))
  return rows
}

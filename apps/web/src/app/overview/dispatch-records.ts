import type { ExchangeResponse, TradingPartner } from "@edispark/integration-client"
import type { PartnerResponse, TransferResponse } from "@edispark/mft-client"

import { attentionReason } from "../../lib/attention-reason"
import { documentLabels, needsAttention, workspaceDocuments, type WorkspaceSet } from "../../lib/document-workspace"
import { exchangeStageLabels, timestamp } from "../../lib/exchange-timeline"
import { transferStatusLabel } from "../../lib/transfer-status"

export interface DispatchRecord {
  href: string
  reference: string
  type: string
  partner: string
  direction?: "INBOUND" | "OUTBOUND"
  updatedAt?: string
  status: string
  tone: "success" | "error" | "neutral"
  reason?: string
  needsAttention: boolean
  scheduledRetry?: boolean
  fileTransfer?: boolean
}

function knownDirection(value?: string) {
  return value === "INBOUND" || value === "OUTBOUND" ? value : undefined
}

export function dispatchRecords({
  sets, exchanges, partners, transfers, connections, admin,
}: {
  sets: WorkspaceSet[]
  exchanges: ExchangeResponse[]
  partners: TradingPartner[]
  transfers: TransferResponse[]
  connections: PartnerResponse[]
  admin: boolean
}) {
  const partnerNames = new Map(partners.map((partner) => [partner.id ?? "", partner.displayName ?? "Partner name unavailable"]))
  const connectionNames = new Map(connections.map((connection) => [connection.id, connection.name]))
  const representedTransfers = new Map<string, boolean>()
  const sourceTransfers = new Map(sets.map((set) => [set.id, set.metadata?.mftTransferId]))
  const workspace = workspaceDocuments(sets, exchanges, partnerNames)
  const byDocumentKey = new Map(workspace.map((document) => [document.key, document]))
  const documents = workspace.flatMap((document): DispatchRecord[] => {
    const fileOnly = document.type === "CREDIT_NOTE"
    if (fileOnly && !admin) return []

    const needsReview = needsAttention(document)
    const delivered = document.status === "DELIVERED"
    const transferId = document.exchange?.mftTransferId ?? sourceTransfers.get(document.setId)
    if (transferId) representedTransfers.set(transferId, needsReview || representedTransfers.get(transferId) === true)
    const status = delivered
      ? (document.direction === "INBOUND" ? "Received and stored" : "Transport delivered")
      : document.status === "DRAFT" ? "Draft"
      : document.status === "CANCELLED" ? "Cancelled"
      : document.status === "STORED" ? "Document stored"
      : document.status === "AWAITING_RECEIPT" && document.exchange?.stages?.some(
        (stage) => stage.detail?.startsWith("AS2 transport receipt verified; awaiting functional acknowledgement."),
      ) ? "Awaiting functional acknowledgement"
      : exchangeStageLabels[document.status] ?? "Status unavailable"
    return [{
      href: fileOnly ? `/files/${document.setId}` : `/documents/${document.setId}/${document.id}`,
      reference: document.reference,
      type: documentLabels[document.type],
      partner: document.partner,
      direction: knownDirection(document.direction),
      updatedAt: document.updatedAt,
      status,
      tone: needsReview ? "error" : delivered ? "success" : "neutral",
      reason: document.reason,
      needsAttention: needsReview,
    }]
  })

  const reviewByTransfer = new Map<string, ExchangeResponse[]>()
  for (const exchange of exchanges) {
    if (exchange.sourceDocument || !exchange.mftTransferId || !attentionReason(exchange)) continue
    const reviews = reviewByTransfer.get(exchange.mftTransferId) ?? []
    reviews.push(exchange)
    reviewByTransfer.set(exchange.mftTransferId, reviews)
  }

  const files = transfers.flatMap((transfer): DispatchRecord[] => {
    if (!admin || !transfer.id) return []
    const inboundReviews = reviewByTransfer.get(transfer.id) ?? []
    const transportIssue = transfer.status === "FAILED" || transfer.status === "NEEDS_REVIEW"
    const needsReview = transportIssue || inboundReviews.length > 0
    const scheduledRetry = transfer.status === "RETRY_SCHEDULED"
    const representedBy = representedTransfers.get(transfer.id)
    if (representedBy !== undefined && !scheduledRetry && !inboundReviews.length && (!needsReview || representedBy)) return []
    const source = transfer.provenance
    const document = byDocumentKey.get(`${source?.documentSetId}:${source?.documentId}`)
    const reason = transportIssue || scheduledRetry ? attentionReason(transfer)
      : inboundReviews.length > 1 ? `${inboundReviews.length} messages in this file need review.`
      : attentionReason(inboundReviews[0] ?? {})?.replace(/^MESSAGE_REJECTED:\s*/, "")
    const delivered = transfer.status === "DELIVERED" || transfer.status === "RECEIVED"
    const status = inboundReviews.length ? "Processing needs review"
      : transfer.status === "DELIVERED"
      ? "Transport delivered"
      : transfer.status === "AWAITING_MDN" ? "Awaiting AS2 receipt"
      : transferStatusLabel(transfer.status)
    return [{
      href: `/files/transfers/${transfer.id}`,
      reference: document?.reference ?? transfer.filename ?? `File · ${transfer.id.slice(0, 8)}`,
      type: document ? `${documentLabels[document.type]} transfer` : "File transfer",
      partner: connectionNames.get(transfer.partnerId) ?? "Connection not recorded",
      direction: transfer.direction,
      updatedAt: transfer.updatedAt ?? transfer.createdAt,
      status,
      tone: needsReview ? "error" : delivered ? "success" : "neutral",
      reason: reason === "Partner returned no MDN" ? "The partner did not return an AS2 receipt." : reason,
      needsAttention: needsReview,
      scheduledRetry,
      fileTransfer: true,
    }]
  })

  return [...documents, ...files].sort(
    (left, right) => (timestamp(right.updatedAt) ?? 0) - (timestamp(left.updatedAt) ?? 0),
  )
}

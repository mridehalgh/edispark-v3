import type { MappingRevision } from "@edispark/integration-client"

export type ExchangeDirection = "INBOUND" | "OUTBOUND"
export type ExchangeDocumentType = "ORDER" | "ORDER_RESPONSE" | "DESPATCH_ADVICE" | "INVOICE" | "REMITTANCE_ADVICE" | "RECEIPT_ADVICE"
export type ExchangeStandard = "EDIFACT" | "TRADACOMS"

export type MappingCompatibility = {
  direction?: ExchangeDirection
  documentType?: ExchangeDocumentType
  standard?: ExchangeStandard
  release?: string
  messageType?: string
}

export type CompatibleMapping = MappingRevision & { compatibility?: MappingCompatibility }

export function findPublishedMapping(
  mappings: CompatibleMapping[],
  direction: ExchangeDirection,
  documentType: ExchangeDocumentType,
  standard: ExchangeStandard,
) {
  return mappings.find((mapping) => mapping.status === "PUBLISHED"
    && mapping.compatibility?.direction === direction
    && mapping.compatibility.documentType === documentType
    && mapping.compatibility.standard === standard)
}

export function unavailableExchangeMessage(
  direction: ExchangeDirection,
  documentType: ExchangeDocumentType,
  standard: ExchangeStandard,
) {
  const action = direction === "INBOUND" ? "receive" : "send"
  const document = documentType.toLowerCase().replaceAll("_", " ")
  const release = standard === "EDIFACT" ? "D96A" : "9"
  return `EdiSpark cannot ${action} ${document} as ${standard} ${release} yet. Publish a compatible ${direction.toLowerCase()} mapping, then add this document exchange again.`
}

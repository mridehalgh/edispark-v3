import type { MappingRevision } from "@edispark/integration-client"

const REMITTANCE_EXAMPLE = JSON.stringify({
  document: {
    id: "REM-2026-0042",
    issueDate: "2026-11-15",
    currency: "GBP",
    totalPaymentAmount: 1256.72,
  },
  parties: { sender: "[retailer_identifier]", receiver: "[supplier_identifier]" },
  lines: [{ id: "INV-100045" }],
}, null, 2)

export function bundledMappingInput(mapping: MappingRevision): string | undefined {
  if (mapping.sample?.sourceDocument) return mapping.sample.sourceDocument
  const compatibility = mapping.compatibility
  if (compatibility?.direction === "INBOUND" && compatibility.documentType === "REMITTANCE_ADVICE"
    && compatibility.messageType === "REMADV") {
    return REMITTANCE_EXAMPLE
  }
}

export function mappingTestRequest(
  mapping: MappingRevision,
  input: string,
  referenceTableRevisions: Record<string, string>,
) {
  return {
    name: "Workspace test",
    input,
    expected: Object.keys(referenceTableRevisions).length === 0 && mapping.sample?.sourceDocument === input
      ? mapping.sample?.targetDocument : undefined,
    referenceTableRevisions,
  }
}

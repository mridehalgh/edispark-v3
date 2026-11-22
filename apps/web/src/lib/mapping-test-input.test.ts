import type { MappingRevision } from "@edispark/integration-client"
import { describe, expect, it } from "vitest"

import { bundledMappingInput, mappingTestRequest } from "./mapping-test-input"

const inboundRemittance: MappingRevision = {
  compatibility: { direction: "INBOUND", documentType: "REMITTANCE_ADVICE", messageType: "REMADV", standard: "EDIFACT", release: "D96A" },
}

describe("mapping test inputs", () => {
  it("provides a parsed remittance example for an inbound REMADV rule without a sample", () => {
    const input = bundledMappingInput(inboundRemittance)
    expect(input).toBeDefined()
    expect(JSON.parse(input!)).toMatchObject({
      document: { id: "REM-2026-0042", totalPaymentAmount: 1256.72, currency: "GBP" },
      parties: { sender: "[retailer_identifier]", receiver: "[supplier_identifier]" },
      lines: [{ id: "INV-100045" }],
    })
    expect(bundledMappingInput({
      ...inboundRemittance, compatibility: { ...inboundRemittance.compatibility, direction: "OUTBOUND" },
    })).toBeUndefined()
  })

  it("uses the published expectation only for the matching source fixture", () => {
    const mapping = { ...inboundRemittance, sample: { sourceDocument: '{"source":1}', targetDocument: '{"target":1}' } }
    expect(bundledMappingInput(mapping)).toBe(mapping.sample.sourceDocument)
    expect(mappingTestRequest(mapping, mapping.sample.sourceDocument, {}).expected).toBe(mapping.sample.targetDocument)
    expect(mappingTestRequest(mapping, '{"source":2}', { sites: "pinned-revision" })).toMatchObject({
      input: '{"source":2}', expected: undefined, referenceTableRevisions: { sites: "pinned-revision" },
    })
  })
})

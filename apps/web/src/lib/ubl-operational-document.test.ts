import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

import { parseOperationalDocument, parseOperationalDocumentJson, type OperationalDocumentKind } from "./ubl-operational-document"

const fixtures: Array<[OperationalDocumentKind, string]> = [
  ["ORDER_RESPONSE", "UBL-OrderResponse-2.1-Example.json"],
  ["DESPATCH_ADVICE", "UBL-DespatchAdvice-2.0-Example.json"],
  ["REMITTANCE_ADVICE", "UBL-RemittanceAdvice-2.0-Example.json"],
  ["RECEIPT_ADVICE", "UBL-ReceiptAdvice-2.0-Example.json"],
]

function fixture(name: string) {
  return readFileSync(resolve(process.cwd(), "../../vendor/ubl/2.1/json", name), "utf8")
}

describe("operational UBL document adapter", () => {
  it.each(fixtures)("parses the official %s example", (kind, name) => {
    const parsed = parseOperationalDocumentJson(fixture(name), kind)
    expect(parsed.ok).toBe(true)
    if (!parsed.ok) return
    expect(parsed.document.id).not.toBe("")
    expect(parsed.document.issueDate).not.toBe("")
    expect(parsed.document.lines.length).toBeGreaterThan(0)
    expect(parsed.document.populatedFields.length).toBeGreaterThan(10)
  })

  it("rejects malformed JSON", () => {
    expect(parseOperationalDocumentJson("{", "ORDER_RESPONSE")).toEqual({
      ok: false,
      reason: "The JSON representation could not be read.",
    })
  })

  it("rejects the wrong namespace and document type", () => {
    const parsed = parseOperationalDocument(JSON.parse(fixture(fixtures[0][1])), "DESPATCH_ADVICE")
    expect(parsed.ok).toBe(false)
  })

  it("preserves repeated values and scalar attributes in the populated-field projection", () => {
    const parsed = parseOperationalDocumentJson(fixture("UBL-ReceiptAdvice-2.0-Example.json"), "RECEIPT_ADVICE")
    expect(parsed.ok).toBe(true)
    if (!parsed.ok) return
    expect(parsed.document.populatedFields.some((field) => field.path.includes("unitCode"))).toBe(true)
    expect(parsed.document.populatedFields.filter((field) => field.path.includes("ReceiptLine[")).length).toBeGreaterThan(1)
  })

  it("reports only handling units with an SSCC as printable labels", () => {
    const parsed = parseOperationalDocument({
      _D: "urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2",
      DespatchAdvice: [{
        ID: [{ _: "DES-1" }], IssueDate: [{ _: "2026-11-04" }],
        Shipment: [{ TransportHandlingUnit: [
          { ID: [{ _: "PALLET-1" }], TraceID: [{ _: "123456789012345675" }] },
          { ID: [{ _: "PALLET-2" }] },
        ] }],
      }],
    }, "DESPATCH_ADVICE")

    expect(parsed.ok).toBe(true)
    if (!parsed.ok) return
    expect(parsed.document.handlingUnitCount).toBe(2)
    expect(parsed.document.printableLabelCount).toBe(1)
  })
})


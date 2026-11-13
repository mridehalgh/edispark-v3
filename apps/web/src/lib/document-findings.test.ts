import { describe, expect, it } from "vitest"
import { groupDocumentFindings } from "./document-findings"

describe("document findings", () => {
  it("groups repeated field occurrences while preserving every exact location", () => {
    const findings = Array.from({ length: 8 }, (_, line) => ({ code: "SOURCE_FIELD_UNMAPPED", owner: "MAPPING",
      path: `/OrderResponse/0/OrderLine/${line}/LineItem/0/Quantity/0/unitCode`, message: "Review the unit mapping." }))
    const grouped = groupDocumentFindings(findings)
    expect(grouped).toHaveLength(1)
    expect(grouped[0].lines).toEqual([1, 2, 3, 4, 5, 6, 7, 8])
    expect(grouped[0].locations).toHaveLength(8)
    expect(grouped[0].label).toBe("Quantity unit")
  })

  it("keeps distinct owners, rules, fields and messages separate", () => {
    const base = { code: "REQUIRED", owner: "DOCUMENT", path: "/lines/0/quantity", message: "Required" }
    expect(groupDocumentFindings([base, { ...base, owner: "MAPPING" }, { ...base, code: "INVALID" },
      { ...base, path: "/lines/0/unitCode" }, { ...base, message: "Must be positive" }])).toHaveLength(5)
  })
})

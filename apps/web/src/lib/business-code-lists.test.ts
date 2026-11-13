import { describe, expect, it } from "vitest"

import { COUNTRIES, CURRENCIES, HANDLING_UNIT_TYPES, ORDER_RESPONSE_CODES, PAYMENT_MEANS, QUANTITY_UNITS, TAX_CATEGORIES, WEIGHT_UNITS } from "./business-code-lists"

const lists = { COUNTRIES, CURRENCIES, HANDLING_UNIT_TYPES, ORDER_RESPONSE_CODES, PAYMENT_MEANS, QUANTITY_UNITS, TAX_CATEGORIES, WEIGHT_UNITS }

describe("business code lists", () => {
  it("distinguishes receipt acknowledgement from accepting the order", () => {
    const labels = new Map(ORDER_RESPONSE_CODES.map((option) => [option.value, option.label]))
    expect(labels.get("AB")).toBe("Received, awaiting a decision")
    expect(labels.get("AP")).toBe("Accepted without changes")
    expect(labels.get("CA")).toBe("Accepted with changes")
  })
  it("keeps every selectable code unique and labelled", () => {
    Object.values(lists).forEach((options) => {
      expect(new Set(options.map((option) => option.value)).size).toBe(options.length)
      expect(options.every((option) => option.value && option.label)).toBe(true)
    })
  })

  it("includes the retail and logistics defaults used by authoring", () => {
    expect(QUANTITY_UNITS.map((option) => option.value)).toEqual(expect.arrayContaining(["EA", "C62", "H87", "KGM", "LTR"]))
    expect(WEIGHT_UNITS.map((option) => option.value)).toEqual(expect.arrayContaining(["KGM", "GRM", "TNE", "LBR"]))
    expect(PAYMENT_MEANS.map((option) => option.value)).toEqual(expect.arrayContaining(["20", "30", "42", "49"]))
    expect(TAX_CATEGORIES.map((option) => option.value)).toEqual(expect.arrayContaining(["S", "Z", "E", "AE", "O"]))
  })
})

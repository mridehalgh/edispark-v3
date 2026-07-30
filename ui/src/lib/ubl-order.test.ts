import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

import { parseUblOrder, parseUblOrderJson } from "./ubl-order"

function repositoryFile(path: string) {
  return readFileSync(resolve(process.cwd(), path), "utf8")
}

function minimumOrder() {
  return {
    _D: "urn:oasis:names:specification:ubl:schema:xsd:Order-2",
    _A: "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
    _B: "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
    Order: [{
      ID: [{ _: "PO-[reference]" }],
      IssueDate: [{ _: "2026-07-30" }],
      BuyerCustomerParty: [{ Party: [{}] }],
      SellerSupplierParty: [{ Party: [{}] }],
      OrderLine: [{
        LineItem: [{
          ID: [{ _: "1" }],
          Item: [{}],
        }],
      }],
    }],
  }
}

describe("parseUblOrder", () => {
  it("maps the repository seed without relying on its shape as the contract", () => {
    const result = parseUblOrderJson(repositoryFile(
      "../domains/documents/src/main/resources/seed/documents/ubl-order-po-2026-0042.json",
    ))

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.order.id).toBe("PO-2026-0042")
    expect(result.order.buyer.name).toBe("Cedar Retail Group Ltd")
    expect(result.order.seller.name).toBe("Northstar Foods Ltd")
    expect(result.order.lines).toHaveLength(2)
    expect(result.order.lines[0]).toMatchObject({
      id: "1",
      sellerItemId: "SPARK-WATER-750",
      standardItemId: "5010000000015",
      quantity: { value: 48, unit: "EA" },
      price: { value: 2.25, currency: "GBP" },
    })
    expect(result.order.totals?.payable).toEqual({ value: 558, currency: "GBP" })
  })

  it("maps the official OASIS UBL 2.1 Order example", () => {
    const result = parseUblOrderJson(repositoryFile("../ubl-source/json/UBL-Order-2.1-Example.json"))

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.order.id).toBe("34")
    expect(result.order.issueDate).toBe("2010-01-20")
    expect(result.order.lines).toHaveLength(2)
    expect(result.order.lines[0].quantity).toEqual({ value: 120, unit: "LTR" })
    expect(result.order.totals?.payable).toEqual({ value: 6225, currency: "SEK" })
  })

  it("accepts the minimum required schema structure", () => {
    const result = parseUblOrder(minimumOrder())

    expect(result).toMatchObject({
      ok: true,
      order: {
        id: "PO-[reference]",
        issueDate: "2026-07-30",
        lines: [{ id: "1" }],
      },
    })
  })

  it("rejects an incorrect document namespace", () => {
    const value = minimumOrder()
    value._D = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"

    expect(parseUblOrder(value)).toEqual({
      ok: false,
      reason: "The JSON representation is not a UBL Order.",
    })
  })

  it("rejects missing required order fields", () => {
    const value = minimumOrder()
    value.Order[0].IssueDate = []

    expect(parseUblOrder(value)).toMatchObject({ ok: false })
  })

  it("rejects malformed required line items", () => {
    const value = minimumOrder()
    value.Order[0].OrderLine[0].LineItem[0].Item = []

    expect(parseUblOrder(value)).toMatchObject({
      ok: false,
      reason: "One or more UBL Order lines are missing their required line item fields.",
    })
  })

  it("returns a safe result for invalid JSON", () => {
    expect(parseUblOrderJson("{")).toEqual({
      ok: false,
      reason: "The JSON representation could not be read.",
    })
  })
})

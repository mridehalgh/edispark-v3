import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

import { parseUblInvoice, parseUblInvoiceJson } from "./ubl-invoice"

function repositoryFile(path: string) {
  return readFileSync(resolve(process.cwd(), path), "utf8")
}

function minimumInvoice() {
  return {
    _D: "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
    _A: "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
    _B: "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
    Invoice: [{
      ID: [{ _: "INV-[reference]" }],
      IssueDate: [{ _: "2026-07-30" }],
      AccountingSupplierParty: [{ Party: [{}] }],
      AccountingCustomerParty: [{ Party: [{}] }],
      LegalMonetaryTotal: [{
        PayableAmount: [{ _: 120, currencyID: "GBP" }],
      }],
      InvoiceLine: [{
        ID: [{ _: "1" }],
        LineExtensionAmount: [{ _: 100, currencyID: "GBP" }],
        Item: [{}],
      }],
    }],
  }
}

describe("parseUblInvoice", () => {
  it("maps the repository seed without relying on its shape as the contract", () => {
    const result = parseUblInvoiceJson(repositoryFile(
      "../domains/documents/src/main/resources/seed/documents/ubl-invoice-100045.json",
    ))

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.invoice).toMatchObject({
      id: "INV-100045",
      issueDate: "2026-07-25",
      dueDate: "2026-08-24",
      orderReference: "PO-2026-0042",
      currency: "GBP",
    })
    expect(result.invoice.supplier.name).toBe("Northstar Foods Ltd")
    expect(result.invoice.customer.name).toBe("Cedar Retail Group Ltd")
    expect(result.invoice.lines).toHaveLength(2)
    expect(result.invoice.lines[0]).toMatchObject({
      id: "1",
      name: "Spark Water 750",
      sellerItemId: "SPARK-WATER-750",
      quantity: { value: 48, unit: "EA" },
      price: { value: 2.25, currency: "GBP" },
      lineExtension: { value: 108, currency: "GBP" },
    })
    expect(result.invoice.taxes[0].taxAmount).toEqual({ value: 111.6, currency: "GBP" })
    expect(result.invoice.totals.payable).toEqual({ value: 669.6, currency: "GBP" })
  })

  it("maps the official OASIS UBL 2.1 Invoice example", () => {
    const result = parseUblInvoiceJson(repositoryFile("../ubl-source/json/UBL-Invoice-2.1-Example.json"))

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.invoice.id).toBe("TOSL108")
    expect(result.invoice.issueDate).toBe("2009-12-15")
    expect(result.invoice.currency).toBe("EUR")
    expect(result.invoice.supplier.name).toBe("Salescompany ltd.")
    expect(result.invoice.customer.name).toBe("Buyercompany ltd")
    expect(result.invoice.lines).toHaveLength(5)
    expect(result.invoice.totals.payable).toEqual({ value: 729, currency: "EUR" })
  })

  it("accepts the minimum required schema structure", () => {
    expect(parseUblInvoice(minimumInvoice())).toMatchObject({
      ok: true,
      invoice: {
        id: "INV-[reference]",
        issueDate: "2026-07-30",
        lines: [{ id: "1", lineExtension: { value: 100, currency: "GBP" } }],
        totals: { payable: { value: 120, currency: "GBP" } },
      },
    })
  })

  it("rejects an incorrect document namespace", () => {
    const value = minimumInvoice()
    value._D = "urn:oasis:names:specification:ubl:schema:xsd:Order-2"

    expect(parseUblInvoice(value)).toEqual({
      ok: false,
      reason: "The JSON representation is not a UBL Invoice.",
    })
  })

  it("rejects missing required invoice fields", () => {
    const value = minimumInvoice()
    value.Invoice[0].AccountingCustomerParty = []

    expect(parseUblInvoice(value)).toMatchObject({ ok: false })
  })

  it("rejects a missing required payable amount", () => {
    const value = minimumInvoice()
    value.Invoice[0].LegalMonetaryTotal[0].PayableAmount = []

    expect(parseUblInvoice(value)).toMatchObject({
      ok: false,
      reason: "The UBL Invoice is missing required invoice fields.",
    })
  })

  it("rejects malformed required invoice lines", () => {
    const value = minimumInvoice()
    value.Invoice[0].InvoiceLine[0].LineExtensionAmount = []

    expect(parseUblInvoice(value)).toMatchObject({
      ok: false,
      reason: "One or more UBL Invoice lines are missing required fields.",
    })
  })

  it("returns a safe result for invalid JSON", () => {
    expect(parseUblInvoiceJson("{")).toEqual({
      ok: false,
      reason: "The JSON representation could not be read.",
    })
  })
})

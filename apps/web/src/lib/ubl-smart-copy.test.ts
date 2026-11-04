import { describe, expect, it } from "vitest"

import { buildSmartCopyDraft, smartCopyTransitions } from "./ubl-smart-copy"

const v = (value: string | number) => [{ _: value }]
const party = (name: string, endpoint: string) => [{ Party: [{ EndpointID: [{ _: endpoint, schemeID: "GLN" }], PartyName: [{ Name: v(name) }] }] }]

function order() {
  return { _D: "urn:oasis:names:specification:ubl:schema:xsd:Order-2", Order: [{ ID: v("PO-1"), IssueDate: v("2026-11-01"), DocumentCurrencyCode: v("GBP"), BuyerCustomerParty: party("Buyer", "BUYER"), SellerSupplierParty: party("Seller", "SELLER"), OrderLine: [{ LineItem: [{ ID: v("10"), Quantity: [{ _: 8, unitCode: "EA" }], Price: [{ PriceAmount: [{ _: 4.25, currencyID: "GBP" }] }], Item: [{ Name: v("Widget"), SellersItemIdentification: [{ ID: v("SKU-1") }] }] }] }] }] }
}

function response() {
  return { _D: "urn:oasis:names:specification:ubl:schema:xsd:OrderResponse-2", OrderResponse: [{ ID: v("OR-1"), IssueDate: v("2026-11-02"), DocumentCurrencyCode: v("GBP"), OrderReference: [{ ID: v("PO-1") }], SellerSupplierParty: party("Seller", "SELLER"), BuyerCustomerParty: party("Buyer", "BUYER"), OrderLine: [{ LineItem: [{ ID: v("10"), Quantity: [{ _: 6, unitCode: "EA" }], Item: [{ Name: v("Widget") }] }] }] }] }
}

function despatch() {
  return { _D: "urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2", DespatchAdvice: [{ ID: v("DES-1"), IssueDate: v("2026-11-03"), OrderReference: [{ ID: v("PO-1") }], DespatchSupplierParty: party("Seller", "SELLER"), DeliveryCustomerParty: party("Buyer", "BUYER"), DespatchLine: [{ ID: v("1"), DeliveredQuantity: [{ _: 5, unitCode: "EA" }], OrderLineReference: [{ LineID: v("10") }], Item: [{ Name: v("Widget"), SellersItemIdentification: [{ ID: v("SKU-1") }] }] }] }] }
}

function invoice() {
  return { _D: "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2", Invoice: [{ ID: v("INV-1"), IssueDate: v("2026-11-04"), DocumentCurrencyCode: v("GBP"), OrderReference: [{ ID: v("PO-1") }], AccountingSupplierParty: party("Seller", "SELLER"), AccountingCustomerParty: party("Buyer", "BUYER"), LegalMonetaryTotal: [{ PayableAmount: [{ _: 25.5, currencyID: "GBP" }] }], InvoiceLine: [{ ID: v("1"), InvoicedQuantity: [{ _: 6, unitCode: "EA" }], LineExtensionAmount: [{ _: 25.5, currencyID: "GBP" }], Item: [{ Name: v("Widget") }] }] }] }
}

function receipt() {
  return { _D: "urn:oasis:names:specification:ubl:schema:xsd:ReceiptAdvice-2", ReceiptAdvice: [{ ID: v("REC-1"), IssueDate: v("2026-11-04"), OrderReference: [{ ID: v("PO-1") }], DespatchDocumentReference: [{ ID: v("DES-1") }], DeliveryCustomerParty: party("Buyer", "BUYER"), DespatchSupplierParty: party("Seller", "SELLER"), ReceiptLine: [{ ID: v("1"), ReceivedQuantity: [{ _: 5, unitCode: "EA" }], DespatchLineReference: [{ LineID: v("1") }], Item: [{ Name: v("Widget"), SellersItemIdentification: [{ ID: v("SKU-1") }] }] }] }] }
}

describe("UBL smart copy", () => {
  it.each(Object.entries(smartCopyTransitions).flatMap(([source, targets]) => targets.map((target) => [source, target]))) (
    "supports the business transition %s to %s",
    (source, target) => {
      const contents = { ORDER: order(), ORDER_RESPONSE: response(), DESPATCH_ADVICE: despatch(), RECEIPT_ADVICE: receipt(), INVOICE: invoice() } as const
      expect(() => buildSmartCopyDraft(target as keyof typeof smartCopyTransitions, [{ documentId: "source", type: source as keyof typeof smartCopyTransitions, content: contents[source as keyof typeof contents] }])).not.toThrow()
    },
  )

  it("orients parties and copies order values into a response", () => {
    const draft = buildSmartCopyDraft(
      "ORDER_RESPONSE",
      [{ documentId: "order", type: "ORDER", content: order() }],
      { partnerId: "d9c01ea5-f555-4c6d-9c87-1465ea78ac05" },
    )
    expect(draft.values).toMatchObject({ senderName: "Seller", senderEndpoint: "SELLER", recipientName: "Buyer", recipientEndpoint: "BUYER", currency: "GBP", references: { order: "PO-1" } })
    expect(draft.values.lines[0]).toMatchObject({ itemName: "Widget", itemIdentifier: "SKU-1", quantity: "8", unitCode: "EA", unitPrice: "4.25", orderLineReference: "10" })
    expect(draft.partnerId).toBe("d9c01ea5-f555-4c6d-9c87-1465ea78ac05")
    expect(draft.requiresCommercialReview).toBe(true)
  })

  it("uses the nearest stage quantity and fills its missing price from the order ancestor", () => {
    const draft = buildSmartCopyDraft("INVOICE", [
      { documentId: "response", type: "ORDER_RESPONSE", content: response() },
      { documentId: "order", type: "ORDER", content: order() },
    ])
    expect(draft.values.lines[0]).toMatchObject({ quantity: "6", unitPrice: "4.25", itemIdentifier: "SKU-1" })
    expect(draft.sources.map((source) => source.reference)).toEqual(["OR-1", "PO-1"])
  })

  it("copies despatched quantities and reverses parties for a receipt", () => {
    const draft = buildSmartCopyDraft("RECEIPT_ADVICE", [{ documentId: "despatch", type: "DESPATCH_ADVICE", content: despatch() }])
    expect(draft.values).toMatchObject({ senderName: "Buyer", recipientName: "Seller", references: { order: "PO-1", despatch: "DES-1" } })
    expect(draft.values.lines[0]).toMatchObject({ quantity: "5", despatchLineReference: "1" })
  })

  it("creates an invoice allocation instead of fake item quantities", () => {
    const draft = buildSmartCopyDraft("REMITTANCE_ADVICE", [{ documentId: "invoice", type: "INVOICE", content: invoice() }])
    expect(draft.values).toMatchObject({ senderName: "Buyer", recipientName: "Seller", currency: "GBP" })
    expect(draft.values.lines).toEqual([expect.objectContaining({ invoiceReference: "INV-1", amount: "25.5" })])
  })

  it("rejects unsupported transitions", () => {
    expect(() => buildSmartCopyDraft("REMITTANCE_ADVICE", [{ documentId: "order", type: "ORDER", content: order() }])).toThrow(/cannot be created/)
  })
})

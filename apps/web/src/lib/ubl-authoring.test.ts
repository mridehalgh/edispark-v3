import { describe, expect, it } from "vitest"

import {
  authoringDefinitions,
  buildUblDocument,
  fillRemainingAllocations,
  getLineAllocationSummary,
  type AuthorableDocumentType,
  type AuthoringHandlingUnit,
  type AuthoringLine,
} from "./ubl-authoring"

const types = Object.keys(authoringDefinitions) as AuthorableDocumentType[]

describe("UBL authoring", () => {
  const allocationLine: AuthoringLine = {
    id: "1",
    itemName: "Spark Water 750",
    itemIdentifier: "SKU-1",
    quantity: "48",
    unitCode: "EA",
    unitPrice: "0",
  }

  const handlingUnits: AuthoringHandlingUnit[] = [
    { id: "PALLET-1", sscc: "", typeCode: "PALLET", packageCount: "1", grossWeight: "", weightUnit: "KGM", shippingMarks: "", allocations: [{ lineId: "1", quantity: "18" }] },
    { id: "PALLET-2", sscc: "", typeCode: "PALLET", packageCount: "1", grossWeight: "", weightUnit: "KGM", shippingMarks: "", allocations: [{ lineId: "1", quantity: "20" }] },
  ]

  it("summarises what is packed here, elsewhere, and still remaining", () => {
    expect(getLineAllocationSummary(allocationLine, handlingUnits, 0)).toEqual({
      required: 48,
      allocatedHere: 18,
      allocatedElsewhere: 20,
      allocatedTotal: 38,
      remaining: 10,
      availableForCurrent: 28,
      overAllocated: 0,
      complete: false,
    })
  })

  it("reports over-allocation without producing a negative remaining quantity", () => {
    const overAllocatedUnits = handlingUnits.map((unit, index) => index === 0
      ? { ...unit, allocations: [{ lineId: "1", quantity: "30" }] }
      : unit)

    expect(getLineAllocationSummary(allocationLine, overAllocatedUnits, 0)).toMatchObject({
      allocatedTotal: 50,
      remaining: 0,
      availableForCurrent: 28,
      overAllocated: 2,
      complete: false,
    })
  })

  it("fills each line with only the quantity not already assigned to other units", () => {
    const secondLine = { ...allocationLine, id: "2", itemName: "Oat Bar 12 Pack", quantity: "24" }
    const units = handlingUnits.map((unit, index) => index === 0
      ? { ...unit, allocations: [{ lineId: "1", quantity: "18" }, { lineId: "2", quantity: "5" }] }
      : { ...unit, allocations: [...unit.allocations, { lineId: "2", quantity: "4" }] })

    const result = fillRemainingAllocations([allocationLine, secondLine], units, 0)

    expect(result[0].allocations).toEqual([
      { lineId: "1", quantity: "28" },
      { lineId: "2", quantity: "20" },
    ])
    expect(result[1]).toEqual(units[1])
  })

  it.each(types)("builds an OASIS UBL 2.1 %s envelope", (documentType) => {
    const definition = authoringDefinitions[documentType]
    const result = buildUblDocument({ documentType, id: "DOC-1", issueDate: "2026-11-04", references: { order: "PO-1", despatch: "DES-1", invoice: "INV-1" }, senderName: "[name]", senderEndpoint: "[partner_identifier]", recipientName: "[name]", recipientEndpoint: "[tenant_identifier]", currency: "GBP", note: "Test document", lines: [{ id: "1", itemName: "Test item", itemIdentifier: "ITEM-1", quantity: "2", unitCode: "EA", unitPrice: "3.50", invoiceReference: "INV-1" }] }) as Record<string, unknown>

    expect(result._D).toBe(definition.namespace)
    expect(result[definition.root]).toBeDefined()
  })

  it("writes invoice, despatch, and billing references into their OASIS aggregates", () => {
    const invoice = buildUblDocument({ documentType: "INVOICE", id: "INV-2", issueDate: "2026-11-04", references: { order: "PO-1", despatch: "DES-1" }, senderName: "[name]", senderEndpoint: "[tenant_identifier]", recipientName: "[name]", recipientEndpoint: "[partner_identifier]", currency: "GBP", note: "", lines: [{ id: "1", itemName: "Item", itemIdentifier: "SKU-1", quantity: "2", unitCode: "EA", unitPrice: "3.50", orderLineReference: "10" }] }) as any
    const remittance = buildUblDocument({ documentType: "REMITTANCE_ADVICE", id: "REM-1", issueDate: "2026-11-04", references: { invoice: "INV-2" }, senderName: "[name]", senderEndpoint: "[partner_identifier]", recipientName: "[name]", recipientEndpoint: "[tenant_identifier]", currency: "GBP", note: "", lines: [{ id: "1", itemName: "", itemIdentifier: "", quantity: "0", unitCode: "EA", unitPrice: "0", amount: "7.00", invoiceReference: "INV-2" }] }) as any

    expect(invoice.Invoice[0].OrderReference[0].ID[0]._).toBe("PO-1")
    expect(invoice.Invoice[0].DespatchDocumentReference[0].ID[0]._).toBe("DES-1")
    expect(invoice.Invoice[0].InvoiceLine[0].OrderLineReference[0].LineID[0]._).toBe("10")
    expect(remittance.RemittanceAdvice[0].RemittanceAdviceLine[0].BillingReference[0].InvoiceDocumentReference[0].ID[0]._).toBe("INV-2")
    expect(remittance.RemittanceAdvice[0].TotalPaymentAmount[0]._).toBe(7)
  })

  it("maps multi-pallet ASN allocations and traceability into UBL handling units", () => {
    const result = buildUblDocument({
      documentType: "DESPATCH_ADVICE", id: "ASN-1", issueDate: "2026-11-04", references: { order: "PO-1" },
      senderName: "[name]", senderEndpoint: "[tenant_identifier]", recipientName: "[name]", recipientEndpoint: "[partner_identifier]",
      currency: "GBP", note: "", lines: [{ id: "1", itemName: "Item", itemIdentifier: "SKU-1", quantity: "10", unitCode: "EA", unitPrice: "0", orderLineReference: "10", lotNumber: "LOT-1", expiryDate: "2027-01-01" }],
      specialist: {
        responseCode: "", responseDescription: "", requestedDeliveryDate: "", promisedDeliveryDate: "", actualDeliveryDate: "2026-11-05",
        deliveryAddress: { line1: "[address]", city: "[city]", postcode: "[postcode]", countryCode: "GB" }, paymentMeansCode: "", paymentDueDate: "", paymentTerms: "", paymentAccountId: "", taxCategory: "S", taxPercent: "20", taxAmount: "", allowanceAmount: "", allowanceReason: "", shipmentId: "SHIP-1", trackingId: "TRACK-1", carrierName: "[name]", vehicleId: "VEHICLE-1", grossWeight: "100", weightUnit: "KGM",
        handlingUnits: [
          { id: "PALLET-1", sscc: "123456789012345675", typeCode: "PALLET", packageCount: "5", grossWeight: "50", weightUnit: "KGM", shippingMarks: "A", allocations: [{ lineId: "1", quantity: "6" }] },
          { id: "PALLET-2", sscc: "123456789012345682", typeCode: "PALLET", packageCount: "4", grossWeight: "50", weightUnit: "KGM", shippingMarks: "B", allocations: [{ lineId: "1", quantity: "4" }] },
        ],
      },
    }) as any

    const units = result.DespatchAdvice[0].Shipment[0].TransportHandlingUnit
    expect(units).toHaveLength(2)
    expect(units[0].TraceID[0]._).toBe("123456789012345675")
    expect(units[0].HandlingUnitDespatchLine[0].DeliveredQuantity[0]._).toBe(6)
    expect(result.DespatchAdvice[0].DespatchLine[0].Item[0].ItemInstance[0].LotIdentification[0].LotNumberID[0]._).toBe("LOT-1")
  })
})

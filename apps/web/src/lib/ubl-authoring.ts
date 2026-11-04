export type AuthorableDocumentType =
  | "ORDER"
  | "ORDER_RESPONSE"
  | "DESPATCH_ADVICE"
  | "INVOICE"
  | "REMITTANCE_ADVICE"
  | "RECEIPT_ADVICE"

export interface AuthoringLine {
  id: string
  itemName: string
  itemIdentifier: string
  quantity: string
  unitCode: string
  unitPrice: string
  amount?: string
  orderLineReference?: string
  despatchLineReference?: string
  invoiceReference?: string
  responseCode?: string
  rejectedQuantity?: string
  rejectionReason?: string
  lotNumber?: string
  expiryDate?: string
  serialNumbers?: string
}

export interface AuthoringAddress {
  line1: string
  city: string
  postcode: string
  countryCode: string
}

export interface HandlingUnitAllocation {
  lineId: string
  quantity: string
}

export interface AuthoringHandlingUnit {
  id: string
  sscc: string
  typeCode: string
  packageCount: string
  grossWeight: string
  weightUnit: string
  shippingMarks: string
  allocations: HandlingUnitAllocation[]
  ssccSource?: "GENERATED" | "MANUAL"
  ssccReservationId?: string
}

export interface LineAllocationSummary {
  required: number
  allocatedHere: number
  allocatedElsewhere: number
  allocatedTotal: number
  remaining: number
  availableForCurrent: number
  overAllocated: number
  complete: boolean
}

const allocationPrecision = 1_000_000

function allocationNumber(value: string) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}

function roundedAllocation(value: number) {
  return Math.round(value * allocationPrecision) / allocationPrecision
}

export function getLineAllocationSummary(
  line: AuthoringLine,
  handlingUnits: AuthoringHandlingUnit[],
  currentUnitIndex: number,
): LineAllocationSummary {
  const required = allocationNumber(line.quantity)
  const allocations = handlingUnits.map((unit) => allocationNumber(
    unit.allocations.find((entry) => entry.lineId === line.id)?.quantity ?? "",
  ))
  const allocatedHere = allocations[currentUnitIndex] ?? 0
  const allocatedTotal = roundedAllocation(allocations.reduce((total, quantity) => total + quantity, 0))
  const allocatedElsewhere = roundedAllocation(allocatedTotal - allocatedHere)
  const remaining = roundedAllocation(Math.max(0, required - allocatedTotal))
  const availableForCurrent = roundedAllocation(Math.max(0, required - allocatedElsewhere))
  const overAllocated = roundedAllocation(Math.max(0, allocatedTotal - required))

  return {
    required,
    allocatedHere,
    allocatedElsewhere,
    allocatedTotal,
    remaining,
    availableForCurrent,
    overAllocated,
    complete: required > 0 && remaining === 0 && overAllocated === 0,
  }
}

export function fillRemainingAllocations(
  lines: AuthoringLine[],
  handlingUnits: AuthoringHandlingUnit[],
  currentUnitIndex: number,
) {
  return handlingUnits.map((unit, unitIndex) => {
    if (unitIndex !== currentUnitIndex) return unit

    const lineIds = new Set(lines.map((line) => line.id))
    const retained = unit.allocations.filter((allocation) => !lineIds.has(allocation.lineId))
    const filled = lines.flatMap((line) => {
      const quantity = getLineAllocationSummary(line, handlingUnits, currentUnitIndex).availableForCurrent
      return quantity > 0 ? [{ lineId: line.id, quantity: String(quantity) }] : []
    })

    return { ...unit, allocations: [...retained, ...filled] }
  })
}

export function hasValidSscc(value: string) {
  if (!/^\d{18}$/.test(value)) return false
  const digits = value.split("").map(Number)
  const expected = digits.pop()
  const sum = digits.reduce((total, digit, index) => total + digit * (index % 2 === 0 ? 3 : 1), 0)
  return expected === (10 - (sum % 10)) % 10
}

export interface AuthoringSpecialistValues {
  responseCode: string
  responseDescription: string
  requestedDeliveryDate: string
  promisedDeliveryDate: string
  actualDeliveryDate: string
  actualDespatchDate?: string
  deliveryAddress: AuthoringAddress
  paymentMeansCode: string
  paymentDueDate: string
  paymentTerms: string
  paymentAccountId: string
  taxCategory: string
  taxPercent: string
  taxAmount: string
  allowanceAmount: string
  allowanceReason: string
  shipmentId: string
  trackingId: string
  carrierName: string
  vehicleId: string
  grossWeight: string
  weightUnit: string
  handlingUnits: AuthoringHandlingUnit[]
}

export interface AuthoringReferences {
  order?: string
  despatch?: string
  invoice?: string
}

export interface AuthoringValues {
  documentType: AuthorableDocumentType
  id: string
  issueDate: string
  references: AuthoringReferences
  senderName: string
  senderEndpoint: string
  recipientName: string
  recipientEndpoint: string
  currency: string
  note: string
  lines: AuthoringLine[]
  specialist?: AuthoringSpecialistValues
  advancedFields?: AuthoringAdvancedField[]
}

export interface AuthoringAdvancedField {
  fieldKey: string
  ublKey: string
  value: string
}

export interface AuthoringDefinition {
  label: string
  root: string
  namespace: string
  senderKey: string
  senderLabel: string
  recipientKey: string
  recipientLabel: string
  referenceKey?: string
  lineKey: string
  relationType: "RESPONDS_TO" | "DESPATCHES" | "INVOICES" | "REMITS" | "RECEIVES" | "RELATED_TO"
}

export const authoringDefinitions: Record<AuthorableDocumentType, AuthoringDefinition> = {
  ORDER: { label: "Order", root: "Order", namespace: "urn:oasis:names:specification:ubl:schema:xsd:Order-2", senderKey: "BuyerCustomerParty", senderLabel: "Buyer", recipientKey: "SellerSupplierParty", recipientLabel: "Seller", lineKey: "OrderLine", relationType: "RELATED_TO" },
  ORDER_RESPONSE: { label: "Order response", root: "OrderResponse", namespace: "urn:oasis:names:specification:ubl:schema:xsd:OrderResponse-2", senderKey: "SellerSupplierParty", senderLabel: "Seller", recipientKey: "BuyerCustomerParty", recipientLabel: "Buyer", referenceKey: "OrderReference", lineKey: "OrderLine", relationType: "RESPONDS_TO" },
  DESPATCH_ADVICE: { label: "Despatch advice", root: "DespatchAdvice", namespace: "urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2", senderKey: "DespatchSupplierParty", senderLabel: "Despatch supplier", recipientKey: "DeliveryCustomerParty", recipientLabel: "Delivery customer", referenceKey: "OrderReference", lineKey: "DespatchLine", relationType: "DESPATCHES" },
  INVOICE: { label: "Invoice", root: "Invoice", namespace: "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2", senderKey: "AccountingSupplierParty", senderLabel: "Supplier", recipientKey: "AccountingCustomerParty", recipientLabel: "Customer", referenceKey: "OrderReference", lineKey: "InvoiceLine", relationType: "INVOICES" },
  REMITTANCE_ADVICE: { label: "Remittance advice", root: "RemittanceAdvice", namespace: "urn:oasis:names:specification:ubl:schema:xsd:RemittanceAdvice-2", senderKey: "AccountingCustomerParty", senderLabel: "Customer", recipientKey: "AccountingSupplierParty", recipientLabel: "Supplier", lineKey: "RemittanceAdviceLine", relationType: "REMITS" },
  RECEIPT_ADVICE: { label: "Receipt advice", root: "ReceiptAdvice", namespace: "urn:oasis:names:specification:ubl:schema:xsd:ReceiptAdvice-2", senderKey: "DeliveryCustomerParty", senderLabel: "Receiving customer", recipientKey: "DespatchSupplierParty", recipientLabel: "Despatch supplier", referenceKey: "DespatchDocumentReference", lineKey: "ReceiptLine", relationType: "RECEIVES" },
}

const aggregateNamespace = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
const basicNamespace = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
const value = (item: string | number | boolean) => [{ _: item }]

function party(name: string, endpoint: string) {
  return [{ Party: [{
    EndpointID: [{ _: endpoint, schemeID: "GLN" }],
    PartyName: [{ Name: value(name) }],
    PartyLegalEntity: [{ RegistrationName: value(name) }],
  }] }]
}

function numberValue(input: string) {
  const parsed = Number(input)
  return Number.isFinite(parsed) ? parsed : 0
}

function item(line: AuthoringLine) {
  const result: Record<string, unknown> = {
    Name: value(line.itemName),
    SellersItemIdentification: [{ ID: value(line.itemIdentifier) }],
  }
  const instances: Record<string, unknown>[] = []
  const serials = line.serialNumbers?.split(",").map((entry) => entry.trim()).filter(Boolean) ?? []
  serials.forEach((serial) => instances.push({ SerialID: value(serial) }))
  if (line.lotNumber?.trim() || line.expiryDate?.trim()) {
    instances.push({ LotIdentification: [{
      ...(line.lotNumber?.trim() ? { LotNumberID: value(line.lotNumber.trim()) } : {}),
      ...(line.expiryDate?.trim() ? { ExpiryDate: value(line.expiryDate.trim()) } : {}),
    }] })
  }
  if (instances.length) result.ItemInstance = instances
  return [result]
}

function lineValue(type: AuthorableDocumentType, line: AuthoringLine, currency: string) {
  const quantity = { _: numberValue(line.quantity), unitCode: line.unitCode }
  const amount = line.amount === undefined
    ? numberValue(line.quantity) * numberValue(line.unitPrice)
    : numberValue(line.amount)
  const orderLineReference = line.orderLineReference
    ? [{ LineID: value(line.orderLineReference) }]
    : undefined
  if (type === "ORDER") return { LineItem: [{ ID: value(line.id), Quantity: [quantity], LineExtensionAmount: [{ _: amount, currencyID: currency }], Price: [{ PriceAmount: [{ _: numberValue(line.unitPrice), currencyID: currency }] }], Item: item(line) }] }
  if (type === "ORDER_RESPONSE") return { LineItem: [{ ID: value(line.id), Quantity: [quantity], LineStatusCode: value(line.responseCode || "Accepted"), Item: item(line) }] }
  if (type === "DESPATCH_ADVICE") return { ID: value(line.id), DeliveredQuantity: [quantity], ...(orderLineReference ? { OrderLineReference: orderLineReference } : {}), Item: item(line) }
  if (type === "INVOICE") return { ID: value(line.id), InvoicedQuantity: [quantity], LineExtensionAmount: [{ _: amount, currencyID: currency }], ...(orderLineReference ? { OrderLineReference: orderLineReference } : {}), Price: [{ PriceAmount: [{ _: numberValue(line.unitPrice), currencyID: currency }] }], Item: item(line) }
  if (type === "REMITTANCE_ADVICE") return {
    ID: value(line.id),
    CreditLineAmount: [{ _: amount, currencyID: currency }],
    BalanceAmount: [{ _: 0, currencyID: currency }],
    ...(line.invoiceReference ? { BillingReference: [{ InvoiceDocumentReference: [{ ID: value(line.invoiceReference) }] }] } : {}),
  }
  return {
    ID: value(line.id),
    ReceivedQuantity: [quantity],
    ShortQuantity: [{ _: Math.max(0, numberValue(line.rejectedQuantity ?? "0")), unitCode: line.unitCode }],
    ...(line.rejectedQuantity && numberValue(line.rejectedQuantity) > 0 ? { RejectedQuantity: [{ _: numberValue(line.rejectedQuantity), unitCode: line.unitCode }] } : {}),
    ...(line.rejectionReason?.trim() ? { RejectionNote: value(line.rejectionReason.trim()) } : {}),
    ...(line.despatchLineReference ? { DespatchLineReference: [{ LineID: value(line.despatchLineReference) }] } : {}),
    Item: item(line),
  }
}

function address(valueToMap: AuthoringAddress) {
  const result: Record<string, unknown> = {}
  if (valueToMap.line1.trim()) result.StreetName = value(valueToMap.line1.trim())
  if (valueToMap.city.trim()) result.CityName = value(valueToMap.city.trim())
  if (valueToMap.postcode.trim()) result.PostalZone = value(valueToMap.postcode.trim())
  if (valueToMap.countryCode.trim()) result.Country = [{ IdentificationCode: value(valueToMap.countryCode.trim().toUpperCase()) }]
  return result
}

function delivery(specialist: AuthoringSpecialistValues) {
  const result: Record<string, unknown> = {}
  if (specialist.actualDeliveryDate) result.ActualDeliveryDate = value(specialist.actualDeliveryDate)
  if (specialist.requestedDeliveryDate) result.RequestedDeliveryPeriod = [{ StartDate: value(specialist.requestedDeliveryDate) }]
  if (specialist.promisedDeliveryDate) result.PromisedDeliveryPeriod = [{ StartDate: value(specialist.promisedDeliveryDate) }]
  if (specialist.actualDespatchDate) result.Despatch = [{ ActualDespatchDate: value(specialist.actualDespatchDate) }]
  const deliveryAddress = address(specialist.deliveryAddress)
  if (Object.keys(deliveryAddress).length) result.DeliveryAddress = [deliveryAddress]
  return result
}

function handlingUnitLine(line: AuthoringLine, quantity: string) {
  return {
    ID: value(line.id),
    DeliveredQuantity: [{ _: numberValue(quantity), unitCode: line.unitCode }],
    ...(line.orderLineReference ? { OrderLineReference: [{ LineID: value(line.orderLineReference) }] } : {}),
    Item: item(line),
  }
}

function shipment(specialist: AuthoringSpecialistValues, lines: AuthoringLine[], receipt: boolean) {
  const result: Record<string, unknown> = { ID: value(specialist.shipmentId || "1") }
  if (specialist.trackingId) result.Consignment = [{ ID: value(specialist.trackingId) }]
  if (specialist.grossWeight) result.GrossWeightMeasure = [{ _: numberValue(specialist.grossWeight), unitCode: specialist.weightUnit || "KGM" }]
  if (specialist.carrierName || specialist.vehicleId) {
    result.ShipmentStage = [{
      ...(specialist.carrierName ? { CarrierParty: [{ PartyName: [{ Name: value(specialist.carrierName) }] }] } : {}),
      ...(specialist.vehicleId ? { TransportMeans: [{ JourneyID: value(specialist.vehicleId) }] } : {}),
    }]
  }
  const units = specialist.handlingUnits.map((unit) => {
    const mapped: Record<string, unknown> = {
      ID: value(unit.id),
      ...(unit.typeCode ? { TransportHandlingUnitTypeCode: value(unit.typeCode) } : {}),
      ...(unit.packageCount ? { TotalPackageQuantity: value(numberValue(unit.packageCount)) } : {}),
      ...(unit.shippingMarks ? { ShippingMarks: value(unit.shippingMarks) } : {}),
      ...(unit.sscc ? { TraceID: value(unit.sscc) } : {}),
      ...(unit.grossWeight ? { ActualPackage: [{ ID: value(unit.id), GrossWeightMeasure: [{ _: numberValue(unit.grossWeight), unitCode: unit.weightUnit || "KGM" }] }] } : {}),
    }
    const allocations = unit.allocations.map((allocation) => {
      const line = lines.find((candidate) => candidate.id === allocation.lineId)
      return line ? handlingUnitLine(line, allocation.quantity) : undefined
    }).filter((entry) => entry !== undefined)
    if (allocations.length) mapped[receipt ? "ReceivedHandlingUnitReceiptLine" : "HandlingUnitDespatchLine"] = allocations
    return mapped
  })
  if (units.length) result.TransportHandlingUnit = units
  const mappedDelivery = delivery(specialist)
  if (Object.keys(mappedDelivery).length) result.Delivery = [mappedDelivery]
  return result
}

export function buildUblDocument(values: AuthoringValues) {
  const definition = authoringDefinitions[values.documentType]
  const total = values.lines.reduce((sum, line) => sum + (line.amount === undefined
    ? numberValue(line.quantity) * numberValue(line.unitPrice)
    : numberValue(line.amount)), 0)
  const document: Record<string, unknown> = {
    UBLVersionID: value("2.1"),
    ID: value(values.id),
    IssueDate: value(values.issueDate),
    [definition.senderKey]: party(values.senderName, values.senderEndpoint),
    [definition.recipientKey]: party(values.recipientName, values.recipientEndpoint),
    [definition.lineKey]: values.lines.map((line) => lineValue(values.documentType, line, values.currency)),
  }
  if (values.documentType !== "DESPATCH_ADVICE" && values.documentType !== "RECEIPT_ADVICE") {
    document.DocumentCurrencyCode = value(values.currency)
  }
  if (values.note.trim()) document.Note = value(values.note.trim())
  if (values.references.order?.trim()) document.OrderReference = [{ ID: value(values.references.order.trim()) }]
  if (values.references.despatch?.trim()) document.DespatchDocumentReference = [{ ID: value(values.references.despatch.trim()) }]
  values.advancedFields?.filter((field) => field.value.trim()).forEach((field) => {
    document[field.ublKey] = value(field.value.trim())
  })
  const specialist = values.specialist
  if (specialist) {
    if (values.documentType === "ORDER_RESPONSE" && specialist.responseCode) {
      document.ResponseCode = value(specialist.responseCode)
      if (specialist.responseDescription.trim()) document.Description = value(specialist.responseDescription.trim())
    }
    const mappedDelivery = delivery(specialist)
    if (!["DESPATCH_ADVICE", "RECEIPT_ADVICE"].includes(values.documentType) && Object.keys(mappedDelivery).length) {
      document.Delivery = [mappedDelivery]
    }
    if (["DESPATCH_ADVICE", "RECEIPT_ADVICE"].includes(values.documentType)) {
      document.Shipment = [shipment(specialist, values.lines, values.documentType === "RECEIPT_ADVICE")]
    }
    if (specialist.paymentMeansCode && ["INVOICE", "REMITTANCE_ADVICE"].includes(values.documentType)) {
      document.PaymentMeans = [{
        PaymentMeansCode: value(specialist.paymentMeansCode),
        ...(specialist.paymentDueDate ? { PaymentDueDate: value(specialist.paymentDueDate) } : {}),
        ...(specialist.paymentAccountId ? { PayeeFinancialAccount: [{ ID: value(specialist.paymentAccountId) }] } : {}),
      }]
    }
    if (specialist.paymentTerms && ["ORDER", "ORDER_RESPONSE", "INVOICE"].includes(values.documentType)) {
      document.PaymentTerms = [{ Note: value(specialist.paymentTerms) }]
    }
    if (specialist.allowanceAmount && ["ORDER", "ORDER_RESPONSE", "INVOICE"].includes(values.documentType)) {
      document.AllowanceCharge = [{ ChargeIndicator: value(false), Amount: [{ _: numberValue(specialist.allowanceAmount), currencyID: values.currency }], ...(specialist.allowanceReason ? { AllowanceChargeReason: value(specialist.allowanceReason) } : {}) }]
    }
    if (specialist.taxAmount && ["ORDER", "ORDER_RESPONSE", "INVOICE", "REMITTANCE_ADVICE"].includes(values.documentType)) {
      document.TaxTotal = [{ TaxAmount: [{ _: numberValue(specialist.taxAmount), currencyID: values.currency }], TaxSubtotal: [{ TaxAmount: [{ _: numberValue(specialist.taxAmount), currencyID: values.currency }], TaxCategory: [{ ID: value(specialist.taxCategory || "S"), Percent: value(numberValue(specialist.taxPercent)), TaxScheme: [{ ID: value("VAT") }] }] }] }]
    }
  }
  if (values.documentType === "INVOICE") {
    document.LegalMonetaryTotal = [{ LineExtensionAmount: [{ _: total, currencyID: values.currency }], PayableAmount: [{ _: total, currencyID: values.currency }] }]
  }
  if (values.documentType === "ORDER") {
    document.AnticipatedMonetaryTotal = [{ LineExtensionAmount: [{ _: total, currencyID: values.currency }], PayableAmount: [{ _: total, currencyID: values.currency }] }]
  }
  if (values.documentType === "REMITTANCE_ADVICE") {
    document.TotalPaymentAmount = [{ _: total, currencyID: values.currency }]
  }
  return {
    _D: definition.namespace,
    _A: aggregateNamespace,
    _B: basicNamespace,
    [definition.root]: [document],
  }
}

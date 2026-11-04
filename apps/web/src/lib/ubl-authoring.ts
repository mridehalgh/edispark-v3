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
}

export interface AuthoringValues {
  documentType: AuthorableDocumentType
  id: string
  issueDate: string
  reference: string
  senderName: string
  senderEndpoint: string
  recipientName: string
  recipientEndpoint: string
  currency: string
  note: string
  lines: AuthoringLine[]
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
const value = (item: string | number) => [{ _: item }]

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
  return [{
    Name: value(line.itemName),
    SellersItemIdentification: [{ ID: value(line.itemIdentifier) }],
  }]
}

function lineValue(type: AuthorableDocumentType, line: AuthoringLine, currency: string) {
  const quantity = { _: numberValue(line.quantity), unitCode: line.unitCode }
  const amount = numberValue(line.quantity) * numberValue(line.unitPrice)
  if (type === "ORDER") return { LineItem: [{ ID: value(line.id), Quantity: [quantity], LineExtensionAmount: [{ _: amount, currencyID: currency }], Price: [{ PriceAmount: [{ _: numberValue(line.unitPrice), currencyID: currency }] }], Item: item(line) }] }
  if (type === "ORDER_RESPONSE") return { LineItem: [{ ID: value(line.id), Quantity: [quantity], LineStatusCode: value("Accepted"), Item: item(line) }] }
  if (type === "DESPATCH_ADVICE") return { ID: value(line.id), DeliveredQuantity: [quantity], Item: item(line) }
  if (type === "INVOICE") return { ID: value(line.id), InvoicedQuantity: [quantity], LineExtensionAmount: [{ _: amount, currencyID: currency }], Price: [{ PriceAmount: [{ _: numberValue(line.unitPrice), currencyID: currency }] }], Item: item(line) }
  if (type === "REMITTANCE_ADVICE") return { ID: value(line.id), CreditLineAmount: [{ _: amount, currencyID: currency }], BalanceAmount: [{ _: 0, currencyID: currency }] }
  return { ID: value(line.id), ReceivedQuantity: [quantity], ShortQuantity: [{ _: 0, unitCode: line.unitCode }], Item: item(line) }
}

export function buildUblDocument(values: AuthoringValues) {
  const definition = authoringDefinitions[values.documentType]
  const total = values.lines.reduce((sum, line) => sum + numberValue(line.quantity) * numberValue(line.unitPrice), 0)
  const document: Record<string, unknown> = {
    UBLVersionID: value("2.1"),
    ID: value(values.id),
    IssueDate: value(values.issueDate),
    DocumentCurrencyCode: value(values.currency),
    [definition.senderKey]: party(values.senderName, values.senderEndpoint),
    [definition.recipientKey]: party(values.recipientName, values.recipientEndpoint),
    [definition.lineKey]: values.lines.map((line) => lineValue(values.documentType, line, values.currency)),
  }
  if (values.note.trim()) document.Note = value(values.note.trim())
  if (definition.referenceKey && values.reference.trim()) {
    document[definition.referenceKey] = [{ ID: value(values.reference.trim()) }]
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

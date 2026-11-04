import {
  entries,
  first,
  itemIdentifier,
  money,
  party,
  quantity,
  record,
  text,
  type JsonRecord,
  type UblParty,
} from "./ubl-common"
import type {
  AuthorableDocumentType,
  AuthoringLine,
  AuthoringReferences,
  AuthoringValues,
} from "./ubl-authoring"

export const smartCopyTransitions: Record<AuthorableDocumentType, AuthorableDocumentType[]> = {
  ORDER: ["ORDER_RESPONSE", "DESPATCH_ADVICE", "INVOICE"],
  ORDER_RESPONSE: ["DESPATCH_ADVICE", "INVOICE"],
  DESPATCH_ADVICE: ["RECEIPT_ADVICE", "INVOICE"],
  RECEIPT_ADVICE: ["INVOICE"],
  INVOICE: ["REMITTANCE_ADVICE"],
  REMITTANCE_ADVICE: [],
}

export interface SmartCopySourceInput {
  documentId: string
  type: AuthorableDocumentType
  content: unknown
}

export interface SmartCopySource {
  documentId: string
  type: AuthorableDocumentType
  reference: string
}

export interface SmartCopyDraft {
  partnerId?: string
  values: Omit<AuthoringValues, "documentType" | "id" | "issueDate" | "note">
  sources: SmartCopySource[]
  provenance: Record<string, string>
  warnings: string[]
  requiresCommercialReview: boolean
}

export interface SmartCopyContext {
  partnerId?: string
}

interface NormalizedLine {
  id: string
  orderLineReference?: string
  despatchLineReference?: string
  itemName?: string
  itemIdentifier?: string
  quantity?: string
  unitCode?: string
  unitPrice?: string
  amount?: string
  invoiceReference?: string
  sourceId: string
}

interface NormalizedDocument {
  source: SmartCopySource
  buyer?: UblParty
  seller?: UblParty
  currency?: string
  references: AuthoringReferences
  lines: NormalizedLine[]
  payable?: string
}

const roots: Record<AuthorableDocumentType, string> = {
  ORDER: "Order",
  ORDER_RESPONSE: "OrderResponse",
  DESPATCH_ADVICE: "DespatchAdvice",
  INVOICE: "Invoice",
  REMITTANCE_ADVICE: "RemittanceAdvice",
  RECEIPT_ADVICE: "ReceiptAdvice",
}

const namespaces: Record<AuthorableDocumentType, string> = {
  ORDER: "urn:oasis:names:specification:ubl:schema:xsd:Order-2",
  ORDER_RESPONSE: "urn:oasis:names:specification:ubl:schema:xsd:OrderResponse-2",
  DESPATCH_ADVICE: "urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2",
  INVOICE: "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
  REMITTANCE_ADVICE: "urn:oasis:names:specification:ubl:schema:xsd:RemittanceAdvice-2",
  RECEIPT_ADVICE: "urn:oasis:names:specification:ubl:schema:xsd:ReceiptAdvice-2",
}

export function isCanonicalSmartCopyContent(type: AuthorableDocumentType, content: unknown) {
  const envelope = record(content)
  return envelope?._D === namespaces[type] && Boolean(first(envelope, roots[type]))
}

function scalar(value: number | string | undefined) {
  return value === undefined ? undefined : String(value)
}

function partyRoles(document: JsonRecord, type: AuthorableDocumentType) {
  if (type === "ORDER") return { buyer: party(first(document, "BuyerCustomerParty")), seller: party(first(document, "SellerSupplierParty")) }
  if (type === "ORDER_RESPONSE") return { buyer: party(first(document, "BuyerCustomerParty")), seller: party(first(document, "SellerSupplierParty")) }
  if (type === "DESPATCH_ADVICE" || type === "RECEIPT_ADVICE") return { buyer: party(first(document, "DeliveryCustomerParty")), seller: party(first(document, "DespatchSupplierParty")) }
  return { buyer: party(first(document, "AccountingCustomerParty")), seller: party(first(document, "AccountingSupplierParty")) }
}

function itemDetails(item: unknown) {
  const seller = itemIdentifier(item, "SellersItemIdentification").value
  const buyer = itemIdentifier(item, "BuyersItemIdentification").value
  const standard = itemIdentifier(item, "StandardItemIdentification").value
  return {
    itemName: text(item, "Name") ?? text(item, "Description"),
    itemIdentifier: seller ?? buyer ?? standard,
  }
}

function orderReference(document: unknown) {
  return text(first(document, "OrderReference"), "ID")
}

function parseLine(value: JsonRecord, index: number, type: AuthorableDocumentType, sourceId: string): NormalizedLine {
  const line = type === "ORDER" || type === "ORDER_RESPONSE" ? first(value, "LineItem") ?? value : value
  const item = first(line, "Item")
  const orderLine = first(line, "OrderLineReference") ?? first(value, "OrderLineReference")
  const despatchLine = first(line, "DespatchLineReference") ?? first(value, "DespatchLineReference")
  const billing = first(value, "BillingReference")
  const invoice = first(billing, "InvoiceDocumentReference")
  const qty = quantity(line, "ReceivedQuantity")
    ?? quantity(line, "DeliveredQuantity")
    ?? quantity(line, "InvoicedQuantity")
    ?? quantity(line, "Quantity")
  const price = money(first(line, "Price"), "PriceAmount")
  const amount = money(line, "CreditLineAmount") ?? money(line, "LineExtensionAmount")
  return {
    id: text(line, "ID") ?? String(index + 1),
    orderLineReference: text(orderLine, "LineID"),
    despatchLineReference: text(despatchLine, "LineID"),
    ...itemDetails(item),
    quantity: scalar(qty?.value),
    unitCode: qty?.unit,
    unitPrice: scalar(price?.value),
    amount: scalar(amount?.value),
    invoiceReference: text(invoice, "ID"),
    sourceId,
  }
}

function normalize(input: SmartCopySourceInput): NormalizedDocument {
  const envelope = record(input.content)
  if (!envelope || !isCanonicalSmartCopyContent(input.type, input.content)) {
    throw new Error(`Document ${input.documentId} is not canonical UBL ${roots[input.type]}.`)
  }
  const document = first(envelope, roots[input.type])
  const id = text(document, "ID")
  if (!document || !id) throw new Error(`Document ${input.documentId} is missing its business reference.`)
  const roles = partyRoles(document, input.type)
  const lineKey = input.type === "ORDER" || input.type === "ORDER_RESPONSE"
    ? "OrderLine"
    : input.type === "DESPATCH_ADVICE"
      ? "DespatchLine"
      : input.type === "RECEIPT_ADVICE"
        ? "ReceiptLine"
        : input.type === "INVOICE"
          ? "InvoiceLine"
          : "RemittanceAdviceLine"
  const legalTotal = first(document, "LegalMonetaryTotal")
  const references: AuthoringReferences = {
    order: input.type === "ORDER" ? id : orderReference(document),
    despatch: input.type === "DESPATCH_ADVICE" ? id : text(first(document, "DespatchDocumentReference"), "ID"),
    invoice: input.type === "INVOICE" ? id : undefined,
  }
  return {
    source: { documentId: input.documentId, type: input.type, reference: id },
    ...roles,
    currency: text(document, "DocumentCurrencyCode")
      ?? money(document, "TotalPaymentAmount")?.currency
      ?? money(legalTotal, "PayableAmount")?.currency,
    references,
    lines: entries(document, lineKey).map((line, index) => parseLine(line, index, input.type, input.documentId)),
    payable: scalar(money(legalTotal, "PayableAmount")?.value),
  }
}

function lineKey(line: NormalizedLine) {
  return line.orderLineReference ?? line.itemIdentifier ?? line.id
}

function linesMatch(left: NormalizedLine, right: NormalizedLine) {
  const leftKeys = new Set([left.orderLineReference, left.itemIdentifier, left.id].filter(Boolean))
  return [right.orderLineReference, right.itemIdentifier, right.id].some((key) => key && leftKeys.has(key))
}

function mergeLines(documents: NormalizedDocument[], warnings: string[]) {
  const merged: NormalizedLine[] = []
  documents.forEach((document) => document.lines.forEach((line) => {
    const key = lineKey(line)
    const matches = merged.filter((candidate) => linesMatch(candidate, line))
    if (matches.length > 1) {
      warnings.push(`Line ${key} matched more than one earlier line and must be reviewed.`)
      merged.push({ ...line })
      return
    }
    const current = matches[0]
    if (!current) {
      merged.push({ ...line })
      return
    }
    for (const field of ["orderLineReference", "despatchLineReference", "itemName", "itemIdentifier", "quantity", "unitCode", "unitPrice", "amount", "invoiceReference"] as const) {
      if (!current[field] && line[field]) current[field] = line[field]
      else if (current[field] && line[field] && current[field] !== line[field] && field !== "quantity" && field !== "amount") {
        warnings.push(`Line ${key} has conflicting ${field.replaceAll(/([A-Z])/g, " $1").toLowerCase()} values; the nearest document was used.`)
      }
    }
  }))
  return merged
}

function firstValue<T>(documents: NormalizedDocument[], select: (document: NormalizedDocument) => T | undefined) {
  for (const document of documents) {
    const value = select(document)
    if (value !== undefined) return { value, sourceId: document.source.documentId }
  }
  return undefined
}

function partyValue(partyValue: UblParty | undefined) {
  return {
    name: partyValue?.name ?? partyValue?.legalName ?? "",
    endpoint: partyValue?.endpointId ?? partyValue?.identifiers[0] ?? "",
  }
}

export function buildSmartCopyDraft(
  targetType: AuthorableDocumentType,
  inputs: SmartCopySourceInput[],
  context: SmartCopyContext = {},
): SmartCopyDraft {
  if (inputs.length === 0) throw new Error("At least one source document is required.")
  if (!smartCopyTransitions[inputs[0].type].includes(targetType)) {
    throw new Error(`${targetType} cannot be created from ${inputs[0].type}.`)
  }
  const documents = inputs.map(normalize)
  const warnings: string[] = []
  const buyer = firstValue(documents, (document) => document.buyer)
  const seller = firstValue(documents, (document) => document.seller)
  const currency = firstValue(documents, (document) => document.currency)
  const order = firstValue(documents, (document) => document.references.order)
  const despatch = firstValue(documents, (document) => document.references.despatch)
  const invoice = firstValue(documents, (document) => document.references.invoice)
  const buyerValue = partyValue(buyer?.value)
  const sellerValue = partyValue(seller?.value)
  const sender = targetType === "RECEIPT_ADVICE" || targetType === "REMITTANCE_ADVICE" ? buyerValue : sellerValue
  const recipient = targetType === "RECEIPT_ADVICE" || targetType === "REMITTANCE_ADVICE" ? sellerValue : buyerValue
  const normalizedLines = targetType === "REMITTANCE_ADVICE"
    ? [{
        id: "1",
        invoiceReference: invoice?.value ?? documents[0].source.reference,
        amount: firstValue(documents, (document) => document.payable)?.value ?? "0.00",
        sourceId: documents[0].source.documentId,
      } satisfies NormalizedLine]
    : mergeLines(documents, warnings)
  const lines: AuthoringLine[] = normalizedLines.map((line, index) => ({
    id: String(index + 1),
    itemName: line.itemName ?? "",
    itemIdentifier: line.itemIdentifier ?? "",
    quantity: line.quantity ?? "0",
    unitCode: line.unitCode ?? "EA",
    unitPrice: line.unitPrice ?? "0.00",
    amount: targetType === "REMITTANCE_ADVICE" ? line.amount ?? "0.00" : undefined,
    orderLineReference: line.orderLineReference ?? (documents.some((document) => document.source.type === "ORDER") ? line.id : undefined),
    despatchLineReference: line.despatchLineReference ?? (documents[0].source.type === "DESPATCH_ADVICE" ? line.id : undefined),
    invoiceReference: line.invoiceReference,
  }))
  const provenance: Record<string, string> = {}
  if (buyer) provenance.buyer = buyer.sourceId
  if (seller) provenance.seller = seller.sourceId
  if (currency) provenance.currency = currency.sourceId
  if (order) provenance["references.order"] = order.sourceId
  if (despatch) provenance["references.despatch"] = despatch.sourceId
  if (invoice) provenance["references.invoice"] = invoice.sourceId
  normalizedLines.forEach((line, index) => { provenance[`lines.${index}`] = line.sourceId })
  if (lines.length === 0) warnings.push("No commercial lines could be copied from the selected document chain.")
  return {
    partnerId: context.partnerId,
    values: {
      references: { order: order?.value, despatch: despatch?.value, invoice: invoice?.value },
      senderName: sender.name,
      senderEndpoint: sender.endpoint,
      recipientName: recipient.name,
      recipientEndpoint: recipient.endpoint,
      currency: currency?.value ?? "GBP",
      lines: lines.length > 0 ? lines : [{ id: "1", itemName: "", itemIdentifier: "", quantity: "1", unitCode: "EA", unitPrice: "0.00" }],
    },
    sources: documents.map((document) => document.source),
    provenance,
    warnings: [...new Set(warnings)],
    requiresCommercialReview: normalizedLines.some((line) => line.quantity !== undefined || line.amount !== undefined || line.unitPrice !== undefined),
  }
}

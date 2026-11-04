import {
  entries,
  first,
  money,
  party,
  quantity,
  record,
  text,
  texts,
  type JsonRecord,
  type UblMoney,
  type UblParty,
  type UblQuantity,
} from "./ubl-common"
import { hasValidSscc } from "./ubl-authoring"

export type OperationalDocumentKind =
  | "ORDER_RESPONSE"
  | "DESPATCH_ADVICE"
  | "REMITTANCE_ADVICE"
  | "RECEIPT_ADVICE"

interface DocumentDefinition {
  root: string
  namespace: string
  title: string
  referenceKey?: string
  statusKey?: string
  partyKeys: Array<{ key: string; label: string }>
  lineKey: string
}

const definitions: Record<OperationalDocumentKind, DocumentDefinition> = {
  ORDER_RESPONSE: {
    root: "OrderResponse",
    namespace: "urn:oasis:names:specification:ubl:schema:xsd:OrderResponse-2",
    title: "Order response",
    referenceKey: "OrderReference",
    partyKeys: [
      { key: "SellerSupplierParty", label: "Seller" },
      { key: "BuyerCustomerParty", label: "Buyer" },
    ],
    lineKey: "OrderLine",
  },
  DESPATCH_ADVICE: {
    root: "DespatchAdvice",
    namespace: "urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2",
    title: "Advance shipping notice",
    referenceKey: "OrderReference",
    statusKey: "DocumentStatusCode",
    partyKeys: [
      { key: "DespatchSupplierParty", label: "Despatch supplier" },
      { key: "DeliveryCustomerParty", label: "Delivery customer" },
    ],
    lineKey: "DespatchLine",
  },
  REMITTANCE_ADVICE: {
    root: "RemittanceAdvice",
    namespace: "urn:oasis:names:specification:ubl:schema:xsd:RemittanceAdvice-2",
    title: "Remittance advice",
    partyKeys: [
      { key: "AccountingCustomerParty", label: "Customer" },
      { key: "AccountingSupplierParty", label: "Supplier" },
      { key: "PayeeParty", label: "Payee" },
    ],
    lineKey: "RemittanceAdviceLine",
  },
  RECEIPT_ADVICE: {
    root: "ReceiptAdvice",
    namespace: "urn:oasis:names:specification:ubl:schema:xsd:ReceiptAdvice-2",
    title: "Receipt advice",
    referenceKey: "DespatchDocumentReference",
    partyKeys: [
      { key: "DeliveryCustomerParty", label: "Receiving customer" },
      { key: "DespatchSupplierParty", label: "Despatch supplier" },
    ],
    lineKey: "ReceiptLine",
  },
}

export interface OperationalParty {
  label: string
  party: UblParty
}

export interface OperationalLine {
  id: string
  status?: string
  name?: string
  description?: string
  quantity?: UblQuantity
  secondaryQuantity?: UblQuantity
  debit?: UblMoney
  credit?: UblMoney
  balance?: UblMoney
  reference?: string
}

export interface PopulatedUblField {
  path: string
  value: string
}

export interface OperationalDocumentViewModel {
  kind: OperationalDocumentKind
  title: string
  id: string
  issueDate: string
  issueTime?: string
  reference?: string
  status?: string
  currency?: string
  notes: string[]
  parties: OperationalParty[]
  lines: OperationalLine[]
  totals: Array<{ label: string; amount: UblMoney }>
  handlingUnitCount: number
  printableLabelCount: number
  populatedFields: PopulatedUblField[]
}

export type OperationalDocumentParseResult =
  | { ok: true; document: OperationalDocumentViewModel }
  | { ok: false; reason: string }

function nestedId(parent: unknown, key: string) {
  return text(first(parent, key), "ID")
}

function lineItem(line: JsonRecord) {
  return first(line, "LineItem") ?? line
}

function parseLine(line: JsonRecord, index: number): OperationalLine {
  const source = lineItem(line)
  const item = first(source, "Item")
  const billingReference = first(line, "BillingReference")
  const invoiceReference = first(billingReference, "InvoiceDocumentReference")
  return {
    id: text(source, "ID") ?? String(index + 1),
    status: text(source, "LineStatusCode"),
    name: text(item, "Name"),
    description: text(item, "Description") ?? texts(line, "Note")[0],
    quantity: quantity(source, "DeliveredQuantity")
      ?? quantity(source, "ReceivedQuantity")
      ?? quantity(source, "Quantity"),
    secondaryQuantity: quantity(source, "BackorderQuantity")
      ?? quantity(source, "ShortQuantity")
      ?? quantity(source, "RejectedQuantity"),
    debit: money(source, "DebitLineAmount"),
    credit: money(source, "CreditLineAmount"),
    balance: money(source, "BalanceAmount"),
    reference: text(first(source, "OrderLineReference"), "LineID")
      ?? text(invoiceReference, "ID"),
  }
}

function collectFields(value: unknown, path: string, output: PopulatedUblField[]) {
  if (value === null || value === undefined) return
  if (Array.isArray(value)) {
    value.forEach((item, index) => collectFields(item, `${path}[${index + 1}]`, output))
    return
  }
  if (typeof value !== "object") {
    const rendered = String(value).trim()
    if (rendered) output.push({ path, value: rendered })
    return
  }
  Object.entries(value as JsonRecord).forEach(([key, item]) => {
    if (key === "_") collectFields(item, path, output)
    else collectFields(item, path ? `${path}.${key}` : key, output)
  })
}

function total(document: JsonRecord, key: string, label: string) {
  const amount = money(document, key)
  return amount ? [{ label, amount }] : []
}

export function parseOperationalDocument(
  value: unknown,
  kind: OperationalDocumentKind,
): OperationalDocumentParseResult {
  const definition = definitions[kind]
  const envelope = record(value)
  if (!envelope || envelope._D !== definition.namespace) {
    return { ok: false, reason: `The JSON representation is not a UBL ${definition.root}.` }
  }
  const document = first(envelope, definition.root)
  const id = text(document, "ID")
  const issueDate = text(document, "IssueDate")
  if (!document || !id || !issueDate) {
    return { ok: false, reason: `The UBL ${definition.root} is missing required document fields.` }
  }

  const fields: PopulatedUblField[] = []
  const handlingUnits = entries(first(document, "Shipment"), "TransportHandlingUnit")
  collectFields(document, definition.root, fields)
  return {
    ok: true,
    document: {
      kind,
      title: definition.title,
      id,
      issueDate,
      issueTime: text(document, "IssueTime"),
      reference: definition.referenceKey ? nestedId(document, definition.referenceKey) : undefined,
      status: definition.statusKey ? text(document, definition.statusKey) : undefined,
      currency: text(document, "DocumentCurrencyCode"),
      notes: texts(document, "Note"),
      parties: definition.partyKeys.flatMap(({ key, label }) => {
        const parsed = party(first(document, key))
        return parsed ? [{ label, party: parsed }] : []
      }),
      lines: entries(document, definition.lineKey).map(parseLine),
      totals: [
        ...total(document, "TotalDebitAmount", "Total debit"),
        ...total(document, "TotalCreditAmount", "Total credit"),
        ...total(document, "TotalPaymentAmount", "Total payment"),
      ],
      handlingUnitCount: handlingUnits.length,
      printableLabelCount: handlingUnits.filter((unit) => hasValidSscc(text(unit, "TraceID") ?? "")).length,
      populatedFields: fields,
    },
  }
}

export function parseOperationalDocumentJson(
  value: string,
  kind: OperationalDocumentKind,
): OperationalDocumentParseResult {
  try {
    return parseOperationalDocument(JSON.parse(value), kind)
  } catch {
    return { ok: false, reason: "The JSON representation could not be read." }
  }
}


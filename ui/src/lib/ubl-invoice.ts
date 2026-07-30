import {
  entries,
  first,
  itemIdentifier,
  monetaryTotals,
  money,
  party,
  quantity,
  record,
  text,
  texts,
  type JsonRecord,
  type UblMoney,
  type UblMonetaryTotals,
  type UblParty,
  type UblQuantity,
} from "./ubl-common"

const UBL_INVOICE_NAMESPACE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"

export interface InvoicePaymentMeans {
  code: string
  name?: string
  dueDate?: string
  paymentId?: string
  accountId?: string
  accountName?: string
}

export interface InvoicePaymentTerms {
  notes: string[]
  dueDate?: string
  amount?: UblMoney
  percent?: string
}

export interface InvoiceTaxSubtotal {
  taxAmount: UblMoney
  taxableAmount?: UblMoney
  percent?: string
  categoryId?: string
  scheme?: string
  exemptionReason?: string
}

export interface InvoiceTaxTotal {
  taxAmount: UblMoney
  subtotals: InvoiceTaxSubtotal[]
}

export interface InvoiceLineViewModel {
  id: string
  notes: string[]
  name?: string
  description?: string
  sellerItemId?: string
  buyerItemId?: string
  standardItemId?: string
  standardItemScheme?: string
  orderLineReference?: string
  quantity?: UblQuantity
  price?: UblMoney
  baseQuantity?: UblQuantity
  lineExtension: UblMoney
  taxAmount?: UblMoney
  taxPercent?: string
}

export interface UblInvoiceViewModel {
  id: string
  issueDate: string
  issueTime?: string
  dueDate?: string
  taxPointDate?: string
  invoiceType?: string
  buyerReference?: string
  orderReference?: string
  currency?: string
  taxCurrency?: string
  ublVersion?: string
  customizationId?: string
  profileId?: string
  notes: string[]
  supplier: UblParty
  customer: UblParty
  paymentMeans: InvoicePaymentMeans[]
  paymentTerms: InvoicePaymentTerms[]
  taxes: InvoiceTaxTotal[]
  lines: InvoiceLineViewModel[]
  totals: UblMonetaryTotals & { payable: UblMoney }
}

export type UblInvoiceParseResult =
  | { ok: true; invoice: UblInvoiceViewModel }
  | { ok: false; reason: string }

function paymentMeans(value: JsonRecord): InvoicePaymentMeans | undefined {
  const codeValue = first(value, "PaymentMeansCode")
  const code = text(value, "PaymentMeansCode")
  if (!code) return undefined
  const account = first(value, "PayeeFinancialAccount")
  return {
    code,
    name: typeof codeValue?.name === "string" ? codeValue.name : undefined,
    dueDate: text(value, "PaymentDueDate"),
    paymentId: text(value, "PaymentID"),
    accountId: text(account, "ID"),
    accountName: text(account, "Name"),
  }
}

function paymentTerms(value: JsonRecord): InvoicePaymentTerms {
  const percent = text(value, "PaymentPercent")
    ?? text(value, "SettlementDiscountPercent")
    ?? text(value, "PenaltySurchargePercent")
  return {
    notes: texts(value, "Note"),
    dueDate: text(value, "PaymentDueDate"),
    amount: money(value, "Amount"),
    percent,
  }
}

function taxSubtotal(value: JsonRecord): InvoiceTaxSubtotal | undefined {
  const taxAmount = money(value, "TaxAmount")
  const category = first(value, "TaxCategory")
  const scheme = first(category, "TaxScheme")
  if (!taxAmount || !category) return undefined
  return {
    taxAmount,
    taxableAmount: money(value, "TaxableAmount"),
    percent: text(value, "Percent") ?? text(category, "Percent"),
    categoryId: text(category, "ID") ?? text(category, "Name"),
    scheme: text(scheme, "ID") ?? text(scheme, "Name"),
    exemptionReason: text(category, "TaxExemptionReason"),
  }
}

function taxTotal(value: JsonRecord): InvoiceTaxTotal | undefined {
  const taxAmount = money(value, "TaxAmount")
  if (!taxAmount) return undefined
  return {
    taxAmount,
    subtotals: entries(value, "TaxSubtotal").flatMap((subtotal) => {
      const parsed = taxSubtotal(subtotal)
      return parsed ? [parsed] : []
    }),
  }
}

function invoiceLine(value: JsonRecord): InvoiceLineViewModel | undefined {
  const id = text(value, "ID")
  const lineExtension = money(value, "LineExtensionAmount")
  const item = first(value, "Item")
  if (!id || !lineExtension || !item) return undefined
  const priceValue = first(value, "Price")
  const sellerId = itemIdentifier(item, "SellersItemIdentification")
  const buyerId = itemIdentifier(item, "BuyersItemIdentification")
  const standardId = itemIdentifier(item, "StandardItemIdentification")
  const firstTax = first(value, "TaxTotal")
  const firstSubtotal = first(firstTax, "TaxSubtotal")
  const category = first(firstSubtotal, "TaxCategory")
  const orderLineReference = first(value, "OrderLineReference")

  return {
    id,
    notes: texts(value, "Note"),
    name: text(item, "Name"),
    description: text(item, "Description"),
    sellerItemId: sellerId.value,
    buyerItemId: buyerId.value,
    standardItemId: standardId.value,
    standardItemScheme: standardId.scheme,
    orderLineReference: text(first(orderLineReference, "LineReference"), "LineID")
      ?? text(orderLineReference, "LineID"),
    quantity: quantity(value, "InvoicedQuantity"),
    price: money(priceValue, "PriceAmount"),
    baseQuantity: quantity(priceValue, "BaseQuantity"),
    lineExtension,
    taxAmount: money(firstTax, "TaxAmount"),
    taxPercent: text(firstSubtotal, "Percent") ?? text(category, "Percent"),
  }
}

export function parseUblInvoice(value: unknown): UblInvoiceParseResult {
  const envelope = record(value)
  if (!envelope || envelope._D !== UBL_INVOICE_NAMESPACE) {
    return { ok: false, reason: "The JSON representation is not a UBL Invoice." }
  }

  const invoice = first(envelope, "Invoice")
  const id = text(invoice, "ID")
  const issueDate = text(invoice, "IssueDate")
  const supplier = party(first(invoice, "AccountingSupplierParty"))
  const customer = party(first(invoice, "AccountingCustomerParty"))
  const totals = monetaryTotals(invoice, "LegalMonetaryTotal")
  const rawLines = entries(invoice, "InvoiceLine")
  const lines = rawLines.flatMap((line) => {
    const parsed = invoiceLine(line)
    return parsed ? [parsed] : []
  })

  if (!invoice || !id || !issueDate || !supplier || !customer || !totals?.payable || rawLines.length === 0) {
    return { ok: false, reason: "The UBL Invoice is missing required invoice fields." }
  }
  if (lines.length !== rawLines.length) {
    return { ok: false, reason: "One or more UBL Invoice lines are missing required fields." }
  }

  return {
    ok: true,
    invoice: {
      id,
      issueDate,
      issueTime: text(invoice, "IssueTime"),
      dueDate: text(invoice, "DueDate"),
      taxPointDate: text(invoice, "TaxPointDate"),
      invoiceType: text(invoice, "InvoiceTypeCode"),
      buyerReference: text(invoice, "BuyerReference"),
      orderReference: text(first(invoice, "OrderReference"), "ID"),
      currency: text(invoice, "DocumentCurrencyCode"),
      taxCurrency: text(invoice, "TaxCurrencyCode"),
      ublVersion: text(invoice, "UBLVersionID"),
      customizationId: text(invoice, "CustomizationID"),
      profileId: text(invoice, "ProfileID"),
      notes: texts(invoice, "Note"),
      supplier,
      customer,
      paymentMeans: entries(invoice, "PaymentMeans").flatMap((means) => {
        const parsed = paymentMeans(means)
        return parsed ? [parsed] : []
      }),
      paymentTerms: entries(invoice, "PaymentTerms").map(paymentTerms),
      taxes: entries(invoice, "TaxTotal").flatMap((tax) => {
        const parsed = taxTotal(tax)
        return parsed ? [parsed] : []
      }),
      lines,
      totals: { ...totals, payable: totals.payable },
    },
  }
}

export function parseUblInvoiceJson(value: string): UblInvoiceParseResult {
  try {
    return parseUblInvoice(JSON.parse(value))
  } catch {
    return { ok: false, reason: "The JSON representation could not be read." }
  }
}

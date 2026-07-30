const UBL_ORDER_NAMESPACE = "urn:oasis:names:specification:ubl:schema:xsd:Order-2"

type JsonRecord = Record<string, unknown>

export interface OrderParty {
  name?: string
  endpointId?: string
  endpointScheme?: string
  identifiers: string[]
  legalName?: string
  companyId?: string
  address?: OrderAddress
}

export interface OrderAddress {
  lines: string[]
  city?: string
  region?: string
  postalCode?: string
  country?: string
}

export interface OrderDelivery {
  id?: string
  requestedStartDate?: string
  requestedEndDate?: string
  promisedStartDate?: string
  promisedEndDate?: string
  locationId?: string
  locationName?: string
  address?: OrderAddress
}

export interface OrderMoney {
  value: number
  currency?: string
}

export interface OrderQuantity {
  value: number
  unit?: string
}

export interface OrderLineViewModel {
  id: string
  salesOrderId?: string
  status?: string
  notes: string[]
  name?: string
  description?: string
  sellerItemId?: string
  buyerItemId?: string
  standardItemId?: string
  standardItemScheme?: string
  brandName?: string
  modelName?: string
  quantity?: OrderQuantity
  price?: OrderMoney
  baseQuantity?: OrderQuantity
  lineExtension?: OrderMoney
  totalTax?: OrderMoney
}

export interface OrderTotals {
  lineExtension?: OrderMoney
  allowance?: OrderMoney
  charge?: OrderMoney
  taxExclusive?: OrderMoney
  taxInclusive?: OrderMoney
  prepaid?: OrderMoney
  rounding?: OrderMoney
  payable?: OrderMoney
}

export interface UblOrderViewModel {
  id: string
  salesOrderId?: string
  customerReference?: string
  orderType?: string
  issueDate: string
  issueTime?: string
  ublVersion?: string
  customizationId?: string
  profileId?: string
  currency?: string
  notes: string[]
  buyer: OrderParty
  seller: OrderParty
  deliveries: OrderDelivery[]
  lines: OrderLineViewModel[]
  totals?: OrderTotals
}

export type UblOrderParseResult =
  | { ok: true; order: UblOrderViewModel }
  | { ok: false; reason: string }

function record(value: unknown): JsonRecord | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as JsonRecord
    : undefined
}

function entries(parent: unknown, key: string): JsonRecord[] {
  const value = record(parent)?.[key]
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    const itemRecord = record(item)
    return itemRecord ? [itemRecord] : []
  })
}

function first(parent: unknown, key: string) {
  return entries(parent, key)[0]
}

function scalar(parent: unknown, key: string): unknown {
  return first(parent, key)?.["_"]
}

function text(parent: unknown, key: string): string | undefined {
  const value = scalar(parent, key)
  if (typeof value === "string") return value.trim() || undefined
  if (typeof value === "number" || typeof value === "boolean") return String(value)
  return undefined
}

function numberValue(value: unknown): number | undefined {
  const parsed = typeof value === "number"
    ? value
    : typeof value === "string" && value.trim()
      ? Number(value)
      : Number.NaN
  return Number.isFinite(parsed) ? parsed : undefined
}

function money(parent: unknown, key: string): OrderMoney | undefined {
  const amount = first(parent, key)
  const value = numberValue(amount?.["_"])
  if (value === undefined) return undefined
  return {
    value,
    currency: typeof amount?.currencyID === "string" ? amount.currencyID : undefined,
  }
}

function quantity(parent: unknown, key: string): OrderQuantity | undefined {
  const item = first(parent, key)
  const value = numberValue(item?.["_"])
  if (value === undefined) return undefined
  return {
    value,
    unit: typeof item?.unitCode === "string" ? item.unitCode : undefined,
  }
}

function identifier(parent: unknown, key: string) {
  const value = first(parent, key)
  return {
    value: text(parent, key),
    scheme: typeof value?.schemeID === "string" ? value.schemeID : undefined,
  }
}

function address(value: unknown): OrderAddress | undefined {
  const source = record(value)
  if (!source) return undefined
  const country = first(source, "Country")
  const lines = entries(source, "AddressLine")
    .flatMap((line) => text(line, "Line") ? [text(line, "Line")!] : [])
  const street = text(source, "StreetName")
  const additionalStreet = text(source, "AdditionalStreetName")
  const number = text(source, "BuildingNumber")
  const streetLine = [number, street].filter(Boolean).join(" ")
  const allLines = [
    streetLine || undefined,
    additionalStreet,
    ...lines,
  ].filter((line): line is string => Boolean(line))
  const result: OrderAddress = {
    lines: allLines,
    city: text(source, "CityName"),
    region: text(source, "CountrySubentity"),
    postalCode: text(source, "PostalZone"),
    country: text(country, "Name") ?? text(country, "IdentificationCode"),
  }
  return Object.values(result).some((item) => Array.isArray(item) ? item.length > 0 : Boolean(item))
    ? result
    : undefined
}

function party(container: unknown): OrderParty | undefined {
  const partyValue = first(container, "Party")
  if (!partyValue) return undefined
  const endpoint = identifier(partyValue, "EndpointID")
  const partyName = first(partyValue, "PartyName")
  const legalEntity = first(partyValue, "PartyLegalEntity")
  return {
    name: text(partyName, "Name") ?? text(legalEntity, "RegistrationName"),
    endpointId: endpoint.value,
    endpointScheme: endpoint.scheme,
    identifiers: entries(partyValue, "PartyIdentification")
      .flatMap((item) => text(item, "ID") ? [text(item, "ID")!] : []),
    legalName: text(legalEntity, "RegistrationName"),
    companyId: text(legalEntity, "CompanyID"),
    address: address(first(partyValue, "PostalAddress")),
  }
}

function period(value: unknown) {
  return {
    start: text(value, "StartDate"),
    end: text(value, "EndDate"),
  }
}

function delivery(value: JsonRecord): OrderDelivery {
  const requested = period(first(value, "RequestedDeliveryPeriod"))
  const promised = period(first(value, "PromisedDeliveryPeriod"))
  const location = first(value, "DeliveryLocation")
  return {
    id: text(value, "ID"),
    requestedStartDate: requested.start,
    requestedEndDate: requested.end,
    promisedStartDate: promised.start,
    promisedEndDate: promised.end,
    locationId: text(location, "ID"),
    locationName: text(location, "Name"),
    address: address(first(location, "Address") ?? first(value, "DeliveryAddress")),
  }
}

function itemIdentifier(item: unknown, key: string) {
  return identifier(first(item, key), "ID")
}

function orderLine(value: JsonRecord): OrderLineViewModel | undefined {
  const lineItem = first(value, "LineItem")
  const id = text(lineItem, "ID")
  const item = first(lineItem, "Item")
  if (!lineItem || !id || !item) return undefined
  const sellerId = itemIdentifier(item, "SellersItemIdentification")
  const buyerId = itemIdentifier(item, "BuyersItemIdentification")
  const standardId = itemIdentifier(item, "StandardItemIdentification")
  const priceValue = first(lineItem, "Price")

  return {
    id,
    salesOrderId: text(lineItem, "SalesOrderID"),
    status: text(lineItem, "LineStatusCode"),
    notes: entries(value, "Note")
      .map((note) => String(note["_"] ?? "").trim())
      .filter(Boolean),
    name: text(item, "Name"),
    description: text(item, "Description"),
    sellerItemId: sellerId.value,
    buyerItemId: buyerId.value,
    standardItemId: standardId.value,
    standardItemScheme: standardId.scheme,
    brandName: text(item, "BrandName"),
    modelName: text(item, "ModelName"),
    quantity: quantity(lineItem, "Quantity"),
    price: money(priceValue, "PriceAmount"),
    baseQuantity: quantity(priceValue, "BaseQuantity"),
    lineExtension: money(lineItem, "LineExtensionAmount"),
    totalTax: money(lineItem, "TotalTaxAmount"),
  }
}

function totals(value: unknown): OrderTotals | undefined {
  const source = first(value, "AnticipatedMonetaryTotal")
  if (!source) return undefined
  const result: OrderTotals = {
    lineExtension: money(source, "LineExtensionAmount"),
    allowance: money(source, "AllowanceTotalAmount"),
    charge: money(source, "ChargeTotalAmount"),
    taxExclusive: money(source, "TaxExclusiveAmount"),
    taxInclusive: money(source, "TaxInclusiveAmount"),
    prepaid: money(source, "PrepaidAmount"),
    rounding: money(source, "PayableRoundingAmount"),
    payable: money(source, "PayableAmount"),
  }
  return Object.values(result).some(Boolean) ? result : undefined
}

export function parseUblOrder(value: unknown): UblOrderParseResult {
  const envelope = record(value)
  if (!envelope || envelope._D !== UBL_ORDER_NAMESPACE) {
    return { ok: false, reason: "The JSON representation is not a UBL Order." }
  }

  const order = first(envelope, "Order")
  const id = text(order, "ID")
  const issueDate = text(order, "IssueDate")
  const buyer = party(first(order, "BuyerCustomerParty"))
  const seller = party(first(order, "SellerSupplierParty"))
  const rawLines = entries(order, "OrderLine")
  const lines = rawLines.flatMap((line) => {
    const parsed = orderLine(line)
    return parsed ? [parsed] : []
  })

  if (!order || !id || !issueDate || !buyer || !seller || rawLines.length === 0) {
    return { ok: false, reason: "The UBL Order is missing required order fields." }
  }
  if (lines.length !== rawLines.length) {
    return { ok: false, reason: "One or more UBL Order lines are missing their required line item fields." }
  }

  return {
    ok: true,
    order: {
      id,
      salesOrderId: text(order, "SalesOrderID"),
      customerReference: text(order, "CustomerReference"),
      orderType: text(order, "OrderTypeCode"),
      issueDate,
      issueTime: text(order, "IssueTime"),
      ublVersion: text(order, "UBLVersionID"),
      customizationId: text(order, "CustomizationID"),
      profileId: text(order, "ProfileID"),
      currency: text(order, "DocumentCurrencyCode"),
      notes: entries(order, "Note")
        .map((note) => String(note["_"] ?? "").trim())
        .filter(Boolean),
      buyer,
      seller,
      deliveries: entries(order, "Delivery").map(delivery),
      lines,
      totals: totals(order),
    },
  }
}

export function parseUblOrderJson(value: string): UblOrderParseResult {
  try {
    return parseUblOrder(JSON.parse(value))
  } catch {
    return { ok: false, reason: "The JSON representation could not be read." }
  }
}


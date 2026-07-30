import {
  address,
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
  type UblAddress,
  type UblMoney,
  type UblMonetaryTotals,
  type UblParty,
  type UblQuantity,
} from "./ubl-common"

const UBL_ORDER_NAMESPACE = "urn:oasis:names:specification:ubl:schema:xsd:Order-2"

export type OrderParty = UblParty
export type OrderAddress = UblAddress
export type OrderMoney = UblMoney
export type OrderQuantity = UblQuantity
export type OrderTotals = UblMonetaryTotals

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
    notes: texts(value, "Note"),
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
  return monetaryTotals(value, "AnticipatedMonetaryTotal")
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
      notes: texts(order, "Note"),
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

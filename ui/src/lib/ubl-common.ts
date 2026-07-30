export type JsonRecord = Record<string, unknown>

export interface UblAddress {
  lines: string[]
  city?: string
  region?: string
  postalCode?: string
  country?: string
}

export interface UblParty {
  name?: string
  endpointId?: string
  endpointScheme?: string
  identifiers: string[]
  legalName?: string
  companyId?: string
  taxIds: Array<{ id: string; scheme?: string }>
  address?: UblAddress
}

export interface UblMoney {
  value: number
  currency?: string
}

export interface UblQuantity {
  value: number
  unit?: string
}

export interface UblMonetaryTotals {
  lineExtension?: UblMoney
  allowance?: UblMoney
  charge?: UblMoney
  taxExclusive?: UblMoney
  taxInclusive?: UblMoney
  prepaid?: UblMoney
  rounding?: UblMoney
  payable?: UblMoney
}

export function record(value: unknown): JsonRecord | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as JsonRecord
    : undefined
}

export function entries(parent: unknown, key: string): JsonRecord[] {
  const value = record(parent)?.[key]
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    const itemRecord = record(item)
    return itemRecord ? [itemRecord] : []
  })
}

export function first(parent: unknown, key: string) {
  return entries(parent, key)[0]
}

export function text(parent: unknown, key: string): string | undefined {
  const value = first(parent, key)?.["_"]
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

export function money(parent: unknown, key: string): UblMoney | undefined {
  const amount = first(parent, key)
  const value = numberValue(amount?.["_"])
  if (value === undefined) return undefined
  return {
    value,
    currency: typeof amount?.currencyID === "string" ? amount.currencyID : undefined,
  }
}

export function quantity(parent: unknown, key: string): UblQuantity | undefined {
  const item = first(parent, key)
  const value = numberValue(item?.["_"])
  if (value === undefined) return undefined
  return {
    value,
    unit: typeof item?.unitCode === "string" ? item.unitCode : undefined,
  }
}

export function identifier(parent: unknown, key: string) {
  const value = first(parent, key)
  return {
    value: text(parent, key),
    scheme: typeof value?.schemeID === "string" ? value.schemeID : undefined,
  }
}

export function address(value: unknown): UblAddress | undefined {
  const source = record(value)
  if (!source) return undefined
  const country = first(source, "Country")
  const addressLines = entries(source, "AddressLine")
    .flatMap((line) => text(line, "Line") ? [text(line, "Line")!] : [])
  const streetLine = [text(source, "BuildingNumber"), text(source, "StreetName")]
    .filter(Boolean)
    .join(" ")
  const result: UblAddress = {
    lines: [streetLine || undefined, text(source, "AdditionalStreetName"), ...addressLines]
      .filter((line): line is string => Boolean(line)),
    city: text(source, "CityName"),
    region: text(source, "CountrySubentity"),
    postalCode: text(source, "PostalZone"),
    country: text(country, "Name") ?? text(country, "IdentificationCode"),
  }
  return Object.values(result).some((item) => Array.isArray(item) ? item.length > 0 : Boolean(item))
    ? result
    : undefined
}

export function party(container: unknown): UblParty | undefined {
  const partyValue = first(container, "Party")
  if (!partyValue) return undefined
  const endpoint = identifier(partyValue, "EndpointID")
  const partyName = first(partyValue, "PartyName")
  const legalEntity = first(partyValue, "PartyLegalEntity")
  const taxIds = entries(partyValue, "PartyTaxScheme").flatMap((taxScheme) => {
    const id = text(taxScheme, "CompanyID")
    const scheme = text(first(taxScheme, "TaxScheme"), "ID")
    return id ? [{ id, scheme }] : []
  })
  return {
    name: text(partyName, "Name") ?? text(legalEntity, "RegistrationName"),
    endpointId: endpoint.value,
    endpointScheme: endpoint.scheme,
    identifiers: entries(partyValue, "PartyIdentification")
      .flatMap((item) => text(item, "ID") ? [text(item, "ID")!] : []),
    legalName: text(legalEntity, "RegistrationName"),
    companyId: text(legalEntity, "CompanyID"),
    taxIds,
    address: address(first(partyValue, "PostalAddress")),
  }
}

export function itemIdentifier(item: unknown, key: string) {
  return identifier(first(item, key), "ID")
}

export function texts(parent: unknown, key: string): string[] {
  return entries(parent, key)
    .map((item) => String(item["_"] ?? "").trim())
    .filter(Boolean)
}

export function monetaryTotals(parent: unknown, key: string): UblMonetaryTotals | undefined {
  const source = first(parent, key)
  if (!source) return undefined
  const result: UblMonetaryTotals = {
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

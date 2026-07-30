import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..")
const schemaRoot = resolve(uiRoot, "../ubl-source/json-schema")

async function readSchema(relativePath) {
  return JSON.parse(await readFile(resolve(schemaRoot, relativePath), "utf8"))
}

function requireProperties(definition, names, label) {
  assert.ok(definition, `${label} definition is missing`)
  for (const name of names) {
    assert.ok(definition.properties?.[name], `${label}.${name} is missing`)
  }
}

function requireArrayProperty(definition, name, label) {
  const property = definition.properties?.[name]
  assert.equal(property?.type, "array", `${label}.${name} must remain an array`)
  assert.equal(property?.minItems, 1, `${label}.${name} must require at least one item`)
}

const [invoiceSchema, aggregateSchema, unqualifiedSchema, cctsSchema] = await Promise.all([
  readSchema("maindoc/UBL-Invoice-2.1.json"),
  readSchema("common/UBL-CommonAggregateComponents-2.1.json"),
  readSchema("common/BDNDR-UnqualifiedDataTypes-1.1.json"),
  readSchema("common/BDNDR-CCTS_CCT_SchemaModule-1.1.json"),
])

assert.deepEqual(invoiceSchema.required, ["Invoice"], "UBL Invoice envelope requirements changed")
assert.equal(invoiceSchema.properties.Invoice.type, "array")
assert.equal(invoiceSchema.properties.Invoice.minItems, 1)
assert.equal(invoiceSchema.properties.Invoice.maxItems, 1)

const invoice = invoiceSchema.definitions.Invoice
for (const required of [
  "ID",
  "IssueDate",
  "AccountingSupplierParty",
  "AccountingCustomerParty",
  "LegalMonetaryTotal",
  "InvoiceLine",
]) {
  assert.ok(invoice.required.includes(required), `Invoice.${required} must remain required`)
  requireArrayProperty(invoice, required, "Invoice")
}
requireProperties(invoice, [
  "DueDate",
  "TaxPointDate",
  "InvoiceTypeCode",
  "BuyerReference",
  "OrderReference",
  "DocumentCurrencyCode",
  "TaxCurrencyCode",
  "UBLVersionID",
  "CustomizationID",
  "ProfileID",
  "Note",
  "PaymentMeans",
  "PaymentTerms",
  "TaxTotal",
], "Invoice")

const aggregateMappings = {
  InvoiceLine: [
    "ID",
    "Note",
    "InvoicedQuantity",
    "LineExtensionAmount",
    "OrderLineReference",
    "TaxTotal",
    "Item",
    "Price",
  ],
  CustomerParty: ["Party"],
  SupplierParty: ["Party"],
  Party: [
    "EndpointID",
    "PartyIdentification",
    "PartyName",
    "PartyTaxScheme",
    "PartyLegalEntity",
    "PostalAddress",
  ],
  PaymentMeans: [
    "PaymentMeansCode",
    "PaymentDueDate",
    "PaymentID",
    "PayeeFinancialAccount",
  ],
  PaymentTerms: ["Note", "PaymentDueDate", "Amount", "PaymentPercent"],
  TaxTotal: ["TaxAmount", "TaxSubtotal"],
  TaxSubtotal: ["TaxableAmount", "TaxAmount", "Percent", "TaxCategory"],
  TaxCategory: ["ID", "Name", "Percent", "TaxExemptionReason", "TaxScheme"],
  MonetaryTotal: [
    "LineExtensionAmount",
    "AllowanceTotalAmount",
    "ChargeTotalAmount",
    "TaxExclusiveAmount",
    "TaxInclusiveAmount",
    "PrepaidAmount",
    "PayableRoundingAmount",
    "PayableAmount",
  ],
  Item: [
    "Name",
    "Description",
    "SellersItemIdentification",
    "BuyersItemIdentification",
    "StandardItemIdentification",
  ],
  Price: ["PriceAmount", "BaseQuantity"],
  DocumentReference: ["ID"],
}

for (const [definitionName, properties] of Object.entries(aggregateMappings)) {
  requireProperties(aggregateSchema.definitions[definitionName], properties, definitionName)
}

assert.ok(aggregateSchema.definitions.InvoiceLine.required.includes("ID"))
assert.ok(aggregateSchema.definitions.InvoiceLine.required.includes("LineExtensionAmount"))
assert.ok(aggregateSchema.definitions.InvoiceLine.required.includes("Item"))
assert.ok(aggregateSchema.definitions.MonetaryTotal.required.includes("PayableAmount"))
assert.ok(aggregateSchema.definitions.TaxTotal.required.includes("TaxAmount"))
assert.ok(aggregateSchema.definitions.TaxSubtotal.required.includes("TaxAmount"))
assert.ok(aggregateSchema.definitions.TaxSubtotal.required.includes("TaxCategory"))
assert.ok(aggregateSchema.definitions.Price.required.includes("PriceAmount"))

for (const typeName of ["AmountType", "DateType", "TimeType"]) {
  assert.ok(unqualifiedSchema.definitions[typeName].required.includes("_"), `${typeName} must retain the _ value`)
}
assert.ok(unqualifiedSchema.definitions.AmountType.required.includes("currencyID"))
for (const typeName of ["CodeType", "IdentifierType", "QuantityType", "TextType"]) {
  assert.ok(cctsSchema.definitions[typeName].required.includes("_"), `${typeName} must retain the _ value`)
}
assert.ok(cctsSchema.definitions.QuantityType.properties.unitCode)
assert.ok(cctsSchema.definitions.IdentifierType.properties.schemeID)

console.log("UBL Invoice UI contract matches ubl-source")

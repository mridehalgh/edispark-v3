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

const [orderSchema, aggregateSchema, unqualifiedSchema, cctsSchema] = await Promise.all([
  readSchema("maindoc/UBL-Order-2.1.json"),
  readSchema("common/UBL-CommonAggregateComponents-2.1.json"),
  readSchema("common/BDNDR-UnqualifiedDataTypes-1.1.json"),
  readSchema("common/BDNDR-CCTS_CCT_SchemaModule-1.1.json"),
])

assert.deepEqual(orderSchema.required, ["Order"], "UBL Order envelope requirements changed")
assert.equal(orderSchema.properties.Order.type, "array")
assert.equal(orderSchema.properties.Order.minItems, 1)
assert.equal(orderSchema.properties.Order.maxItems, 1)

const order = orderSchema.definitions.Order
for (const required of ["ID", "IssueDate", "BuyerCustomerParty", "SellerSupplierParty", "OrderLine"]) {
  assert.ok(order.required.includes(required), `Order.${required} must remain required`)
  requireArrayProperty(order, required, "Order")
}
requireProperties(order, [
  "SalesOrderID",
  "CustomerReference",
  "OrderTypeCode",
  "IssueTime",
  "UBLVersionID",
  "CustomizationID",
  "ProfileID",
  "DocumentCurrencyCode",
  "Note",
  "Delivery",
  "AnticipatedMonetaryTotal",
], "Order")

const aggregateMappings = {
  OrderLine: ["LineItem", "Note"],
  LineItem: [
    "ID",
    "SalesOrderID",
    "LineStatusCode",
    "Quantity",
    "LineExtensionAmount",
    "TotalTaxAmount",
    "Price",
    "Item",
  ],
  CustomerParty: ["Party"],
  SupplierParty: ["Party"],
  Party: ["EndpointID", "PartyIdentification", "PartyName", "PartyLegalEntity", "PostalAddress"],
  Delivery: ["ID", "RequestedDeliveryPeriod", "PromisedDeliveryPeriod", "DeliveryLocation", "DeliveryAddress"],
  Period: ["StartDate", "EndDate"],
  Location: ["ID", "Name", "Address"],
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
    "BrandName",
    "ModelName",
  ],
  Price: ["PriceAmount", "BaseQuantity"],
}

for (const [definitionName, properties] of Object.entries(aggregateMappings)) {
  requireProperties(aggregateSchema.definitions[definitionName], properties, definitionName)
}

assert.ok(aggregateSchema.definitions.OrderLine.required.includes("LineItem"))
assert.ok(aggregateSchema.definitions.LineItem.required.includes("ID"))
assert.ok(aggregateSchema.definitions.LineItem.required.includes("Item"))
assert.ok(aggregateSchema.definitions.MonetaryTotal.required.includes("PayableAmount"))
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

console.log("UBL Order UI contract matches ubl-source")


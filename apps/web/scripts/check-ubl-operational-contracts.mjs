import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..")
const schemaRoot = resolve(webRoot, "../../vendor/ubl/2.1/json-schema")

const documents = [
  ["OrderResponse", "OrderLine", false, ["BuyerCustomerParty", "SellerSupplierParty", "OrderReference"]],
  ["DespatchAdvice", "DespatchLine", true, ["DespatchSupplierParty", "DeliveryCustomerParty", "Shipment"]],
  ["RemittanceAdvice", "RemittanceAdviceLine", true, ["AccountingCustomerParty", "AccountingSupplierParty", "TotalPaymentAmount"]],
  ["ReceiptAdvice", "ReceiptLine", true, ["DespatchSupplierParty", "DeliveryCustomerParty", "Shipment"]],
]

const aggregateSchema = JSON.parse(await readFile(resolve(schemaRoot, "common/UBL-CommonAggregateComponents-2.1.json"), "utf8"))

for (const [root, line, lineRequired, displayed] of documents) {
  const path = resolve(schemaRoot, `maindoc/UBL-${root}-2.1.json`)
  const schema = JSON.parse(await readFile(path, "utf8"))
  assert.deepEqual(schema.required, [root], `${root} envelope requirements changed`)
  assert.equal(schema.properties[root]?.type, "array", `${root} root must remain an array`)
  assert.equal(schema.properties[root]?.minItems, 1, `${root} root must require one item`)
  assert.equal(schema.properties[root]?.maxItems, 1, `${root} root must allow one item`)
  const definition = schema.definitions[root]
  for (const required of ["ID", "IssueDate"]) {
    assert.ok(definition.required.includes(required), `${root}.${required} must remain required`)
    assert.equal(definition.properties[required]?.type, "array", `${root}.${required} must remain an array`)
  }
  assert.ok(definition.properties[line], `${root}.${line} is missing`)
  assert.equal(definition.properties[line]?.type, "array", `${root}.${line} must remain an array`)
  assert.equal(definition.required.includes(line), lineRequired, `${root}.${line} required status changed`)
  for (const property of displayed) {
    assert.ok(definition.properties[property], `${root}.${property} is missing`)
  }
}

for (const [definition, properties] of Object.entries({
  DespatchLine: ["OrderLineReference"],
  ReceiptLine: ["DespatchLineReference"],
  RemittanceAdviceLine: ["BillingReference", "CreditLineAmount", "BalanceAmount"],
  BillingReference: ["InvoiceDocumentReference"],
})) {
  for (const property of properties) {
    assert.ok(aggregateSchema.definitions[definition]?.properties?.[property], `${definition}.${property} is missing`)
  }
}

console.log("Operational UBL UI contracts match vendor/ubl/2.1")


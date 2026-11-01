import assert from "node:assert/strict"
import test from "node:test"
import { findInvalidNullableReferences } from "./openapi-contract-validation.mjs"

test("rejects a null type combined with a reference", () => {
  const contract = { schema: { $ref: "#/components/schemas/TransferProvenance", type: "null" } }

  assert.equal(findInvalidNullableReferences(contract).length, 1)
})

test("accepts a reference and null expressed as alternatives", () => {
  const contract = {
    schema: {
      oneOf: [
        { $ref: "#/components/schemas/TransferProvenance" },
        { type: "null" },
      ],
    },
  }

  assert.deepEqual(findInvalidNullableReferences(contract), [])
})

test("rejects OpenAPI 3.0 nullable flags in 3.1 contracts", () => {
  const contract = { schema: { nullable: true, type: "string" } }

  assert.equal(findInvalidNullableReferences(contract).length, 1)
})

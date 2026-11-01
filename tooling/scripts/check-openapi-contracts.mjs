import { readFile, readdir } from "node:fs/promises"
import { resolve } from "node:path"
import { findInvalidNullableReferences } from "./openapi-contract-validation.mjs"

const workspace = resolve(import.meta.dirname, "../..")
const contractsDirectory = resolve(workspace, "contracts")
const failures = []

for (const entry of await readdir(contractsDirectory, { withFileTypes: true })) {
  if (!entry.isDirectory()) continue
  const contractPath = resolve(contractsDirectory, entry.name, "openapi.json")
  const contract = JSON.parse(await readFile(contractPath, "utf8"))
  failures.push(...findInvalidNullableReferences(contract, entry.name))
}

if (failures.length > 0) {
  console.error("Invalid OpenAPI nullable-reference schemas:\n")
  failures.forEach((failure) => console.error(`- ${failure}`))
  console.error("\nRepresent nullable references as oneOf: [$ref, { type: 'null' }].")
  process.exitCode = 1
} else {
  console.log("OpenAPI nullable-reference schemas are valid.")
}

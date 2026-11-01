export function findInvalidNullableReferences(contract, name = "contract") {
  const failures = []
  inspect(contract, `${name}#`, failures)
  return failures
}

function inspect(value, path, failures) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => inspect(item, `${path}/${index}`, failures))
    return
  }
  if (value === null || typeof value !== "object") return

  if (typeof value.$ref === "string" && permitsNull(value.type)) {
    failures.push(`${path} combines $ref with a null type, which is an intersection rather than a nullable union`)
  }
  if (value.nullable === true) {
    failures.push(`${path} uses OpenAPI 3.0 nullable instead of an OpenAPI 3.1 null union`)
  }

  Object.entries(value).forEach(([key, item]) => inspect(item, `${path}/${escapePointer(key)}`, failures))
}

function permitsNull(type) {
  return type === "null" || (Array.isArray(type) && type.includes("null"))
}

function escapePointer(value) {
  return value.replaceAll("~", "~0").replaceAll("/", "~1")
}

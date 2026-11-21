import { getSchema } from "better-auth/db"
import { oauthProvider } from "@better-auth/oauth-provider"
import { writeFile, mkdir } from "node:fs/promises"
import { randomBytes } from "node:crypto"

// Schema generation is offline and must never open a production database.
Object.assign(process.env, { NODE_ENV: "production", BETTER_AUTH_SECRET: randomBytes(32).toString("hex") })
process.env.NEXT_PHASE = "phase-production-build"
process.env.BETTER_AUTH_DATABASE_PATH = ":memory:"
process.env.BETTER_AUTH_AUTO_MIGRATE = "false"
process.env.LOCAL_TEST_USER_BOOTSTRAP = "false"
const { auth } = await import("../src/lib/auth")
const oauth = process.argv.includes("--mcp")
const schema = oauth
  ? getSchema({ plugins: [oauthProvider({ loginPage: "/mcp/connect/login", consentPage: "/mcp/connect/consent" })] })
  : getSchema(auth.options)
const quote = (name: string) => {
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) throw new Error(`Invalid schema identifier: ${name}`)
  return `"${name}"`
}
const types: Record<string, string> = { string: "TEXT", boolean: "BOOLEAN", number: "INTEGER", date: "TIMESTAMPTZ", json: "JSONB", "string[]": "TEXT", "number[]": "TEXT" }
const pending = new Set(Object.keys(schema).filter(name => !schema[name].disableMigrations && (!oauth || name.startsWith("oauth"))))
const complete = new Set<string>(oauth ? ["user", "session"] : [])
const statements: string[] = []
const indexes: string[] = []
while (pending.size) {
  const name = [...pending].find(name => Object.values(schema[name].fields).every(field => !field.references || field.references.model === name || complete.has(field.references.model)))
  if (!name) throw new Error("Cyclic authentication schema requires an explicit migration")
  const columns = ['"id" TEXT PRIMARY KEY NOT NULL']
  for (const [column, field] of Object.entries(schema[name].fields)) {
    if (column === "id") continue
    const type = Array.isArray(field.type) ? "TEXT" : types[field.type]
    if (!type) throw new Error(`Unsupported DSQL field: ${name}.${column}`)
    let definition = `${quote(column)} ${type}${field.required === false ? "" : " NOT NULL"}${field.unique ? " UNIQUE" : ""}`
    if (field.references) {
      const action = (field.references.onDelete ?? "cascade").toUpperCase()
      if (!["CASCADE", "RESTRICT", "NO ACTION", "SET NULL", "SET DEFAULT"].includes(action)) throw new Error("Invalid reference action")
      definition += ` REFERENCES auth.${quote(field.references.model)} (${quote(field.references.field)}) ON DELETE ${action}`
    }
    columns.push(definition)
    if (field.index && !field.unique) indexes.push(`CREATE INDEX ASYNC ${quote(`${name}_${column}_idx`)} ON auth.${quote(name)} (${quote(column)});`)
  }
  statements.push(`CREATE TABLE auth.${quote(name)} (\n  ${columns.join(",\n  ")}\n);`)
  pending.delete(name)
  complete.add(name)
}
if (oauth) indexes.push(
  'CREATE UNIQUE INDEX ASYNC "oauthClientResource_client_resource_idx" ON auth."oauthClientResource" ("clientId", "resourceId");',
  'CREATE UNIQUE INDEX ASYNC "oauthConsent_client_user_reference_idx" ON auth."oauthConsent" ("clientId", "userId", "referenceId");',
)
else statements.push(
  "CREATE TABLE auth.platform_principal (user_id TEXT PRIMARY KEY REFERENCES auth.\"user\"(id) ON DELETE CASCADE, role TEXT NOT NULL CHECK (role = 'platform_admin'), created_at TIMESTAMPTZ NOT NULL);",
  "CREATE TABLE auth.organization_tenant (organization_id TEXT PRIMARY KEY REFERENCES auth.organization(id) ON DELETE CASCADE, tenant_id TEXT UNIQUE NOT NULL, created_at TIMESTAMPTZ NOT NULL);",
)
await mkdir("migrations", { recursive: true })
await writeFile(oauth ? "migrations/002-mcp-oauth.sql" : "migrations/001-auth.sql", `-- Generated from the pinned Better Auth configuration. Review changes; never rewrite an applied migration.\n${[...statements, ...indexes].join("\n\n")}\n`)

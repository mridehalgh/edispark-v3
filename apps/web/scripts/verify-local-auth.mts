import { strict as assert } from "node:assert"
import { randomBytes } from "node:crypto"
import { mkdtemp, rm } from "node:fs/promises"
import { tmpdir } from "node:os"
import { join } from "node:path"

const directory = await mkdtemp(join(tmpdir(), "edispark-auth-verification-"))
const base = "http://localhost:3000"
Object.assign(process.env, {
  NODE_ENV: "test", BETTER_AUTH_URL: base,
  BETTER_AUTH_SECRET: randomBytes(32).toString("hex"),
  BETTER_AUTH_DATABASE_PATH: join(directory, "auth.db"),
  BETTER_AUTH_AUTO_MIGRATE: "false", LOCAL_TEST_USER_BOOTSTRAP: "false",
})
delete process.env.RESEND_API_KEY
delete process.env.EMAIL_FROM
const info = console.info
console.info = () => {}
const { auth } = await import("../src/lib/auth")
const context = await auth.$context
await context.runMigrations()
const cookies = new Map<string, string>()
async function post(path: string, body: unknown) {
  const response = await auth.handler(new Request(`${base}/api/auth/${path}`, {
    method: "POST", headers: { "Content-Type": "application/json", Origin: base, Cookie: [...cookies.values()].join("; ") }, body: JSON.stringify(body),
  }))
  for (const cookie of response.headers.getSetCookie()) {
    const value = cookie.split(";")[0]
    cookies.set(value.split("=")[0], value)
  }
  assert.equal(response.status, 200, `Local auth failed at ${path}`)
  return response.json()
}
try {
  await post("sign-up/email", { email: "user@example.invalid", name: "[name]", password: randomBytes(24).toString("hex") })
  const organization = await post("organization/create", { name: "Local verification", slug: "local-verification" })
  await post("organization/set-active", { organizationId: organization.id })
  const response = await auth.handler(new Request(`${base}/api/auth/token`, { headers: { Cookie: [...cookies.values()].join("; ") } }))
  assert.equal(response.status, 200)
  const result = await response.json()
  const claims = JSON.parse(Buffer.from(result.token.split(".")[1], "base64url").toString())
  assert.equal(claims.organizationId, organization.id)
  assert.equal(claims.organization_role, "owner")
  assert.equal(claims.tenant_id, process.env.LOCAL_SEED_TENANT_ID ?? "2OnHVzeAJKwm0ThFLcy1HfLHGPm")
  console.log("Local SQLite signup, organization, session and tenant JWT verified")
} finally { console.info = info; await rm(directory, { recursive: true, force: true }) }

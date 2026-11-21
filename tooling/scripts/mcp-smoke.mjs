import assert from "node:assert/strict"
import { createHash, randomBytes } from "node:crypto"
import { localEnvironment } from "./local-environment.mjs"

const env = await localEnvironment()
const web = loopbackOrigin(env.BETTER_AUTH_URL)
const api = loopbackOrigin(env.EDISPARK_API_URL)
const resource = api + "/mcp"
const issuer = web + "/api/auth"
const redirectUri = "http://127.0.0.1:9876/callback"
const clientId = "edispark-local-mcp-smoke"
const organizationId = "local-test-workspace"
const version = "2026-07-28"
const cookies = new Map()
let requestId = 0

const anonymous = await expectStatus(await request(resource), 401)
assert.match(anonymous.headers.get("www-authenticate"), /resource_metadata/)
const metadata = await json(api + "/.well-known/oauth-protected-resource/mcp")
assert.equal(metadata.resource, resource)
assert.deepEqual(metadata.authorization_servers, [issuer])
const oauth = await json(web + "/.well-known/oauth-authorization-server/api/auth")
assert.equal(oauth.issuer, issuer)
assert.equal(oauth.authorization_endpoint, issuer + "/oauth2/authorize")
assert.equal(oauth.token_endpoint, issuer + "/oauth2/token")

await auth("/sign-in/email", { email: env.LOCAL_TEST_USER_EMAIL, password: env.LOCAL_TEST_USER_PASSWORD })
await auth("/organization/set-active", { organizationId })
const accessToken = await authorize("edi:evidence edi:preview")

const { token: sessionToken } = await auth("/token")
await expectStatus(await request(resource, { headers: bearer(sessionToken) }), 401)
await expectStatus(await request(api + "/api/exchanges", { headers: bearer(accessToken) }), 401)
const rpcHeaders = {
  ...bearer(accessToken), "content-type": "application/json", accept: "application/json, text/event-stream",
  "mcp-protocol-version": version,
}
await expectStatus(await request(resource, {
  method: "POST", headers: { ...rpcHeaders, "mcp-method": "tools/call" }, body: " ".repeat(96 * 1024 + 1),
}), 413)
const malformedResponse = await request(resource, {
  method: "POST", headers: { ...rpcHeaders, "mcp-method": "tools/list" }, body: "{",
})
assert.equal((await malformedResponse.json()).error.code, -32700)
assert.ok((await rpc("server/discover")).capabilities.tools)
assert.deepEqual((await rpc("tools/list")).tools.map(tool => tool.name).sort(), [
  "check_outbound_readiness", "find_exchanges", "get_route_context", "preview_mapping", "trace_exchange",
])

const fixture = (await rest("/api/exchanges")).find(exchange => exchange.agreementId && exchange.capabilityId)
assert.ok(fixture, "The local demo should have an exchange linked to a capability")
const fixtureTime = Date.parse(fixture.createdAt)
let cursor
let linked
do {
  const { structuredContent } = await call("find_exchanges", {
    from: new Date(fixtureTime - 1).toISOString(), to: new Date(fixtureTime + 1).toISOString(),
    ...(cursor ? { cursor } : {}),
  })
  linked = structuredContent.matches.find(exchange => exchange.exchangeId === fixture.id)
  cursor = structuredContent.nextCursor
} while (!linked && cursor)
assert.ok(linked, "The exchange search should find the fixture")
const trace = (await call("trace_exchange", { exchangeId: linked.exchangeId })).structuredContent
assert.equal(trace.exchangeId, linked.exchangeId)
assert.ok(trace.transportReceipt)
assert.ok(trace.functionalAcknowledgement)
assert.ok(trace.businessAcceptance)
const route = await call("get_route_context", { agreementId: linked.agreementId, capabilityId: linked.capabilityId })
assert.ok(route.structuredContent.mappingRevisionId)

const agreements = await rest("/api/trading-partners/agreements")
const agreement = agreements.find(item => item.name === "B&Q ACK v1.4 · local simulator" && item.status === "ACTIVE")
assert.ok(agreement)
const capability = agreement.capabilities.find(item => item.direction === "OUTBOUND")
const { items: sets } = await rest("/api/document-sets?limit=100")
for (const [title, ready] of [
  ["B&Q acknowledgement · vendor number needed", false],
  ["B&Q acknowledgement · ready to send", true],
]) {
  const set = sets.find(item => item.metadata.name === title)
  assert.ok(set, title)
  const documentPath = "/api/document-sets/" + set.id + "/documents/" + set.documents[0].id
  const document = await rest(documentPath)
  const pins = { documentSetId: set.id, documentId: document.id, representationId: document.currentVersion.id,
    contentHash: document.currentVersion.contentHash }
  const routePins = { agreementId: agreement.id, capabilityId: capability.id }
  const readiness = await call("check_outbound_readiness", { ...pins, ...routePins })
  assert.equal(readiness.structuredContent.ready, ready)
  const preview = await call("preview_mapping", { ...pins, mappingRevisionId: capability.mappingRevisionId })
  assert.equal(preview.structuredContent.passed, ready)
  assert.equal(preview.structuredContent.mode, "RETAINED_SOURCE")
  const stale = await call("check_outbound_readiness", {
    ...pins, ...routePins, representationId: "00000000-0000-4000-8000-000000000000",
  })
  assert.equal(stale.structuredContent.code, "STALE_SOURCE")
  assert.deepEqual((await rest(documentPath)).derivatives, document.derivatives)
}
// This grant belongs to the dedicated smoke client. Leave interactive consents alone.
const limitedToken = await authorize("edi:evidence")
const denied = await rpc("tools/call", {
  name: "preview_mapping", arguments: { mappingRevisionId: route.structuredContent.mappingRevisionId, input: "{}", expected: "{}" },
}, limitedToken)
assert.equal(denied.isError, true)
assert.equal(denied.structuredContent.code, "INSUFFICIENT_SCOPE")
const consents = await auth("/oauth2/get-consents")
const smokeConsent = consents.find(item => item.clientId === clientId
  && item.referenceId === organizationId && item.resources?.includes(resource))
assert.ok(smokeConsent?.id)
await auth("/oauth2/delete-consent", { id: smokeConsent.id })
await expectStatus(await request(resource, {
  method: "POST",
  headers: { ...rpcHeaders, ...bearer(limitedToken), "mcp-method": "tools/list" },
  body: "{}",
}), 403)
console.log("MCP smoke passed: five tools, token isolation, scope denial, consent revocation, and preview safeguards.")

async function authorize(scopes) {
  const verifier = randomBytes(32).toString("base64url")
  const state = randomBytes(16).toString("base64url")
  const query = new URLSearchParams({
    client_id: clientId, redirect_uri: redirectUri, response_type: "code", scope: scopes,
    resource, state, code_challenge: createHash("sha256").update(verifier).digest("base64url"),
    code_challenge_method: "S256", prompt: "consent",
  })
  const { url } = await auth("/oauth2/authorize?" + query)
  const consent = new URL(url, web)
  assert.equal(consent.origin, web)
  assert.equal(consent.pathname, "/mcp/connect/consent")
  const renderedConsent = await expectStatus(await request(consent))
  assert.match(await renderedConsent.text(), /Connect your MCP client/)
  const accepted = await auth("/oauth2/consent", { accept: true, oauth_query: consent.search.slice(1) })
  const callback = new URL(accepted.url)
  assert.equal(callback.origin + callback.pathname, redirectUri)
  assert.equal(callback.searchParams.get("state"), state)
  assert.equal(callback.searchParams.get("iss"), issuer)
  const { access_token: accessToken } = await json(oauth.token_endpoint, {
    method: "POST",
    body: new URLSearchParams({
      grant_type: "authorization_code", client_id: clientId, redirect_uri: redirectUri,
      code: callback.searchParams.get("code"), code_verifier: verifier, resource,
    }),
  })
  assert.ok(accessToken)
  return accessToken
}

async function rpc(method, params = {}, token = accessToken) {
  const message = await json(resource, {
    method: "POST",
    headers: {
      ...rpcHeaders, ...bearer(token), "mcp-method": method, ...(params.name ? { "mcp-name": params.name } : {}),
    },
    body: JSON.stringify({
      jsonrpc: "2.0", id: ++requestId, method,
      params: {
        ...params,
        _meta: {
          "io.modelcontextprotocol/protocolVersion": version,
          "io.modelcontextprotocol/clientCapabilities": {},
          "io.modelcontextprotocol/clientInfo": { name: "edispark-smoke", version: "1" },
        },
      },
    }),
  })
  assert.ok(message.result, message.error?.message)
  return message.result
}

function call(name, args) {
  return rpc("tools/call", { name, arguments: args })
}

function rest(path) {
  return json(api + path, { headers: bearer(sessionToken) })
}

async function auth(path, body) {
  const response = await expectStatus(await request(issuer + path, {
    headers: {
      origin: web, accept: "application/json",
      cookie: [...cookies].map(([key, value]) => key + "=" + value).join("; "),
      ...(body ? { "content-type": "application/json" } : {}),
    },
    ...(body ? { method: "POST", body: JSON.stringify(body) } : {}),
  }))
  for (const entry of response.headers.getSetCookie()) {
    const pair = entry.split(";")[0]
    const split = pair.indexOf("=")
    cookies.set(pair.slice(0, split), pair.slice(split + 1))
  }
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

function bearer(token) {
  return { authorization: "Bearer " + token }
}

function request(url, options) {
  return fetch(url, { redirect: "manual", signal: AbortSignal.timeout(30000), ...options })
}

async function json(url, options) {
  return (await expectStatus(await request(url, options))).json()
}

async function expectStatus(response, status = 200) {
  assert.equal(response.status, status, new URL(response.url).pathname)
  return response
}

function loopbackOrigin(value) {
  const url = new URL(value)
  assert.ok(url.protocol === "http:" && ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname),
    "MCP smoke tests require loopback URLs")
  return url.origin
}

import assert from "node:assert/strict"
import { createHash, randomBytes } from "node:crypto"
import { readFile } from "node:fs/promises"
import { localEnvironment } from "./local-environment.mjs"
import { tools as documentedTools } from "../../apps/web/src/app/ai/guide.ts"

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

// Compile the token handler before it receives a single-use authorization code.
const tokenHandlerProbe = () => request(oauth.token_endpoint, { method: "POST", body: new URLSearchParams() })
let tokenProbe = await tokenHandlerProbe()
if (tokenProbe.status === 500) {
  await tokenProbe.body?.cancel()
  tokenProbe = await tokenHandlerProbe()
}
assert.ok([400, 401].includes(tokenProbe.status), `Unexpected token handler response ${tokenProbe.status}`)
await tokenProbe.body?.cancel()

await expectStatus(await request(web + "/api/access"), 401)
await auth("/sign-in/email", { email: env.LOCAL_TEST_USER_EMAIL, password: env.LOCAL_TEST_USER_PASSWORD })
await auth("/organization/set-active", { organizationId })
const ownerAccess = await json(web + "/api/access", { headers: { cookie: cookieHeader() } })
assert.equal(ownerAccess.organizationRole, "owner")
assert.equal(ownerAccess.mcpSetup?.endpoint, resource)
const accessToken = await authorize("edi:evidence edi:preview mft:evidence")

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
const advertised = (await rpc("tools/list")).tools
assert.deepEqual(advertised.map(tool => tool.name).sort(), documentedTools.map(tool => tool.name).sort())
for (const { name, examples } of documentedTools) {
  const schema = advertised.find(tool => tool.name === name).inputSchema
  for (const example of examples) {
    assert.ok((schema.required ?? []).every(field => Object.hasOwn(example, field)), `${name} example omits a required argument`)
    for (const [field, value] of Object.entries(example)) {
      assert.equal(schema.properties[field]?.type, Array.isArray(value) ? "array" : typeof value,
        `${name} example uses an unknown or mistyped argument ${field}`)
      if (schema.properties[field].enum) assert.ok(schema.properties[field].enum.includes(value))
    }
  }
}

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
const qualification = (await call("preview_route_qualification", { agreementId: agreement.id })).structuredContent
assert.equal(qualification.passed, true)
assert.deepEqual(await rest(`/api/trading-partners/agreements/${agreement.id}`), agreement)
const syntheticInterchange = await readFile(new URL(
  "../../domains/transformations/src/main/resources/global-mappings/edifact-orders-d96a.edi", import.meta.url,
), "utf8")
const interchangePreview = (await call("preview_inbound_interchange", { input: syntheticInterchange })).structuredContent
assert.ok(interchangePreview.messageCount > 0)
assert.ok(interchangePreview.messages.some(message => message.documentType === "ORDER" && message.valid))
const mappingRevision = (await call("get_mapping_revision", { mappingRevisionId: capability.mappingRevisionId })).structuredContent
assert.equal(mappingRevision.mappingRevisionId, capability.mappingRevisionId)
const mappingDiff = (await call("compare_mapping_revisions", {
  baselineRevisionId: capability.mappingRevisionId, candidateRevisionId: capability.mappingRevisionId,
})).structuredContent
assert.equal(mappingDiff.changedCount, 0)
const profileFixture = await expectStatus(await request(`${api}/api/trading-partners/${agreement.partnerId}/document-profiles`, {
  method: "POST",
  headers: { ...bearer(sessionToken), "content-type": "application/json" },
  body: JSON.stringify({ name: `MCP smoke profile ${randomBytes(6).toString("hex")}`, direction: "OUTBOUND",
    documentType: "ORDER_RESPONSE", fieldRules: [{ fieldKey: "vendorNumber", requirement: "REQUIRED" }] }),
}), 201).then(response => response.json())
assert.ok(profileFixture.id)
try {
  const profile = (await call("get_document_profile", { documentProfileRevisionId: profileFixture.id })).structuredContent
  assert.ok(profile.fieldRules.some(rule => rule.fieldKey === "vendorNumber" && rule.requirement === "REQUIRED"))
} finally {
  await expectStatus(await request(`${api}/api/trading-partners/document-profiles/${profileFixture.id}`, {
    method: "DELETE", headers: bearer(sessionToken),
  }), 204)
}

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
  const outboundRoutes = (await call("list_eligible_routes", { documentSetId: set.id, documentId: document.id })).structuredContent
  assert.ok(outboundRoutes.routes.some(item => item.agreementId === agreement.id && item.capabilityId === capability.id))
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
const orders = await collect("find_documents", { businessReference: "PO-2026-0042", documentType: "ORDER" }, "matches")
assert.ok(orders.length, "the seeded order is searchable by business reference")
const documentIds = ({ documentSetId, documentId }) => ({ documentSetId, documentId })
let linkedOrder
let linkedWorkflow
for (const order of orders) {
  const links = await collect("get_document_workflow", documentIds(order), "links")
  if (links.some(link => link.relation === "DESPATCHES") && links.some(link => link.relation === "INVOICES")) {
    linkedOrder = order
    linkedWorkflow = links
    break
  }
}
assert.ok(linkedOrder, "the seeded order has cross-set despatch and invoice evidence")
const orderContext = (await call("get_document_context", documentIds(linkedOrder))).structuredContent
assert.equal(orderContext.representationStatus, "RECORDED")
assert.ok(orderContext.contentHash)
const draft = (await call("prepare_related_document_draft", documentIds(linkedOrder))).structuredContent
assert.equal(draft.status, "REVIEW_IN_WORKSPACE")
const draftLink = new URL(draft.workspacePath, web)
assert.equal(draftLink.pathname, "/documents/new")
assert.equal(draftLink.searchParams.get("type"), "ORDER_RESPONSE")
const smartCopy = new URL(`/api/documents/${linkedOrder.documentSetId}/smart-copy`, web)
smartCopy.search = new URLSearchParams({ sourceDocumentId: linkedOrder.documentId, targetType: "ORDER_RESPONSE" })
const ordinary = await json(smartCopy, { headers: { cookie: cookieHeader() } })
smartCopy.searchParams.set("representationId", draftLink.searchParams.get("fromRepresentation"))
smartCopy.searchParams.set("contentHash", draftLink.searchParams.get("fromHash"))
const pinned = await json(smartCopy, { headers: { cookie: cookieHeader() } })
assert.deepEqual(pinned, ordinary, "MCP must reuse the existing Order Response copy behavior")
assert.ok(pinned.values.lines.length > 0)
assert.equal(pinned.requiresCommercialReview, true)
smartCopy.searchParams.set("contentHash", "sha-256:" + "0".repeat(64))
await expectStatus(await request(smartCopy, { headers: { cookie: cookieHeader() } }), 409)

const pinsFor = async document => {
  const context = (await call("get_document_context", documentIds(document))).structuredContent
  assert.equal(context.representationStatus, "RECORDED")
  return { ...documentIds(document), representationId: context.representationId, contentHash: context.contentHash }
}
const correction = (await call("prepare_document_correction", documentIds(linkedOrder))).structuredContent
const correctionLink = new URL(correction.workspacePath, web)
assert.equal(correctionLink.searchParams.get("mode"), "correct")
assert.equal(correctionLink.searchParams.get("type"), "ORDER")
const orderPins = await pinsFor(linkedOrder)
const canonicalValidation = await call("validate_document_preview", orderPins)
assert.equal(Boolean(canonicalValidation.isError), false)
assert.equal(typeof canonicalValidation.structuredContent.passed, "boolean")

const response = linkedWorkflow.find(link => link.relation === "RESPONDS_TO")?.document
const despatch = linkedWorkflow.find(link => link.relation === "DESPATCHES").document
const invoice = linkedWorkflow.find(link => link.relation === "INVOICES").document
const receipt = (await collect("get_document_workflow", documentIds(despatch), "links")).find(link => link.relation === "RECEIVES")?.document
const remittance = (await collect("get_document_workflow", documentIds(invoice), "links")).find(link => link.relation === "REMITS")?.document
assert.ok(response && receipt && remittance, "expected order response, receipt, and remittance relationships")
for (const [name, primary, related] of [
  ["compare_order_to_response", orderPins, await pinsFor(response)],
  ["reconcile_despatch_and_receipt", await pinsFor(despatch), await pinsFor(receipt)],
  ["reconcile_invoice_and_remittance", await pinsFor(invoice), await pinsFor(remittance)],
]) {
  const { structuredContent, isError } = await call(name, { primary, related: [related] })
  assert.equal(Boolean(isError), false, `${name} returned ${structuredContent.code}`)
  assert.ok(Array.isArray(structuredContent.findings))
  assert.equal(structuredContent.values, undefined, "commercial values require their own grant")
}

if (trace.sourceDocument) {
  const associated = await collect("find_exchanges", documentIds(trace.sourceDocument), "matches")
  assert.ok(associated.some(item => item.exchangeId === trace.exchangeId))
}
const exceptionWindow = { from: new Date(fixtureTime - 86_400_000).toISOString(), to: new Date(fixtureTime + 86_400_000).toISOString() }
assert.ok(Array.isArray(await collect("find_document_exceptions", exceptionWindow, "matches")))
const codes = (await call("list_business_codes", { listId: "ORDER_RESPONSE" })).structuredContent.matches
assert.ok(codes[0].values.length)
if (trace.transfer?.id) {
  const interchange = await collect("trace_interchange", { transferId: trace.transfer.id }, "matches")
  assert.ok(interchange.some(exchange => exchange.exchangeId === trace.exchangeId))
}
const transfers = await rest("/api/mft/transfers")
const waiting = transfers.find(item => item.status === "AWAITING_MDN" && item.direction === "OUTBOUND")
const inbound = transfers.find(item => item.direction === "INBOUND")
const retry = transfers.find(item => item.status === "RETRY_SCHEDULED")
assert.ok(waiting && inbound && retry, "expected local AS2 operational fixtures")
const foundTransfer = (await call("find_transfers", { transferId: waiting.id })).structuredContent.matches[0]
assert.equal(foundTransfer.status, "AWAITING_MDN")
const waitingTrace = (await call("trace_transfer", { transferId: waiting.id })).structuredContent
assert.equal(waitingTrace.transportReceipt, "NOT_RECORDED")
assert.deepEqual(await collect("trace_interchange", { transferId: waiting.id }, "matches"), [])
const retryTrace = (await call("trace_transfer", { transferId: retry.id })).structuredContent
assert.ok(retryTrace.nextAttemptAt)
const connection = (await call("find_connections", { connectionId: waiting.partnerId })).structuredContent.matches[0]
assert.equal(connection.connectionId, waiting.partnerId)
const certificate = (await call("find_certificates", { certificateId: connection.localCertificateId })).structuredContent.matches[0]
assert.ok(certificate.notAfter && certificate.fingerprintSha256)
assert.equal(certificate.publicCertificatePem, undefined)
const setup = (await call("get_connection_setup", { connectionId: connection.connectionId })).structuredContent
assert.ok(setup.as2Identifier && setup.signatureAlgorithms.length)
const usage = await collect("get_certificate_usage", { certificateId: connection.localCertificateId }, "matches")
assert.ok(usage.some(item => item.connectionId === connection.connectionId && item.localIdentity))
const configurationDiff = (await call("compare_connection_revisions", {
  baselineConnectionId: connection.connectionId, candidateConnectionId: connection.connectionId,
})).structuredContent
assert.deepEqual(configurationDiff.changedFields, [])
const transferReadiness = (await call("check_transfer_readiness", { connectionId: connection.connectionId })).structuredContent
assert.equal(typeof transferReadiness.ready, "boolean")
const rollover = (await call("preview_certificate_rollover", {
  connectionId: connection.connectionId, signingCertificateId: connection.partnerSigningCertificateId,
  encryptionCertificateId: connection.partnerEncryptionCertificateId,
})).structuredContent
assert.equal(typeof rollover.ready, "boolean")

assert.ok(Array.isArray(await collect("find_mdn_deliveries", { transferId: inbound.id }, "matches")))

const financialPins = { primary: await pinsFor(invoice), related: [await pinsFor(remittance)] }
const staleComparison = await call("reconcile_invoice_and_remittance", {
  ...financialPins, primary: { ...financialPins.primary, contentHash: "sha-256:" + "0".repeat(64) },
})
assert.equal(staleComparison.structuredContent.code, "STALE_SOURCE")
const commercialToken = await authorize("edi:evidence edi:commercial")
const commercial = await rpc("tools/call", { name: "reconcile_invoice_and_remittance", arguments: financialPins }, commercialToken)
assert.ok(commercial.structuredContent.values.length, "the explicit commercial grant enables comparison values")
// This grant belongs to the dedicated smoke client. Leave interactive consents alone.
const limitedToken = await authorize("edi:evidence")
const denied = await rpc("tools/call", {
  name: "preview_mapping", arguments: { mappingRevisionId: route.structuredContent.mappingRevisionId, input: "{}", expected: "{}" },
}, limitedToken)
assert.equal(denied.isError, true)
assert.equal(denied.structuredContent.code, "INSUFFICIENT_SCOPE")
const deniedMft = await rpc("tools/call", { name: "trace_transfer", arguments: { transferId: waiting.id } }, limitedToken)
assert.equal(deniedMft.structuredContent.code, "INSUFFICIENT_SCOPE")
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
cookies.clear()
await auth("/sign-in/email", { email: env.LOCAL_MEMBER_USER_EMAIL, password: env.LOCAL_MEMBER_USER_PASSWORD })
await auth("/organization/set-active", { organizationId })
const memberAccess = await json(web + "/api/access", { headers: { cookie: cookieHeader() } })
assert.equal(memberAccess.organizationRole, "member")
assert.equal(memberAccess.mcpSetup, undefined)
console.log("MCP smoke passed: workspace access, document and AS2 evidence, smart-copy parity, documented tools, token isolation, scope denial, consent revocation, and preview safeguards.")

async function collect(tool, args, resultsKey) {
  let cursor
  const results = []
  for (let page = 0; page < 200; page++) {
    const result = await call(tool, { ...args, ...(cursor ? { cursor } : {}) })
    assert.equal(Boolean(result.isError), false, `${tool} returned ${result.structuredContent?.code}`)
    results.push(...result.structuredContent[resultsKey])
    cursor = result.structuredContent.nextCursor
    if (!cursor) return results
  }
  throw new Error(`${tool} exceeded the local fixture pagination budget`)
}

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
      cookie: cookieHeader(),
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

function cookieHeader() {
  return [...cookies].map(([key, value]) => key + "=" + value).join("; ")
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

#!/usr/bin/env node

import { randomUUID } from "node:crypto"
import { readFile } from "node:fs/promises"
import { resolve } from "node:path"

const WEB_ORIGIN = "http://localhost:3000"
const HEALTH_ENDPOINTS = [
  ["web-auth", `${WEB_ORIGIN}/api/auth/ok`],
  ["unified-api", "http://localhost:8080/actuator/health"],
  ["as2-simulator", "http://localhost:8082/actuator/health"],
]
const REDACTED_KEYS = /authorization|cookie|password|secret|token|private.?key|certificate.?bytes/i
const MAX_RESPONSE_CHARACTERS = 20_000

const [command, ...argumentsList] = process.argv.slice(2)

try {
  if (command === "health") {
    await health()
  } else if (command === "request") {
    await request(argumentsList)
  } else {
    invalid("command must be health or request")
  }
} catch (error) {
  console.error(error instanceof Error ? error.message : "Local API probe failed")
  process.exitCode = 1
}

async function health() {
  const results = await Promise.all(HEALTH_ENDPOINTS.map(async ([name, url]) => {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(3_000) })
      return { name, url, status: response.status, ok: response.ok }
    } catch (error) {
      return { name, url, ok: false, error: error instanceof Error ? error.message : "unreachable" }
    }
  }))
  console.log(JSON.stringify(results, null, 2))
  if (results.some((result) => !result.ok)) process.exitCode = 1
}

async function request(argumentsList) {
  const [methodCandidate, pathCandidate, ...options] = argumentsList
  const method = methodCandidate?.toUpperCase()
  if (!method || !pathCandidate) return invalid("request requires METHOD and /api/path")
  const target = localTarget(pathCandidate)
  if (target.pathname.endsWith("/content")) return invalid("binary content endpoints are not supported")

  const persona = option(options, "--persona") ?? "admin"
  if (!new Set(["admin", "member"]).has(persona)) return invalid("persona must be admin or member")
  const bodyFile = option(options, "--body-file")
  rejectUnknownOptions(options)
  if (bodyFile && new Set(["GET", "HEAD"]).has(method)) return invalid(`${method} cannot use --body-file`)
  const body = bodyFile ? await readFile(temporaryBodyPath(bodyFile)) : undefined
  const cookie = await login(persona)
  const correlationId = randomUUID()
  const response = await fetch(target, {
    method,
    body,
    redirect: "manual",
    signal: AbortSignal.timeout(15_000),
    headers: {
      Accept: "application/json",
      Cookie: cookie,
      "X-Correlation-ID": correlationId,
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
  })
  const contentType = response.headers.get("content-type") ?? ""
  const result = {
    method,
    url: target.toString(),
    status: response.status,
    correlationId: response.headers.get("x-correlation-id") ?? correlationId,
    body: contentType.includes("json") ? await safeJson(response) : { contentType, omitted: true },
  }
  console.log(JSON.stringify(redact(result), null, 2).slice(0, MAX_RESPONSE_CHARACTERS))
  if (!response.ok) process.exitCode = 1
}

async function login(persona) {
  const response = await fetch(`${WEB_ORIGIN}/api/local-test-login`, {
    method: "POST",
    redirect: "manual",
    signal: AbortSignal.timeout(10_000),
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ persona }),
  })
  if (!response.ok) throw new Error(`Local ${persona} sign-in failed with HTTP ${response.status}`)
  const setCookies = response.headers.getSetCookie?.() ?? [response.headers.get("set-cookie")].filter(Boolean)
  const cookie = setCookies.map((value) => value.split(";", 1)[0]).join("; ")
  if (!cookie) throw new Error("Local sign-in did not return a session cookie")
  return cookie
}

function localTarget(path) {
  if (!path.startsWith("/") || path.startsWith("//")) throw new Error("API path must start with one /")
  const target = new URL(path, WEB_ORIGIN)
  if (target.origin !== WEB_ORIGIN || !target.pathname.startsWith("/api/")) {
    throw new Error("Only same-origin local /api/ paths are supported")
  }
  return target
}

async function safeJson(response) {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return { malformedJson: true, preview: text.slice(0, 500) }
  }
}

function redact(value) {
  if (Array.isArray(value)) return value.map(redact)
  if (!value || typeof value !== "object") return value
  return Object.fromEntries(Object.entries(value).map(([key, entry]) => [
    key,
    REDACTED_KEYS.test(key) ? "[redacted]" : redact(entry),
  ]))
}

function option(options, name) {
  const index = options.indexOf(name)
  if (index < 0) return undefined
  const value = options[index + 1]
  if (!value || value.startsWith("--")) throw new Error(`${name} requires a value`)
  return value
}

function rejectUnknownOptions(options) {
  for (let index = 0; index < options.length; index += 2) {
    if (!new Set(["--persona", "--body-file"]).has(options[index])) {
      throw new Error(`Unknown option: ${options[index]}`)
    }
  }
}

function temporaryBodyPath(candidate) {
  const path = resolve(candidate)
  if (!path.startsWith("/tmp/") && !path.startsWith("/private/tmp/")) {
    throw new Error("--body-file must be stored under /tmp")
  }
  return path
}

function invalid(message) {
  console.error(message)
  usage()
  process.exitCode = 2
}

function usage() {
  console.error("Usage:")
  console.error("  local-api.mjs health")
  console.error("  local-api.mjs request METHOD /api/path [--persona admin|member] [--body-file /tmp/request.json]")
}

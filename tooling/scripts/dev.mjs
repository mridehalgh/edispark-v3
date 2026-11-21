import { localEnvironment, workspaceDirectory } from "./local-environment.mjs"
import { localServices, releasePorts, requireAvailablePorts } from "./ports.mjs"
import { coordinate, start, waitForExit } from "./processes.mjs"

const API_READY_TIMEOUT_MS = 120_000
const WEB_READY_TIMEOUT_MS = 60_000
const API_READY_POLL_INTERVAL_MS = 500

const smoke = process.argv.includes("--mcp-smoke")
const env = await localEnvironment()
if (smoke) env.EDISPARK_MCP_ENABLED = "true"
const cwd = workspaceDirectory()
if (smoke) await requireAvailablePorts(localServices(env))
else await releasePorts(localServices(env))
const childEnvironment = { ...env, EDISPARK_PORTS_PREPARED: "true" }
const api = start("pnpm", ["dev:api"], { cwd, env: childEnvironment })
const coordinator = coordinate([api])

try {
  await waitForEndpoint({
    child: api,
    label: "EdiSpark API",
    timeout: API_READY_TIMEOUT_MS,
    url: new URL("/actuator/health", env.EDISPARK_API_URL).toString(),
  })
  const simulator = start("pnpm", ["dev:as2-simulator"], { cwd, env: childEnvironment })
  coordinator.add(simulator)
  await waitForEndpoint({
    child: simulator,
    label: "AS2 simulator",
    timeout: API_READY_TIMEOUT_MS,
    url: new URL("/actuator/health", env.AS2_SIMULATOR_URL).toString(),
  })
  const web = start("pnpm", ["dev:web"], { cwd, env: childEnvironment })
  coordinator.add(web)
  await waitForEndpoint({
    child: web,
    label: "web authentication",
    timeout: WEB_READY_TIMEOUT_MS,
    url: new URL("/api/auth/jwks", env.BETTER_AUTH_URL).toString(),
  })
  console.log("EdiSpark local stack is ready.")
  if (smoke) {
    // Compile the access callback before the API applies its downstream deadline.
    const access = await fetch(new URL("/api/mcp/access", env.BETTER_AUTH_URL), {
      method: "POST", signal: AbortSignal.timeout(30_000),
    })
    if (access.status !== 403) throw new Error(`MCP access callback returned ${access.status}, expected 403.`)
    const probe = start("pnpm", ["smoke:mcp"], { cwd, env: childEnvironment })
    const exit = waitForExit(probe)
    coordinator.add(probe)
    process.exitCode = await exit
    coordinator.stop()
  }
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
  coordinator.stop()
}

async function waitForEndpoint({ child, label, timeout, url }) {
  console.log(`Waiting for ${label} at ${url} ...`)
  const deadline = Date.now() + timeout

  while (Date.now() < deadline) {
    if (child.exitCode !== null || child.signalCode !== null) {
      throw new Error(`${label} stopped before becoming ready.`)
    }

    try {
      const response = await fetch(url)
      if (response.ok) {
        console.log(`${label} is ready.`)
        return
      }
    } catch {
      // The API is still building or starting.
    }

    await delay(API_READY_POLL_INTERVAL_MS)
  }

  throw new Error(`${label} did not become ready within ${timeout / 1000} seconds.`)
}

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

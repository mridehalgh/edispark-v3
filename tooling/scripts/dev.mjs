import { localEnvironment, workspaceDirectory } from "./local-environment.mjs"
import { localServices, releasePorts } from "./ports.mjs"
import { coordinate, start } from "./processes.mjs"

const API_READY_TIMEOUT_MS = 120_000
const WEB_READY_TIMEOUT_MS = 60_000
const API_READY_POLL_INTERVAL_MS = 500

const env = await localEnvironment()
const cwd = workspaceDirectory()
await releasePorts(localServices(env))
const childEnvironment = { ...env, EDISPARK_PORTS_PREPARED: "true" }
const api = start("pnpm", ["dev:api"], { cwd, env: childEnvironment })
const coordinator = coordinate([api])

try {
  await waitForEndpoint({
    child: api,
    label: "Documents API",
    timeout: API_READY_TIMEOUT_MS,
    url: new URL("/actuator/health", env.DOCUMENTS_API_URL).toString(),
  })
  const mftApi = start("pnpm", ["dev:mft-api"], { cwd, env: childEnvironment })
  coordinator.add(mftApi)
  await waitForEndpoint({
    child: mftApi,
    label: "MFT API",
    timeout: API_READY_TIMEOUT_MS,
    url: new URL("/actuator/health", env.MFT_API_URL).toString(),
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

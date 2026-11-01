import { execFile } from "node:child_process"
import { createServer } from "node:net"
import { setTimeout as delay } from "node:timers/promises"
import { promisify } from "node:util"

const execFileAsync = promisify(execFile)
const gracefulShutdownMs = 2_000
const forcedShutdownMs = 1_000

export async function releasePorts(services) {
  for (const service of services) {
    const port = normalizePort(service.port)
    const processIds = await listeningProcessIds(port)
    for (const processId of processIds) {
      console.info(`Stopping process ${processId} on port ${port} (${service.name})...`)
      await stopProcess(processId)
    }
    await assertPortAvailable(port, service.name)
  }
}

export function localServices(env) {
  return [
    { name: "web application", port: new URL(env.BETTER_AUTH_URL).port || 3000 },
    { name: "embedded DynamoDB", port: new URL(env.DYNAMODB_LOCAL_ENDPOINT ?? "http://localhost:8000").port || 8000 },
    { name: "documents API", port: new URL(env.DOCUMENTS_API_URL).port || 8080 },
    { name: "MFT API", port: new URL(env.MFT_API_URL).port || 8081 },
    { name: "AS2 simulator", port: new URL(env.AS2_SIMULATOR_URL).port || 8082 },
    { name: "integration API", port: new URL(env.INTEGRATION_API_URL).port || 8083 },
  ]
}

async function listeningProcessIds(port) {
  if (process.platform === "win32") return windowsListeningProcessIds(port)

  try {
    const { stdout } = await execFileAsync("lsof", ["-nP", `-tiTCP:${port}`, "-sTCP:LISTEN"])
    return uniqueProcessIds(stdout)
  } catch (error) {
    if (error.code === 1) return []
    if (error.code === "ENOENT") {
      throw new Error("Cannot prepare local ports because the `lsof` command is unavailable.")
    }
    throw error
  }
}

async function windowsListeningProcessIds(port) {
  const { stdout } = await execFileAsync("netstat", ["-ano", "-p", "tcp"])
  const processIds = stdout.split(/\r?\n/).flatMap((line) => {
    const columns = line.trim().split(/\s+/)
    if (columns.length < 5 || columns[3] !== "LISTENING") return []
    if (!columns[1].endsWith(`:${port}`)) return []
    return [Number(columns[4])]
  })
  return [...new Set(processIds.filter(isSafeProcessId))]
}

function uniqueProcessIds(output) {
  return [...new Set(output.split(/\s+/).map(Number).filter(isSafeProcessId))]
}

function isSafeProcessId(processId) {
  return Number.isSafeInteger(processId) && processId > 1 && processId !== process.pid
}

async function stopProcess(processId) {
  if (process.platform === "win32") {
    await execFileAsync("taskkill", ["/PID", String(processId), "/T", "/F"])
    return
  }

  sendSignal(processId, "SIGTERM")
  if (await waitUntilStopped(processId, gracefulShutdownMs)) return

  console.info(`Process ${processId} did not stop gracefully; forcing shutdown...`)
  sendSignal(processId, "SIGKILL")
  if (!await waitUntilStopped(processId, forcedShutdownMs)) {
    throw new Error(`Unable to stop process ${processId}.`)
  }
}

function sendSignal(processId, signal) {
  try {
    process.kill(processId, signal)
  } catch (error) {
    if (error.code !== "ESRCH") throw error
  }
}

async function waitUntilStopped(processId, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (!isProcessRunning(processId)) return true
    await delay(50)
  }
  return !isProcessRunning(processId)
}

function isProcessRunning(processId) {
  try {
    process.kill(processId, 0)
    return true
  } catch (error) {
    if (error.code === "ESRCH") return false
    if (error.code === "EPERM") return true
    throw error
  }
}

function assertPortAvailable(port, serviceName) {
  return new Promise((resolve, reject) => {
    const server = createServer()
    server.unref()
    server.once("error", () => reject(new Error(
      `Cannot start the ${serviceName}: port ${port} is still unavailable after cleanup.`,
    )))
    server.listen(port, "127.0.0.1", () => server.close(resolve))
  })
}

function normalizePort(port) {
  const normalized = Number(port)
  if (!Number.isInteger(normalized) || normalized < 1 || normalized > 65_535) {
    throw new Error(`Invalid local port: ${port}`)
  }
  return normalized
}

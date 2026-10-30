import { spawn } from "node:child_process"

const FORCE_STOP_DELAY_MS = 3_000

export function start(command, args, options) {
  return spawn(command, args, {
    stdio: "inherit",
    detached: process.platform !== "win32",
    ...options,
  })
}

export function waitForExit(child) {
  return new Promise((resolve) => child.once("exit", (code, signal) => {
    resolve(code ?? (signal ? 1 : 0))
  }))
}

export function mirrorExit(child) {
  let stopping = false
  for (const signal of ["SIGINT", "SIGTERM"]) {
    process.on(signal, () => {
      stopping = true
      stopChild(child, signal)
    })
  }
  child.on("exit", (code, signal) => {
    process.exitCode = stopping ? 0 : code ?? (signal ? 1 : 0)
  })
}

export function coordinate(children) {
  const managedChildren = new Set()
  let stopping = false
  const stop = (signal = "SIGTERM") => {
    if (stopping) return
    stopping = true
    managedChildren.forEach((child) => stopChild(child, signal))
  }

  for (const signal of ["SIGINT", "SIGTERM"]) {
    process.on(signal, () => stop(signal))
  }

  const add = (child) => {
    managedChildren.add(child)
    child.on("exit", (code) => {
      if (!stopping) {
        process.exitCode = code ?? 1
        stop()
      }
    })
  }
  children.forEach(add)

  return { add, stop }
}

function stopChild(child, signal) {
  if (!child.pid || child.exitCode !== null) return
  try {
    if (process.platform === "win32") child.kill(signal)
    else process.kill(-child.pid, signal)
  } catch (error) {
    if (error.code !== "ESRCH") throw error
  }

  setTimeout(() => forceStopChild(child), FORCE_STOP_DELAY_MS)
}

function forceStopChild(child) {
  if (!child.pid) return
  try {
    if (process.platform === "win32") {
      if (child.exitCode === null) child.kill("SIGKILL")
    } else {
      process.kill(-child.pid, "SIGKILL")
    }
  } catch (error) {
    if (error.code !== "ESRCH") throw error
  }
}

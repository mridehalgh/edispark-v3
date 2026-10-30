import { execFileSync } from "node:child_process"
import { existsSync } from "node:fs"
import { resolve } from "node:path"

import { localEnvironment, workspaceDirectory } from "./local-environment.mjs"
import { releasePorts } from "./ports.mjs"
import { mirrorExit, start, waitForExit } from "./processes.mjs"

const cwd = workspaceDirectory()
const env = await localEnvironment()
const labDirectory = resolve(cwd, ".local/as2-simulator")
if (!existsSync(resolve(labDirectory, "password.txt"))) {
  execFileSync(process.execPath, ["tooling/scripts/generate-as2-lab-certs.mjs"], { cwd, stdio: "inherit" })
}
Object.assign(env, {
  MFT_LOCAL_IDENTITY_PATH: resolve(labDirectory, "edispark.p12"),
  MFT_LOCAL_PARTNER_CERTIFICATE_PATH: resolve(labDirectory, "simulator.cer"),
  MFT_LOCAL_IDENTITY_PASSWORD_FILE: resolve(labDirectory, "password.txt"),
  MFT_LOCAL_DEMO_SEED_ENABLED: "true",
})
const managedByRootRunner = env.EDISPARK_PORTS_PREPARED === "true"
if (!managedByRootRunner) {
  await releasePorts([
    { name: "MFT API", port: new URL(env.MFT_API_URL).port || 8081 },
  ])
}

const build = start("./mvnw", [
  "-B",
  "-pl", "apps/mft-api",
  "-am",
  "-DskipTests",
  "install",
], { cwd, detached: !managedByRootRunner, env })

const buildExitCode = await waitForExit(build)
if (buildExitCode !== 0) {
  process.exitCode = buildExitCode
} else {
  const child = start("./mvnw", [
    "-B",
    "-f", "apps/mft-api/pom.xml",
    "-DskipTests",
    "spring-boot:run",
  ], { cwd, detached: !managedByRootRunner, env })
  mirrorExit(child)
}

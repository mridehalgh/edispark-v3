import { execFileSync, spawn } from "node:child_process"
import { existsSync, readFileSync } from "node:fs"
import { resolve } from "node:path"

const passwordFile = resolve(".local/as2-simulator/password.txt")
if (!existsSync(passwordFile)) execFileSync(process.execPath, ["tooling/scripts/generate-as2-lab-certs.mjs"], { stdio: "inherit" })
const password = readFileSync(passwordFile, "utf8").trim()
const child = spawn("./mvnw", ["-B", "-pl", "tooling/as2-simulator", "spring-boot:run"], {
  cwd: resolve("."),
  env: {
    ...process.env,
    AS2_SIMULATOR_PASSWORD: password,
    AS2_SIMULATOR_KEYSTORE: resolve(".local/as2-simulator/simulator.p12"),
    AS2_SIMULATOR_RECEIVER_CERT: resolve(".local/as2-simulator/edispark.cer"),
  },
  stdio: "inherit",
})
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => child.kill(signal))
child.on("exit", (code, signal) => process.exit(signal ? 1 : (code ?? 0)))

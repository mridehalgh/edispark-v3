import { spawn } from "node:child_process"
import { resolve } from "node:path"
import { ensureAs2Lab, readAs2LabPassword } from "./as2-lab.mjs"

const paths = ensureAs2Lab()
const password = readAs2LabPassword(paths)
const child = spawn("./mvnw", ["-B", "-pl", "tooling/as2-simulator", "spring-boot:run"], {
  cwd: resolve("."),
  env: {
    ...process.env,
    AS2_SIMULATOR_PASSWORD: password,
    AS2_SIMULATOR_KEYSTORE: paths.simulatorIdentity,
    AS2_SIMULATOR_RECEIVER_CERT: paths.edisparkCertificate,
  },
  stdio: "inherit",
})
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => child.kill(signal))
child.on("exit", (code, signal) => process.exit(signal ? 1 : (code ?? 0)))

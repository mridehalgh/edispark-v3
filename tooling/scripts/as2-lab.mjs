import { execFileSync } from "node:child_process"
import { existsSync, readFileSync, statSync } from "node:fs"
import { resolve } from "node:path"

const workspace = resolve(import.meta.dirname, "../..")
export const as2LabDirectory = resolve(workspace, ".local/as2-simulator")

export function as2LabPaths(directory = as2LabDirectory) {
  return {
    directory,
    edisparkCertificate: resolve(directory, "edispark.cer"),
    edisparkIdentity: resolve(directory, "edispark.p12"),
    passwordFile: resolve(directory, "password.txt"),
    simulatorCertificate: resolve(directory, "simulator.cer"),
    simulatorIdentity: resolve(directory, "simulator.p12"),
  }
}

export function as2LabIsComplete(directory = as2LabDirectory) {
  const paths = as2LabPaths(directory)
  const files = [
    paths.edisparkCertificate,
    paths.edisparkIdentity,
    paths.passwordFile,
    paths.simulatorCertificate,
    paths.simulatorIdentity,
  ]
  return files.every((file) => existsSync(file) && statSync(file).size > 0)
    && readFileSync(paths.passwordFile, "utf8").trim().length > 0
}

export function ensureAs2Lab({ directory = as2LabDirectory, generate = generateAs2Lab } = {}) {
  if (as2LabIsComplete(directory)) return as2LabPaths(directory)

  generate({ directory, force: existsSync(directory) })
  if (!as2LabIsComplete(directory)) {
    throw new Error(`AS2 lab certificate generation did not complete in ${directory}`)
  }
  return as2LabPaths(directory)
}

export function readAs2LabPassword(paths = as2LabPaths()) {
  return readFileSync(paths.passwordFile, "utf8").trim()
}

function generateAs2Lab({ directory, force }) {
  if (directory !== as2LabDirectory) {
    throw new Error(`Refusing to generate AS2 lab certificates outside ${as2LabDirectory}`)
  }
  const args = [resolve(import.meta.dirname, "generate-as2-lab-certs.mjs")]
  if (force) args.push("--force")
  execFileSync(process.execPath, args, { cwd: workspace, stdio: "inherit" })
}

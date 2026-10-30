import { execFileSync } from "node:child_process"
import { randomBytes } from "node:crypto"
import { existsSync, mkdirSync, rmSync, writeFileSync } from "node:fs"
import { resolve } from "node:path"

const directory = resolve(".local/as2-simulator")
const force = process.argv.includes("--force")
if (existsSync(directory)) {
  if (!force) {
    console.log(`AS2 lab certificates already exist in ${directory}. Use --force to replace them.`)
    process.exit(0)
  }
  rmSync(directory, { recursive: true })
}
mkdirSync(directory, { recursive: true, mode: 0o700 })

const password = randomBytes(24).toString("base64url")
const keytool = (args) => execFileSync("keytool", args, { stdio: "inherit" })
const identity = (alias, filename) => keytool([
  "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "3072", "-sigalg", "SHA256withRSA",
  "-validity", "825", "-dname", `CN=${alias} AS2 Lab,O=EdiSpark Test,C=GB`,
  "-storetype", "PKCS12", "-keystore", resolve(directory, filename),
  "-storepass", password, "-keypass", password, "-noprompt",
])
const exportCertificate = (alias, keyStore, filename) => keytool([
  "-exportcert", "-rfc", "-alias", alias, "-keystore", resolve(directory, keyStore),
  "-storepass", password, "-file", resolve(directory, filename),
])

identity("EDISPARK", "edispark.p12")
identity("SIMULATOR", "simulator.p12")
exportCertificate("EDISPARK", "edispark.p12", "edispark.cer")
exportCertificate("SIMULATOR", "simulator.p12", "simulator.cer")
keytool([
  "-importcert", "-alias", "EDISPARK", "-file", resolve(directory, "edispark.cer"),
  "-keystore", resolve(directory, "simulator.p12"), "-storepass", password, "-noprompt",
])
writeFileSync(resolve(directory, "password.txt"), `${password}\n`, { mode: 0o600 })

console.log("\nAS2 lab ready:")
console.log(`  EdiSpark identity: ${resolve(directory, "edispark.p12")}`)
console.log(`  Partner certificate: ${resolve(directory, "simulator.cer")}`)
console.log(`  PKCS#12 password: ${password}`)

import { randomBytes } from "node:crypto"
import { mkdir, readFile, writeFile } from "node:fs/promises"
import { resolve } from "node:path"

const workspace = resolve(import.meta.dirname, "../..")
const localDirectory = resolve(workspace, ".local")
const settingsPath = resolve(localDirectory, "settings.json")
const seedTenantId = "2OnHVzeAJKwm0ThFLcy1HfLHGPm"

export async function localEnvironment() {
  await mkdir(localDirectory, { recursive: true })
  const settings = await readSettings()
  return {
    ...process.env,
    BETTER_AUTH_SECRET: process.env.BETTER_AUTH_SECRET ?? settings.betterAuthSecret,
    EDISPARK_INTERNAL_TOKEN: process.env.EDISPARK_INTERNAL_TOKEN ?? settings.internalServiceToken,
    BETTER_AUTH_URL: process.env.BETTER_AUTH_URL ?? "http://localhost:3000",
    BETTER_AUTH_DATABASE_PATH: process.env.BETTER_AUTH_DATABASE_PATH ?? resolve(localDirectory, "auth.db"),
    BETTER_AUTH_AUTO_MIGRATE: process.env.BETTER_AUTH_AUTO_MIGRATE ?? "true",
    LOCAL_TEST_USER_BOOTSTRAP: process.env.LOCAL_TEST_USER_BOOTSTRAP ?? "true",
    LOCAL_TEST_USER_EMAIL: process.env.LOCAL_TEST_USER_EMAIL ?? "local@edispark.test",
    LOCAL_TEST_USER_PASSWORD: process.env.LOCAL_TEST_USER_PASSWORD ?? "EdiSpark-Local-2026!",
    LOCAL_TEST_USER_NAME: process.env.LOCAL_TEST_USER_NAME ?? "Local Tester",
    LOCAL_MEMBER_USER_EMAIL: process.env.LOCAL_MEMBER_USER_EMAIL ?? "operations@edispark.test",
    LOCAL_MEMBER_USER_PASSWORD: process.env.LOCAL_MEMBER_USER_PASSWORD ?? "EdiSpark-Member-2026!",
    LOCAL_MEMBER_USER_NAME: process.env.LOCAL_MEMBER_USER_NAME ?? "Operations User",
    DOCUMENTS_API_URL: process.env.DOCUMENTS_API_URL ?? "http://localhost:8080",
    MFT_API_URL: process.env.MFT_API_URL ?? "http://localhost:8081",
    INTEGRATION_API_URL: process.env.INTEGRATION_API_URL ?? "http://localhost:8083",
    AS2_SIMULATOR_URL: process.env.AS2_SIMULATOR_URL ?? "http://localhost:8082",
    MFT_ALLOW_PRIVATE_NETWORKS: process.env.MFT_ALLOW_PRIVATE_NETWORKS ?? "true",
    LOCAL_SEED_TENANT_ID: process.env.LOCAL_SEED_TENANT_ID ?? seedTenantId,
    DYNAMODB_LOCAL_DATA_DIR: process.env.DYNAMODB_LOCAL_DATA_DIR ?? resolve(localDirectory, "dynamodb"),
    DYNAMODB_LOCAL_EMBEDDED: process.env.DYNAMODB_LOCAL_EMBEDDED ?? "true",
    MFT_LOCAL_DEMO_PARTNER_ID_FILE: process.env.MFT_LOCAL_DEMO_PARTNER_ID_FILE ?? resolve(localDirectory, "mft-demo-partner-id"),
    SPRING_PROFILES_ACTIVE: process.env.SPRING_PROFILES_ACTIVE ?? "local",
  }
}

export function workspaceDirectory() {
  return workspace
}

async function readSettings() {
  try {
    const settings = JSON.parse(await readFile(settingsPath, "utf8"))
    if (settings.internalServiceToken) return settings
    const updated = { ...settings, internalServiceToken: randomBytes(48).toString("base64url") }
    await writeFile(settingsPath, `${JSON.stringify(updated, null, 2)}\n`, { mode: 0o600 })
    return updated
  } catch (error) {
    if (error.code !== "ENOENT") throw error
    const settings = {
      betterAuthSecret: randomBytes(48).toString("base64url"),
      internalServiceToken: randomBytes(48).toString("base64url"),
    }
    await writeFile(settingsPath, `${JSON.stringify(settings, null, 2)}\n`, { mode: 0o600 })
    return settings
  }
}

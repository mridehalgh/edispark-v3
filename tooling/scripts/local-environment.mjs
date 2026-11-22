import { createHmac, randomBytes } from "node:crypto"
import { mkdir, readFile, writeFile } from "node:fs/promises"
import { resolve } from "node:path"
import { as2LabPaths } from "./as2-lab.mjs"

const workspace = resolve(import.meta.dirname, "../..")
const localDirectory = resolve(workspace, ".local")
const settingsPath = resolve(localDirectory, "settings.json")
const seedTenantId = "2OnHVzeAJKwm0ThFLcy1HfLHGPm"

export async function localEnvironment() {
  await mkdir(localDirectory, { recursive: true })
  const settings = await readSettings()
  const as2Lab = as2LabPaths()
  return {
    ...process.env,
    BETTER_AUTH_SECRET: process.env.BETTER_AUTH_SECRET ?? settings.betterAuthSecret,
    EDISPARK_INTERNAL_TOKEN: process.env.EDISPARK_INTERNAL_TOKEN ?? settings.internalServiceToken,
    EDISPARK_AGENT_ENABLED: process.env.EDISPARK_AGENT_ENABLED ?? "true",
    EDISPARK_AGENT_GUARDRAIL: process.env.EDISPARK_AGENT_GUARDRAIL ?? settings.deskGuardrail?.arn ?? "",
    EDISPARK_AGENT_VERSION: process.env.EDISPARK_AGENT_VERSION ?? settings.deskGuardrail?.version ?? "",
    AWS_PROFILE: process.env.AWS_PROFILE ?? settings.deskGuardrail?.awsProfile,
    EDISPARK_MCP_ORGANIZATIONS: process.env.EDISPARK_MCP_ORGANIZATIONS ?? "local-test-workspace",
    EDISPARK_MCP_ACCESS_SECRET: process.env.EDISPARK_MCP_ACCESS_SECRET
      ?? createHmac("sha256", settings.internalServiceToken).update("edispark-local-mcp").digest("base64url"),
    BETTER_AUTH_URL: process.env.BETTER_AUTH_URL ?? "http://localhost:3000",
    BETTER_AUTH_DATABASE_PATH: process.env.BETTER_AUTH_DATABASE_PATH ?? resolve(localDirectory, "auth.db"),
    BETTER_AUTH_AUTO_MIGRATE: process.env.BETTER_AUTH_AUTO_MIGRATE ?? "true",
    LOCAL_TEST_USER_BOOTSTRAP: process.env.LOCAL_TEST_USER_BOOTSTRAP ?? "true",
    LOCAL_TEST_USER_EMAIL: process.env.LOCAL_TEST_USER_EMAIL ?? "local@edispark.test",
    LOCAL_TEST_USER_PASSWORD: process.env.LOCAL_TEST_USER_PASSWORD ?? "EdiSpark-Local-2026!",
    LOCAL_PLATFORM_USER_EMAIL: process.env.LOCAL_PLATFORM_USER_EMAIL ?? "platform@edispark.test",
    LOCAL_PLATFORM_USER_PASSWORD: process.env.LOCAL_PLATFORM_USER_PASSWORD ?? "EdiSpark-Platform-Local-2026!",
    LOCAL_TEST_USER_NAME: process.env.LOCAL_TEST_USER_NAME ?? "Local Tester",
    LOCAL_MEMBER_USER_EMAIL: process.env.LOCAL_MEMBER_USER_EMAIL ?? "operations@edispark.test",
    LOCAL_MEMBER_USER_PASSWORD: process.env.LOCAL_MEMBER_USER_PASSWORD ?? "EdiSpark-Member-2026!",
    LOCAL_MEMBER_USER_NAME: process.env.LOCAL_MEMBER_USER_NAME ?? "Operations User",
    EDISPARK_API_URL: process.env.EDISPARK_API_URL ?? "http://localhost:8080",
    DOCUMENTS_API_URL: process.env.EDISPARK_API_URL ?? process.env.DOCUMENTS_API_URL ?? "http://localhost:8080",
    MFT_API_URL: process.env.EDISPARK_API_URL ?? process.env.MFT_API_URL ?? "http://localhost:8080",
    INTEGRATION_API_URL: process.env.EDISPARK_API_URL ?? process.env.INTEGRATION_API_URL ?? "http://localhost:8080",
    AS2_SIMULATOR_URL: process.env.AS2_SIMULATOR_URL ?? "http://localhost:8082",
    MFT_ALLOW_PRIVATE_NETWORKS: process.env.MFT_ALLOW_PRIVATE_NETWORKS ?? "true",
    MFT_PUBLIC_AS2_URL: process.env.MFT_PUBLIC_AS2_URL ?? "http://localhost:8080/as2",
    MFT_PUBLIC_AS2_MDN_URL: process.env.MFT_PUBLIC_AS2_MDN_URL ?? "http://localhost:8080/as2/mdn",
    MFT_LOCAL_IDENTITY_PATH: process.env.MFT_LOCAL_IDENTITY_PATH ?? as2Lab.edisparkIdentity,
    MFT_LOCAL_IDENTITY_PASSWORD_FILE: process.env.MFT_LOCAL_IDENTITY_PASSWORD_FILE ?? as2Lab.passwordFile,
    MFT_LOCAL_PARTNER_CERTIFICATE_PATH: process.env.MFT_LOCAL_PARTNER_CERTIFICATE_PATH ?? as2Lab.simulatorCertificate,
    LOCAL_SEED_TENANT_ID: process.env.LOCAL_SEED_TENANT_ID ?? seedTenantId,
    DYNAMODB_LOCAL_DATA_DIR: process.env.DYNAMODB_LOCAL_DATA_DIR ?? resolve(localDirectory, "dynamodb"),
    DYNAMODB_LOCAL_NATIVE_DIR: process.env.DYNAMODB_LOCAL_NATIVE_DIR ?? resolve(workspace, "tooling/local-runtime/target/dependencies"),
    DYNAMODB_LOCAL_EMBEDDED: process.env.DYNAMODB_LOCAL_EMBEDDED ?? "true",
    MFT_LOCAL_DEMO_PARTNER_ID_FILE: process.env.MFT_LOCAL_DEMO_PARTNER_ID_FILE ?? resolve(localDirectory, "mft-demo-partner-id"),
    MFT_LOCAL_DEMO_TRADACOMS_PARTNER_ID_FILE: process.env.MFT_LOCAL_DEMO_TRADACOMS_PARTNER_ID_FILE ?? resolve(localDirectory, "mft-demo-tradacoms-partner-id"),
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

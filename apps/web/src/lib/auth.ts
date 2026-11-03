import { betterAuth } from "better-auth"
import { passkey } from "@better-auth/passkey"
import { sso } from "@better-auth/sso"
import { jwt, lastLoginMethod } from "better-auth/plugins"
import { organization } from "better-auth/plugins/organization"
import { twoFactor } from "better-auth/plugins/two-factor"
import Database from "better-sqlite3"
import { randomBytes } from "node:crypto"
import { mkdirSync } from "node:fs"
import { dirname } from "node:path"

import { sendTransactionalEmail } from "@/lib/email"
import { createKmsJwtSigner, getRemoteJwtSigningConfig } from "@/lib/jwt-signing"
import { isOrganizationRole, type AccessContext, type PlatformRole } from "@/lib/access"

const databasePath = process.env.BETTER_AUTH_DATABASE_PATH ?? "auth.db"
mkdirSync(dirname(databasePath), { recursive: true })
const database = new Database(databasePath)
const KSUID_EPOCH_SECONDS = 1_400_000_000
const KSUID_LENGTH = 27
const BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
const DEFAULT_LOCAL_SEED_TENANT_ID = "2OnHVzeAJKwm0ThFLcy1HfLHGPm"
const LOCAL_TEST_ORGANIZATION_ID = "local-test-workspace"
const LOCAL_TEST_MEMBER_ID = "local-test-owner"
const LOCAL_OPERATIONS_MEMBER_ID = "local-test-operations"

database.exec(`
  CREATE TABLE IF NOT EXISTS platform_principal (
    user_id TEXT PRIMARY KEY NOT NULL,
    role TEXT NOT NULL CHECK (role = 'platform_admin'),
    created_at TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
  )
`)

function encodeBase62(bytes: Buffer) {
  let value = BigInt(`0x${bytes.toString("hex")}`)
  let encoded = ""

  while (value > 0n) {
    encoded = BASE62_ALPHABET[Number(value % 62n)] + encoded
    value /= 62n
  }

  return encoded.padStart(KSUID_LENGTH, "0")
}

function createTenantId(existingTenantCount: number) {
  const localSeedTenantId = process.env.LOCAL_SEED_TENANT_ID ?? DEFAULT_LOCAL_SEED_TENANT_ID
  if (process.env.NODE_ENV !== "production" && existingTenantCount === 0) return localSeedTenantId

  const bytes = Buffer.allocUnsafe(20)
  bytes.writeUInt32BE(Math.floor(Date.now() / 1000) - KSUID_EPOCH_SECONDS)
  randomBytes(16).copy(bytes, 4)
  return encodeBase62(bytes)
}

let resolveTenantIdTransaction: ((organizationId: string) => string) | undefined

function resolveTenantId(organizationId: string) {
  if (!resolveTenantIdTransaction) {
    database.exec(`
      CREATE TABLE IF NOT EXISTS organization_tenant (
        organization_id TEXT PRIMARY KEY NOT NULL,
        tenant_id TEXT UNIQUE NOT NULL,
        created_at TEXT NOT NULL,
        FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE
      )
    `)
    const findTenantId = database.prepare(
      "SELECT tenant_id AS tenantId FROM organization_tenant WHERE organization_id = ?",
    )
    const insertTenantId = database.prepare(
      "INSERT OR IGNORE INTO organization_tenant (organization_id, tenant_id, created_at) VALUES (?, ?, ?)",
    )
    const countTenants = database.prepare("SELECT COUNT(*) AS count FROM organization_tenant")

    resolveTenantIdTransaction = database.transaction((id: string) => {
      const existing = findTenantId.get(id) as { tenantId: string } | undefined
      if (existing) return existing.tenantId

      const { count } = countTenants.get() as { count: number }
      insertTenantId.run(id, createTenantId(count), new Date().toISOString())
      const created = findTenantId.get(id) as { tenantId: string } | undefined
      if (!created) throw new Error("Unable to provision a tenant for the active organization")
      return created.tenantId
    })
  }

  return resolveTenantIdTransaction(organizationId)
}

function ensureLocalOrganizationTenantMapping() {
  const tenantId = process.env.LOCAL_SEED_TENANT_ID ?? DEFAULT_LOCAL_SEED_TENANT_ID
  database.exec(`
    CREATE TABLE IF NOT EXISTS organization_tenant (
      organization_id TEXT PRIMARY KEY NOT NULL,
      tenant_id TEXT UNIQUE NOT NULL,
      created_at TEXT NOT NULL,
      FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE
    )
  `)
  const owner = database.prepare(
    "SELECT organization_id AS organizationId FROM organization_tenant WHERE tenant_id = ?",
  ).get(tenantId) as { organizationId: string } | undefined
  if (owner && owner.organizationId !== LOCAL_TEST_ORGANIZATION_ID) {
    throw new Error("The local seed tenant belongs to another workspace. Run `pnpm local:reset`.")
  }
  database.prepare(`
    INSERT INTO organization_tenant (organization_id, tenant_id, created_at)
    VALUES (?, ?, ?)
    ON CONFLICT(organization_id) DO UPDATE SET tenant_id = excluded.tenant_id
  `).run(LOCAL_TEST_ORGANIZATION_ID, tenantId, new Date().toISOString())
}

const appUrl = process.env.BETTER_AUTH_URL ?? "http://localhost:3000"
const appOrigin = new URL(appUrl).origin
const passkeyRpId = process.env.BETTER_AUTH_PASSKEY_RP_ID ?? new URL(appUrl).hostname
const passkeyOrigins = process.env.BETTER_AUTH_PASSKEY_ORIGINS
  ?.split(",")
  .map((origin) => origin.trim())
  .filter(Boolean) ?? [appOrigin]
const remoteJwtSigning = getRemoteJwtSigningConfig()

function defineJwtPayload({
  user,
  session,
}: {
  user: Record<string, unknown>
  session: Record<string, unknown>
}) {
  const organizationId = session.activeOrganizationId
  if (typeof organizationId !== "string" || !organizationId) {
    throw new Error("An active organization is required to issue a JWT")
  }

  const userId = typeof user.id === "string" ? user.id : undefined
  if (!userId) throw new Error("A user identifier is required to issue a JWT")

  const membership = database.prepare(
    "SELECT role FROM member WHERE organizationId = ? AND userId = ?",
  ).get(organizationId, userId) as { role: string } | undefined
  if (!membership || !isOrganizationRole(membership.role)) {
    throw new Error("Active organization membership is required to issue a JWT")
  }

  const platformPrincipal = database.prepare(
    "SELECT role FROM platform_principal WHERE user_id = ?",
  ).get(userId) as { role: PlatformRole } | undefined

  return {
    ...user,
    organizationId,
    tenant_id: resolveTenantId(organizationId),
    organization_role: membership.role,
    ...(platformPrincipal ? { platform_role: platformPrincipal.role } : {}),
  }
}

export const auth = betterAuth({
  appName: "EDI Spark",
  database,
  baseURL: appUrl,
  trustedOrigins: [appUrl, ...(process.env.BETTER_AUTH_TRUSTED_ORIGINS?.split(",").filter(Boolean) ?? [])],
  user: {
    changeEmail: {
      enabled: true,
      sendChangeEmailConfirmation: async ({ user, newEmail, url }) => {
        await sendTransactionalEmail({
          to: user.email,
          subject: "Approve your EDI Spark email change",
          text: `Approve changing your sign-in email to ${newEmail}: ${url}`,
          html: `<p>Approve changing your EDI Spark sign-in email to ${newEmail}:</p><p><a href="${url}">Approve email change</a></p>`,
        })
      },
    },
    deleteUser: {
      enabled: true,
      sendDeleteAccountVerification: async ({ user, url }) => {
        await sendTransactionalEmail({
          to: user.email,
          subject: "Confirm deletion of your EDI Spark account",
          text: `Permanently delete your EDI Spark account: ${url}`,
          html: `<p>You requested permanent deletion of your EDI Spark account.</p><p><a href="${url}">Confirm account deletion</a></p>`,
        })
      },
    },
  },
  emailAndPassword: {
    enabled: true,
    minPasswordLength: 12,
    maxPasswordLength: 256,
    resetPasswordTokenExpiresIn: 60 * 30,
    revokeSessionsOnPasswordReset: true,
    sendResetPassword: async ({ user, url }) => {
      await sendTransactionalEmail({
        to: user.email,
        subject: "Reset your EDI Spark password",
        text: `Use this link to reset your password: ${url}`,
        html: `<p>Use this link to reset your EDI Spark password:</p><p><a href="${url}">Reset password</a></p>`,
      })
    },
  },
  session: {
    expiresIn: 60 * 60 * 24 * 7,
    updateAge: 60 * 60 * 24,
    freshAge: 60 * 30,
  },
  emailVerification: {
    sendOnSignUp: true,
    sendVerificationEmail: async ({ user, url }) => {
      await sendTransactionalEmail({
        to: user.email,
        subject: "Verify your EDI Spark email address",
        text: `Verify your email address: ${url}`,
        html: `<p>Verify your EDI Spark email address:</p><p><a href="${url}">Verify email</a></p>`,
      })
    },
  },
  rateLimit: {
    enabled: true,
    storage: "database",
    customRules: {
      "/sign-in/email": { window: 60, max: 5 },
      "/sign-up/email": { window: 60, max: 3 },
      "/request-password-reset": { window: 60, max: 3 },
      "/change-email": { window: 60, max: 3 },
      "/change-password": { window: 60, max: 3 },
      "/delete-user": { window: 60, max: 3 },
    },
  },
  advanced: {
    useSecureCookies: process.env.NODE_ENV === "production",
  },
  plugins: [
    jwt(remoteJwtSigning
      ? {
          jwks: {
            remoteUrl: remoteJwtSigning.jwksUrl,
            keyPairConfig: { alg: "ES256" },
          },
          jwt: {
            definePayload: defineJwtPayload,
            sign: createKmsJwtSigner(remoteJwtSigning),
          },
        }
      : {
          jwks: {
            keyPairConfig: { alg: "ES256" },
          },
          jwt: {
            definePayload: defineJwtPayload,
          },
        }),
    lastLoginMethod({
      storeInDatabase: true,
      beforeStoreCookie: () => false,
      customResolveMethod: (context) =>
        context.path === "/passkey/verify-authentication" ? "passkey" : null,
    }),
    passkey({
      rpID: passkeyRpId,
      rpName: "EDI Spark",
      origin: passkeyOrigins,
      authenticatorSelection: {
        residentKey: "preferred",
        userVerification: "required",
      },
    }),
    twoFactor({
      issuer: "EDI Spark",
      backupCodeOptions: {
        amount: 10,
        length: 10,
        storeBackupCodes: "encrypted",
      },
      totpOptions: {
        digits: 6,
        period: 30,
      },
    }),
    organization({
      allowUserToCreateOrganization: true,
      organizationLimit: 5,
      membershipLimit: 100,
      invitationExpiresIn: 60 * 60 * 24 * 7,
      cancelPendingInvitationsOnReInvite: true,
      sendInvitationEmail: async ({ email, organization, inviter, invitation }) => {
        const url = new URL("/invite", appUrl)
        url.searchParams.set("id", invitation.id)
        await sendTransactionalEmail({
          to: email,
          subject: `Join the ${organization.name} workspace on EDI Spark`,
          text: `${inviter.user.name} invited you to join the ${organization.name} workspace. Accept the invitation: ${url}`,
          html: `<p>${inviter.user.name} invited you to join the ${organization.name} workspace.</p><p><a href="${url}">Accept invitation</a></p>`,
        })
      },
    }),
    sso({
      providersLimit: 20,
      organizationProvisioning: {
        disabled: false,
        defaultRole: "member",
      },
      saml: {
        enableInResponseToValidation: true,
        allowIdpInitiated: false,
        requestTTL: 5 * 60 * 1000,
      },
    }),
  ],
})

async function prepareLocalAuth() {
  const context = await auth.$context
  await context.runMigrations()

  if (process.env.LOCAL_TEST_USER_BOOTSTRAP !== "true") return

  const owner = await ensureLocalUser(
    process.env.LOCAL_TEST_USER_EMAIL,
    process.env.LOCAL_TEST_USER_PASSWORD,
    process.env.LOCAL_TEST_USER_NAME ?? "Local Tester",
  )
  const operations = await ensureLocalUser(
    process.env.LOCAL_MEMBER_USER_EMAIL,
    process.env.LOCAL_MEMBER_USER_PASSWORD,
    process.env.LOCAL_MEMBER_USER_NAME ?? "Operations User",
  )

  const createdAt = new Date().toISOString()
  database.prepare(`
    INSERT OR IGNORE INTO organization (id, name, slug, createdAt)
    VALUES (?, ?, ?, ?)
  `).run(LOCAL_TEST_ORGANIZATION_ID, "EdiSpark Local", "local-test-workspace", createdAt)
  database.prepare(`
    INSERT OR IGNORE INTO member (id, organizationId, userId, role, createdAt)
    VALUES (?, ?, ?, ?, ?)
  `).run(LOCAL_TEST_MEMBER_ID, LOCAL_TEST_ORGANIZATION_ID, owner.id, "owner", createdAt)
  database.prepare(`
    INSERT INTO member (id, organizationId, userId, role, createdAt)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET role = excluded.role
  `).run(LOCAL_OPERATIONS_MEMBER_ID, LOCAL_TEST_ORGANIZATION_ID, operations.id, "member", createdAt)
  ensureLocalOrganizationTenantMapping()
}

async function ensureLocalUser(email: string | undefined, password: string | undefined, name: string) {
  if (!email || !password) throw new Error("Local test-user credentials are not configured")
  const existing = database.prepare("SELECT id FROM user WHERE email = ?").get(email) as { id: string } | undefined
  if (!existing) await auth.api.signUpEmail({ body: { email, password, name } })
  database.prepare("UPDATE user SET emailVerified = 1 WHERE email = ?").run(email)
  const user = database.prepare("SELECT id FROM user WHERE email = ?").get(email) as { id: string } | undefined
  if (!user) throw new Error("Unable to provision the local test user")
  return user
}

export function activateLocalTestWorkspace(email = process.env.LOCAL_TEST_USER_EMAIL) {
  if (process.env.NODE_ENV !== "development" || process.env.LOCAL_TEST_USER_BOOTSTRAP !== "true") {
    throw new Error("Local test login is unavailable")
  }

  const user = database.prepare("SELECT id FROM user WHERE email = ?").get(email) as { id: string } | undefined
  if (!user) throw new Error("Local test user is unavailable")

  database.prepare(`
    UPDATE session
    SET activeOrganizationId = ?
    WHERE userId = ? AND activeOrganizationId IS NULL
  `).run(LOCAL_TEST_ORGANIZATION_ID, user.id)
}

export async function getRequestAccess(headers: Headers): Promise<AccessContext | null> {
  const result = await auth.api.getSession({ headers })
  const session = result?.session as { activeOrganizationId?: string } | undefined
  const user = result?.user as { id?: string } | undefined
  if (!session?.activeOrganizationId || !user?.id) return null

  const membership = database.prepare(
    "SELECT role FROM member WHERE organizationId = ? AND userId = ?",
  ).get(session.activeOrganizationId, user.id) as { role: string } | undefined
  if (!membership || !isOrganizationRole(membership.role)) return null

  const platformPrincipal = database.prepare(
    "SELECT role FROM platform_principal WHERE user_id = ?",
  ).get(user.id) as { role: PlatformRole } | undefined

  return {
    organizationRole: membership.role,
    platformRole: platformPrincipal?.role,
  }
}

export const authReady = process.env.BETTER_AUTH_AUTO_MIGRATE === "true"
  ? prepareLocalAuth()
  : Promise.resolve()

export type AuthSession = typeof auth.$Infer.Session

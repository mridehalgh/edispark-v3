import { betterAuth } from "better-auth"
import { APIError } from "better-auth/api"
import { oauthProvider } from "@better-auth/oauth-provider"
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
import { isOrganizationRole, type AccessContext, type OrganizationRole, type PlatformRole } from "@/lib/access"
import { createAuthPool, createDsqlAccessStore } from "@/lib/auth-dsql"

const productionRuntime = process.env.NODE_ENV === "production" && process.env.NEXT_PHASE !== "phase-production-build"
if (productionRuntime && (process.env.BETTER_AUTH_AUTO_MIGRATE === "true" || process.env.LOCAL_TEST_USER_BOOTSTRAP === "true")) {
  throw new Error("Local authentication and automatic migrations are forbidden in production")
}
const databasePath = process.env.BETTER_AUTH_DATABASE_PATH ?? "auth.db"
if (!productionRuntime) mkdirSync(dirname(databasePath), { recursive: true })
const localDatabase = productionRuntime ? undefined : new Database(databasePath)
const database = localDatabase!
const pool = productionRuntime ? createAuthPool() : undefined
const dsqlAccess = pool ? createDsqlAccessStore(pool) : undefined
const KSUID_EPOCH_SECONDS = 1_400_000_000
const KSUID_LENGTH = 27
const BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
const DEFAULT_LOCAL_SEED_TENANT_ID = "2OnHVzeAJKwm0ThFLcy1HfLHGPm"
const LOCAL_TEST_ORGANIZATION_ID = "local-test-workspace"
const LOCAL_TEST_MEMBER_ID = "local-test-owner"
const LOCAL_OPERATIONS_MEMBER_ID = "local-test-operations"

localDatabase?.exec(`
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

export const mcpEnabled = process.env.EDISPARK_MCP_ENABLED === "true"
  && process.env.NEXT_PHASE !== "phase-production-build"
export const mcpResource = process.env.EDISPARK_MCP_RESOURCE ?? "http://localhost:8080/mcp"
export const mcpScopes = ["edi:evidence", "edi:preview", "mft:evidence", "edi:commercial"] as const
const enabledMcpOrganizations = new Set(process.env.EDISPARK_MCP_ORGANIZATIONS?.split(",").map(value => value.trim()) ?? [])
const localMcpClient = "edispark-local-mcp"
const localMcpSmokeClient = "edispark-local-mcp-smoke"

async function mcpTenant(organizationId: string, userId: string) {
  if (!mcpEnabled || !enabledMcpOrganizations.has(organizationId)) return null
  if (pool) {
    const result = await pool.query<{ tenant_id: string }>(
      `SELECT t.tenant_id FROM auth.organization_tenant t JOIN auth.member m ON m."organizationId" = t.organization_id
        WHERE t.organization_id = $1 AND m."userId" = $2 AND m.role IN ('admin', 'owner')`,
      [organizationId, userId],
    )
    return result.rows[0]?.tenant_id ?? null
  }
  const member = database.prepare(
    `SELECT t.tenant_id FROM organization_tenant t JOIN member m ON m.organizationId = t.organization_id
      WHERE t.organization_id = ? AND m.userId = ? AND m.role IN ('admin', 'owner')`,
  ).get(organizationId, userId) as { tenant_id: string } | undefined
  return member?.tenant_id ?? null
}

async function requireMcpTenant(organizationId: unknown, userId: string) {
  const tenant = typeof organizationId === "string" ? await mcpTenant(organizationId, userId) : null
  if (!tenant) throw new APIError("FORBIDDEN", { message: "MCP access is unavailable for this workspace." })
  return tenant
}


async function defineJwtPayload({
  user,
  session,
}: {
  user: Record<string, unknown>
  session: Record<string, unknown>
}) {
  const userId = typeof user.id === "string" ? user.id : undefined
  if (!userId) throw new Error("A user identifier is required to issue a JWT")
  const platformRole = await findPlatformRole(userId)
  const organizationId = session.activeOrganizationId
  const membership = typeof organizationId === "string" && organizationId
    ? dsqlAccess ? await dsqlAccess.membership(organizationId, userId) : database.prepare(
      "SELECT role FROM member WHERE organizationId = ? AND userId = ?",
    ).get(organizationId, userId) as { role: string } | undefined : undefined
  if (typeof organizationId !== "string" || !membership || !isOrganizationRole(membership.role)) {
    if (platformRole) return { sub: userId, platform_role: platformRole }
    throw new Error("Active organization membership is required to issue a JWT")
  }

  const tenantId = dsqlAccess ? await dsqlAccess.resolveTenantId(organizationId, createTenantId(1)) : resolveTenantId(organizationId)
  const commercial = membership.role !== "member" && await hasMcpAccess({
    organizationId, tenantId, subject: userId, clientId: "edispark-desk", scopes: ["edi:commercial"],
  })
  return {
    ...user,
    organizationId,
    tenant_id: tenantId,
    organization_role: membership.role,
    agent_scopes: membership.role === "member" ? ["edi:evidence", "edi:preview"] : [
      "edi:evidence", "edi:preview", "mft:evidence",
      ...(commercial ? ["edi:commercial"] : []),
    ],
    ...(platformRole ? { platform_role: platformRole } : {}),
  }
}

export const auth = betterAuth({
  appName: "EDI Spark",
  database: pool ?? database,
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
    ...(mcpEnabled ? [oauthProvider({
      scopes: [...mcpScopes],
      grantTypes: ["authorization_code"],
      loginPage: "/mcp/connect/login",
      consentPage: "/mcp/connect/consent",
      accessTokenExpiresIn: 300,
      allowDynamicClientRegistration: false,
      enforcePerClientResources: true,
      silenceWarnings: { oauthAuthServerConfig: true },
      postLogin: {
        page: "/mcp/connect/consent",
        shouldRedirect: () => false,
        async consentReferenceId({ session, user }) {
          await requireMcpTenant(session.activeOrganizationId, user.id)
          return session.activeOrganizationId as string
        },
      },
      async customAccessTokenClaims({ user, referenceId, resources }) {
        if (!user || resources?.length !== 1 || resources[0] !== mcpResource) {
          throw new APIError("FORBIDDEN", { message: "MCP requires a delegated resource request." })
        }
        return {
          tenant_id: await requireMcpTenant(referenceId, user.id),
          mcp_organization: referenceId,
          mcp_delegated: true,
        }
      },
    })] : []),
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
  if (productionRuntime) throw new Error("Local auth preparation is unavailable in production")
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

  if (process.env.LOCAL_PLATFORM_USER_EMAIL && process.env.LOCAL_PLATFORM_USER_PASSWORD) {
    const platformAdmin = await ensureLocalUser(
      process.env.LOCAL_PLATFORM_USER_EMAIL,
      process.env.LOCAL_PLATFORM_USER_PASSWORD,
      "Local Platform Administrator",
    )
    database.prepare("INSERT OR IGNORE INTO platform_principal (user_id, role, created_at) VALUES (?, ?, ?)")
      .run(platformAdmin.id, "platform_admin", new Date().toISOString())
  }

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

  if (mcpEnabled) {
    const redirectUris = (process.env.EDISPARK_MCP_LOCAL_REDIRECT_URIS ?? "http://127.0.0.1:9876/callback")
      .split(",").map(value => new URL(value.trim())).map(url => {
        if (url.protocol !== "http:" || !["127.0.0.1", "localhost", "[::1]"].includes(url.hostname)
          || url.username || url.password || url.hash) throw new Error("Local MCP client requires a loopback redirect")
        return url.href
      })
    const adapter = context.adapter
    const find = (model: string, field: string, value: string) => adapter.findOne({
      model, where: [{ field, value }],
    })
    const now = new Date()
    if (!await find("oauthResource", "identifier", mcpResource)) await adapter.create({
      model: "oauthResource",
      data: { identifier: mcpResource, name: "EdiSpark local MCP", allowedScopes: [...mcpScopes], createdAt: now, updatedAt: now },
    })
    else await adapter.update({
      model: "oauthResource", where: [{ field: "identifier", value: mcpResource }],
      update: { allowedScopes: [...mcpScopes] },
    })
    for (const [clientId, name, callbacks] of [
      [localMcpClient, "EdiSpark local MCP client", redirectUris],
      [localMcpSmokeClient, "EdiSpark local MCP smoke tests", ["http://127.0.0.1:9876/callback"]],
    ] as const) {
      if (!await find("oauthClient", "clientId", clientId)) await adapter.create({
        model: "oauthClient",
        data: { clientId, name, redirectUris: [...callbacks],
          scopes: [...mcpScopes], requirePKCE: true, applicationType: "native", tokenEndpointAuthMethod: "none",
          grantTypes: ["authorization_code"], responseTypes: ["code"], createdAt: now, updatedAt: now },
      })
      else await adapter.update({
        model: "oauthClient", where: [{ field: "clientId", value: clientId }],
        update: { scopes: [...mcpScopes] },
      })
      const link = [{ field: "clientId", value: clientId }, { field: "resourceId", value: mcpResource }]
      if (!await adapter.findOne({ model: "oauthClientResource", where: link })) await adapter.create({
        model: "oauthClientResource", data: { clientId, resourceId: mcpResource, createdAt: now },
      })
    }
  }
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

async function findPlatformRole(userId: string): Promise<PlatformRole | undefined> {
  const principal = dsqlAccess ? await dsqlAccess.platformPrincipal(userId) : database.prepare(
    "SELECT role FROM platform_principal WHERE user_id = ?",
  ).get(userId) as { role: PlatformRole } | undefined
  return principal?.role
}

export async function getRequestPlatformRole(headers: Headers): Promise<PlatformRole | undefined> {
  const session = await auth.api.getSession({ headers })
  return session?.user.id ? findPlatformRole(session.user.id) : undefined
}

export async function getRequestAccess(headers: Headers, { includeMcpSetup = false } = {}): Promise<(AccessContext & { organizationRole: OrganizationRole }) | null> {
  const result = await auth.api.getSession({ headers })
  const session = result?.session as { activeOrganizationId?: string } | undefined
  const user = result?.user as { id?: string } | undefined
  if (!session?.activeOrganizationId || !user?.id) return null

  const membership = dsqlAccess ? await dsqlAccess.membership(session.activeOrganizationId, user.id) : database.prepare(
    "SELECT role FROM member WHERE organizationId = ? AND userId = ?",
  ).get(session.activeOrganizationId, user.id) as { role: string } | undefined
  if (!membership || !isOrganizationRole(membership.role)) return null

  const endpoint = includeMcpSetup && (membership.role === "admin" || membership.role === "owner")
    && await mcpTenant(session.activeOrganizationId, user.id) ? publicMcpEndpoint() : undefined

  return {
    organizationRole: membership.role,
    platformRole: await findPlatformRole(user.id),
    ...(endpoint ? { mcpSetup: { endpoint } } : {}),
  }
}

function publicMcpEndpoint() {
  const url = URL.parse(mcpResource)
  if (!url || url.pathname !== "/mcp" || url.username || url.password || url.search || url.hash) return
  if (url.protocol === "https:" || !productionRuntime && url.protocol === "http:"
    && ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)) return mcpResource
}

export async function hasMcpAccess(identity: {
  organizationId: string
  tenantId: string
  subject: string
  clientId: string
  scopes: string[]
}): Promise<boolean> {
  if (await mcpTenant(identity.organizationId, identity.subject) !== identity.tenantId) return false
  const { adapter } = await auth.$context
  const [client, resource, link, consent] = await Promise.all([
    adapter.findOne<{ disabled?: boolean; scopes?: string[] }>({
      model: "oauthClient", where: [{ field: "clientId", value: identity.clientId }],
    }),
    adapter.findOne<{ disabled?: boolean; allowedScopes?: string[] }>({
      model: "oauthResource", where: [{ field: "identifier", value: mcpResource }],
    }),
    adapter.findOne({
      model: "oauthClientResource", where: [{ field: "clientId", value: identity.clientId },
        { field: "resourceId", value: mcpResource }],
    }),
    adapter.findOne<{ scopes: string[]; resources?: string[] }>({
      model: "oauthConsent", where: [{ field: "clientId", value: identity.clientId },
        { field: "userId", value: identity.subject }, { field: "referenceId", value: identity.organizationId }],
    }),
  ])
  return Boolean(client && !client.disabled && resource && !resource.disabled && link
    && consent?.resources?.includes(mcpResource)
    && identity.scopes.every(scope => mcpScopes.some(allowed => allowed === scope) && consent.scopes.includes(scope)
      && (client.scopes?.includes(scope) ?? true) && (resource.allowedScopes?.includes(scope) ?? true)))
}

export const authReady = process.env.BETTER_AUTH_AUTO_MIGRATE === "true"
  ? prepareLocalAuth()
  : Promise.resolve()

export type AuthSession = typeof auth.$Infer.Session

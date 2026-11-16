-- Generated from the pinned Better Auth configuration. Review changes; never rewrite an applied migration.
CREATE TABLE auth."user" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "name" TEXT NOT NULL,
  "email" TEXT NOT NULL UNIQUE,
  "emailVerified" BOOLEAN NOT NULL,
  "image" TEXT,
  "createdAt" TIMESTAMPTZ NOT NULL,
  "updatedAt" TIMESTAMPTZ NOT NULL,
  "lastLoginMethod" TEXT,
  "twoFactorEnabled" BOOLEAN
);

CREATE TABLE auth."session" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "expiresAt" TIMESTAMPTZ NOT NULL,
  "token" TEXT NOT NULL UNIQUE,
  "createdAt" TIMESTAMPTZ NOT NULL,
  "updatedAt" TIMESTAMPTZ NOT NULL,
  "ipAddress" TEXT,
  "userAgent" TEXT,
  "userId" TEXT NOT NULL REFERENCES auth."user" ("id") ON DELETE CASCADE,
  "activeOrganizationId" TEXT
);

CREATE TABLE auth."account" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "accountId" TEXT NOT NULL,
  "providerId" TEXT NOT NULL,
  "userId" TEXT NOT NULL REFERENCES auth."user" ("id") ON DELETE CASCADE,
  "accessToken" TEXT,
  "refreshToken" TEXT,
  "idToken" TEXT,
  "accessTokenExpiresAt" TIMESTAMPTZ,
  "refreshTokenExpiresAt" TIMESTAMPTZ,
  "scope" TEXT,
  "password" TEXT,
  "createdAt" TIMESTAMPTZ NOT NULL,
  "updatedAt" TIMESTAMPTZ NOT NULL
);

CREATE TABLE auth."verification" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "identifier" TEXT NOT NULL,
  "value" TEXT NOT NULL,
  "expiresAt" TIMESTAMPTZ NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL,
  "updatedAt" TIMESTAMPTZ NOT NULL
);

CREATE TABLE auth."jwks" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "publicKey" TEXT NOT NULL,
  "privateKey" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL,
  "expiresAt" TIMESTAMPTZ,
  "alg" TEXT,
  "crv" TEXT
);

CREATE TABLE auth."passkey" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "name" TEXT,
  "publicKey" TEXT NOT NULL,
  "userId" TEXT NOT NULL REFERENCES auth."user" ("id") ON DELETE CASCADE,
  "credentialID" TEXT NOT NULL,
  "counter" INTEGER NOT NULL,
  "deviceType" TEXT NOT NULL,
  "backedUp" BOOLEAN NOT NULL,
  "transports" TEXT,
  "createdAt" TIMESTAMPTZ,
  "aaguid" TEXT
);

CREATE TABLE auth."twoFactor" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "secret" TEXT NOT NULL,
  "backupCodes" TEXT NOT NULL,
  "userId" TEXT NOT NULL REFERENCES auth."user" ("id") ON DELETE CASCADE,
  "verified" BOOLEAN,
  "failedVerificationCount" INTEGER,
  "lockedUntil" TIMESTAMPTZ
);

CREATE TABLE auth."organization" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "name" TEXT NOT NULL,
  "slug" TEXT NOT NULL UNIQUE,
  "logo" TEXT,
  "createdAt" TIMESTAMPTZ NOT NULL,
  "metadata" TEXT
);

CREATE TABLE auth."member" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "organizationId" TEXT NOT NULL REFERENCES auth."organization" ("id") ON DELETE CASCADE,
  "userId" TEXT NOT NULL REFERENCES auth."user" ("id") ON DELETE CASCADE,
  "role" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL
);

CREATE TABLE auth."invitation" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "organizationId" TEXT NOT NULL REFERENCES auth."organization" ("id") ON DELETE CASCADE,
  "email" TEXT NOT NULL,
  "role" TEXT,
  "status" TEXT NOT NULL,
  "expiresAt" TIMESTAMPTZ NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL,
  "inviterId" TEXT NOT NULL REFERENCES auth."user" ("id") ON DELETE CASCADE
);

CREATE TABLE auth."ssoProvider" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "issuer" TEXT NOT NULL,
  "oidcConfig" TEXT,
  "samlConfig" TEXT,
  "userId" TEXT NOT NULL REFERENCES auth."user" ("id") ON DELETE CASCADE,
  "providerId" TEXT NOT NULL UNIQUE,
  "organizationId" TEXT,
  "domain" TEXT NOT NULL
);

CREATE TABLE auth."rateLimit" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "key" TEXT NOT NULL UNIQUE,
  "count" INTEGER NOT NULL,
  "lastRequest" INTEGER NOT NULL
);

CREATE TABLE auth.platform_principal (user_id TEXT PRIMARY KEY REFERENCES auth."user"(id) ON DELETE CASCADE, role TEXT NOT NULL CHECK (role = 'platform_admin'), created_at TIMESTAMPTZ NOT NULL);

CREATE TABLE auth.organization_tenant (organization_id TEXT PRIMARY KEY REFERENCES auth.organization(id) ON DELETE CASCADE, tenant_id TEXT UNIQUE NOT NULL, created_at TIMESTAMPTZ NOT NULL);

CREATE INDEX ASYNC "session_userId_idx" ON auth."session" ("userId");

CREATE INDEX ASYNC "account_userId_idx" ON auth."account" ("userId");

CREATE INDEX ASYNC "verification_identifier_idx" ON auth."verification" ("identifier");

CREATE INDEX ASYNC "passkey_userId_idx" ON auth."passkey" ("userId");

CREATE INDEX ASYNC "passkey_credentialID_idx" ON auth."passkey" ("credentialID");

CREATE INDEX ASYNC "twoFactor_secret_idx" ON auth."twoFactor" ("secret");

CREATE INDEX ASYNC "twoFactor_userId_idx" ON auth."twoFactor" ("userId");

CREATE INDEX ASYNC "member_organizationId_idx" ON auth."member" ("organizationId");

CREATE INDEX ASYNC "member_userId_idx" ON auth."member" ("userId");

CREATE INDEX ASYNC "invitation_organizationId_idx" ON auth."invitation" ("organizationId");

CREATE INDEX ASYNC "invitation_email_idx" ON auth."invitation" ("email");

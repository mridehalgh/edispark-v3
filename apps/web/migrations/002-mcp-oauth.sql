-- Generated from the pinned Better Auth configuration. Review changes; never rewrite an applied migration.
CREATE TABLE auth."oauthClient" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "clientId" TEXT NOT NULL UNIQUE,
  "clientSecret" TEXT,
  "clientDiscoveryId" TEXT,
  "disabled" BOOLEAN,
  "skipConsent" BOOLEAN,
  "enableEndSession" BOOLEAN,
  "subjectType" TEXT,
  "scopes" TEXT,
  "clientCredentialsScopes" TEXT,
  "userId" TEXT REFERENCES auth."user" ("id") ON DELETE CASCADE,
  "createdAt" TIMESTAMPTZ,
  "updatedAt" TIMESTAMPTZ,
  "name" TEXT,
  "uri" TEXT,
  "icon" TEXT,
  "contacts" TEXT,
  "tos" TEXT,
  "policy" TEXT,
  "softwareId" TEXT,
  "softwareVersion" TEXT,
  "softwareStatement" TEXT,
  "redirectUris" TEXT NOT NULL,
  "postLogoutRedirectUris" TEXT,
  "backchannelLogoutUri" TEXT,
  "backchannelLogoutSessionRequired" BOOLEAN,
  "tokenEndpointAuthMethod" TEXT,
  "applicationType" TEXT,
  "jwks" TEXT,
  "jwksUri" TEXT,
  "grantTypes" TEXT,
  "responseTypes" TEXT,
  "requirePKCE" BOOLEAN,
  "dpopBoundAccessTokens" BOOLEAN,
  "referenceId" TEXT,
  "metadata" JSONB
);

CREATE TABLE auth."oauthResource" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "identifier" TEXT NOT NULL UNIQUE,
  "name" TEXT NOT NULL,
  "accessTokenTtl" INTEGER,
  "refreshTokenTtl" INTEGER,
  "signingAlgorithm" TEXT,
  "signingKeyId" TEXT,
  "allowedScopes" TEXT,
  "customClaims" JSONB,
  "dpopBoundAccessTokensRequired" BOOLEAN,
  "disabled" BOOLEAN,
  "createdAt" TIMESTAMPTZ,
  "updatedAt" TIMESTAMPTZ,
  "policyVersion" INTEGER,
  "metadata" JSONB
);

CREATE TABLE auth."oauthClientResource" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "clientId" TEXT NOT NULL REFERENCES auth."oauthClient" ("clientId") ON DELETE CASCADE,
  "resourceId" TEXT NOT NULL REFERENCES auth."oauthResource" ("identifier") ON DELETE CASCADE,
  "metadata" JSONB,
  "createdAt" TIMESTAMPTZ
);

CREATE TABLE auth."oauthRefreshToken" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "token" TEXT NOT NULL UNIQUE,
  "clientId" TEXT NOT NULL REFERENCES auth."oauthClient" ("clientId") ON DELETE CASCADE,
  "sessionId" TEXT REFERENCES auth."session" ("id") ON DELETE SET NULL,
  "userId" TEXT NOT NULL REFERENCES auth."user" ("id") ON DELETE CASCADE,
  "referenceId" TEXT,
  "authorizationCodeId" TEXT,
  "resources" TEXT,
  "requestedUserInfoClaims" TEXT,
  "expiresAt" TIMESTAMPTZ NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL,
  "revoked" TIMESTAMPTZ,
  "rotatedAt" TIMESTAMPTZ,
  "rotationReplayResponse" TEXT,
  "rotationReplayExpiresAt" TIMESTAMPTZ,
  "authTime" TIMESTAMPTZ,
  "confirmation" JSONB,
  "scopes" TEXT NOT NULL
);

CREATE TABLE auth."oauthAccessToken" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "token" TEXT NOT NULL UNIQUE,
  "clientId" TEXT NOT NULL REFERENCES auth."oauthClient" ("clientId") ON DELETE CASCADE,
  "sessionId" TEXT REFERENCES auth."session" ("id") ON DELETE SET NULL,
  "userId" TEXT REFERENCES auth."user" ("id") ON DELETE CASCADE,
  "referenceId" TEXT,
  "authorizationCodeId" TEXT,
  "resources" TEXT,
  "requestedUserInfoClaims" TEXT,
  "refreshId" TEXT REFERENCES auth."oauthRefreshToken" ("id") ON DELETE CASCADE,
  "expiresAt" TIMESTAMPTZ NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL,
  "revoked" TIMESTAMPTZ,
  "confirmation" JSONB,
  "scopes" TEXT NOT NULL
);

CREATE TABLE auth."oauthConsent" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "clientId" TEXT NOT NULL REFERENCES auth."oauthClient" ("clientId") ON DELETE CASCADE,
  "userId" TEXT REFERENCES auth."user" ("id") ON DELETE CASCADE,
  "referenceId" TEXT,
  "resources" TEXT,
  "requestedUserInfoClaims" TEXT,
  "scopes" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ NOT NULL,
  "updatedAt" TIMESTAMPTZ NOT NULL
);

CREATE TABLE auth."oauthClientAssertion" (
  "id" TEXT PRIMARY KEY NOT NULL,
  "expiresAt" TIMESTAMPTZ NOT NULL
);

CREATE INDEX ASYNC "oauthClient_userId_idx" ON auth."oauthClient" ("userId");

CREATE INDEX ASYNC "oauthClientResource_clientId_idx" ON auth."oauthClientResource" ("clientId");

CREATE INDEX ASYNC "oauthClientResource_resourceId_idx" ON auth."oauthClientResource" ("resourceId");

CREATE INDEX ASYNC "oauthRefreshToken_clientId_idx" ON auth."oauthRefreshToken" ("clientId");

CREATE INDEX ASYNC "oauthRefreshToken_sessionId_idx" ON auth."oauthRefreshToken" ("sessionId");

CREATE INDEX ASYNC "oauthRefreshToken_userId_idx" ON auth."oauthRefreshToken" ("userId");

CREATE INDEX ASYNC "oauthRefreshToken_authorizationCodeId_idx" ON auth."oauthRefreshToken" ("authorizationCodeId");

CREATE INDEX ASYNC "oauthAccessToken_clientId_idx" ON auth."oauthAccessToken" ("clientId");

CREATE INDEX ASYNC "oauthAccessToken_sessionId_idx" ON auth."oauthAccessToken" ("sessionId");

CREATE INDEX ASYNC "oauthAccessToken_userId_idx" ON auth."oauthAccessToken" ("userId");

CREATE INDEX ASYNC "oauthAccessToken_authorizationCodeId_idx" ON auth."oauthAccessToken" ("authorizationCodeId");

CREATE INDEX ASYNC "oauthAccessToken_refreshId_idx" ON auth."oauthAccessToken" ("refreshId");

CREATE INDEX ASYNC "oauthConsent_clientId_idx" ON auth."oauthConsent" ("clientId");

CREATE INDEX ASYNC "oauthConsent_userId_idx" ON auth."oauthConsent" ("userId");

CREATE UNIQUE INDEX ASYNC "oauthClientResource_client_resource_idx" ON auth."oauthClientResource" ("clientId", "resourceId");

CREATE UNIQUE INDEX ASYNC "oauthConsent_client_user_reference_idx" ON auth."oauthConsent" ("clientId", "userId", "referenceId");

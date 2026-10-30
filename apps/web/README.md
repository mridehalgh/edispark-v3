# EdiSpark Web

The Next.js application provides the EdiSpark user interface and the Better Auth server used by the local documents API.

Run the complete system from the repository root with `pnpm dev`; the root README contains setup, architecture, environment, and troubleshooting guidance.

## Authentication

EDI Spark uses Better Auth for password, two-factor, workspace, session, passkey, and organization SSO authentication.

- [Production passkey configuration](./PASSKEYS.md)
- [JWT signing and local diagnostics](./JWT.md)

### Organization SSO

Workspace owners and admins can register separate OIDC or SAML providers from **Workspaces → Single sign-on**. Each provider is linked to the active organization and its company email domain. Successful first-time SSO users are added to that organization with the `member` role.

Before deploying the SSO plugin against a new database, apply the Better Auth schema:

```bash
npx @better-auth/cli@latest migrate --config src/lib/auth.ts --yes
```

SAML sign-in validates `InResponseTo` for five minutes and rejects IdP-initiated responses. Signed assertions are required; signed AuthnRequests can be enabled per provider with its service-provider private key. Never commit identity-provider certificates containing private material, client secrets, or private keys.

## Web-only development

From the repository root:

```bash
pnpm dev:web
```

This command supplies safe local defaults and automatically migrates the generated `.local/auth.db`. It expects the documents API at `http://localhost:8080`; use `pnpm dev` when you need both sides.

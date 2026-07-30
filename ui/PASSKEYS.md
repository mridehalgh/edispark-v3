# Production passkey configuration

EDI Spark uses Better Auth's passkey plugin to provide phishing-resistant, passwordless authentication with WebAuthn. Users can create and manage passkeys under **Your account → Security**, then choose **Sign in with a passkey** on the sign-in page.

Passkeys are bound to a relying-party ID and an allowed browser origin. Treat the production domain as permanent identity infrastructure: changing it can require every user to enrol a new passkey.

## Before deploying

Production must provide:

- A stable public hostname, such as `app.example.com`
- HTTPS with a valid certificate
- A persistent production database
- A strong Better Auth secret
- Exact public URLs, without internal container or load-balancer addresses

WebAuthn works only in a secure browser context. HTTPS is mandatory outside the special `localhost` development environment. An IP address, an internal hostname, or plain HTTP production URL will not work reliably.

## Environment variables

Configure these values in the production runtime:

```dotenv
BETTER_AUTH_URL=https://app.example.com
BETTER_AUTH_SECRET=[generate-a-secret-of-at-least-32-random-characters]

BETTER_AUTH_PASSKEY_RP_ID=app.example.com
BETTER_AUTH_PASSKEY_ORIGINS=https://app.example.com

BETTER_AUTH_TRUSTED_ORIGINS=https://app.example.com
BETTER_AUTH_DATABASE_PATH=/persistent-data/edispark-auth.db
```

Generate the secret outside the repository:

```bash
openssl rand -base64 32
```

Never commit the generated value. Store it in the production platform's secret manager.

### `BETTER_AUTH_URL`

Set this to the canonical, user-facing application URL:

```dotenv
BETTER_AUTH_URL=https://app.example.com
```

Do not use an internal service URL such as `http://web:3000`. Better Auth uses this value for callbacks, cookies, email links, and default passkey configuration.

### `BETTER_AUTH_PASSKEY_RP_ID`

This is a hostname, not a URL. It must not contain a protocol, port, path, or trailing slash:

```dotenv
BETTER_AUTH_PASSKEY_RP_ID=app.example.com
```

By default, EDI Spark uses the hostname from `BETTER_AUTH_URL`.

The relying-party ID must equal the browser hostname or be a registrable parent domain:

| Browser origin | Valid RP ID |
| --- | --- |
| `https://app.example.com` | `app.example.com` |
| `https://app.example.com` | `example.com` |
| `https://example.com` | `example.com` |
| `https://app.example.com` | Not `login.example.net` |

Use the exact application hostname unless passkeys intentionally need to work across trusted subdomains. Using `example.com` allows credentials to be scoped to that parent domain, so only do this when every relevant subdomain is under the same security control.

### `BETTER_AUTH_PASSKEY_ORIGINS`

This is a comma-separated allowlist of exact browser origins. Each value includes the protocol and optional non-default port, but no path:

```dotenv
BETTER_AUTH_PASSKEY_ORIGINS=https://app.example.com
```

Multiple intentional origins are supported:

```dotenv
BETTER_AUTH_PASSKEY_ORIGINS=https://app.example.com,https://admin.example.com
```

Do not add wildcard origins. Do not include preview deployments unless users are expected to create separate preview credentials.

### Staging and preview environments

Use a separate RP ID and origin for staging:

```dotenv
BETTER_AUTH_URL=https://staging.example.com
BETTER_AUTH_PASSKEY_RP_ID=staging.example.com
BETTER_AUTH_PASSKEY_ORIGINS=https://staging.example.com
```

Passkeys enrolled on staging will not work in production, which is normally the safest behaviour.

Avoid enabling passkey enrolment on short-lived, randomly named preview deployments. A passkey registered against a preview hostname remains bound to that hostname after the deployment disappears.

## Database requirements

The passkey migration creates a `passkey` table containing public credentials, counters, authenticator metadata, and the owning user ID. Private passkey material never reaches EDI Spark; it remains on the user's device or passkey provider.

Apply migrations against the production database during a controlled deployment step:

```bash
pnpm dlx @better-auth/cli@latest migrate --config src/lib/auth.ts
```

Before migrating:

1. Back up the authentication database.
2. Confirm the command is using the production environment variables.
3. Run the migration once, before enabling traffic on the new release.
4. Confirm the `passkey` table exists.

For SQLite:

```bash
sqlite3 "$BETTER_AUTH_DATABASE_PATH" ".schema passkey"
```

### Important SQLite deployment constraint

The current application uses `better-sqlite3`. `BETTER_AUTH_DATABASE_PATH` must point to durable storage shared by the running application instance.

Do not use the default `auth.db` path on an ephemeral or serverless filesystem. Restarts or deployments could lose accounts, sessions, two-factor secrets, and passkeys. Multi-instance deployments also require a database architecture that every instance can access consistently.

Before deploying to a serverless or horizontally scaled platform, migrate Better Auth to a supported shared database adapter such as managed PostgreSQL. A single VM or container is acceptable only when its SQLite database lives on a backed-up persistent volume and the deployment model prevents unsafe concurrent writers.

## Proxy and TLS configuration

TLS may terminate at a reverse proxy, but the browser must always see the configured HTTPS origin.

Verify that:

- Requests preserve the public `Host`
- The application knows the original protocol was HTTPS
- The proxy does not rewrite `/api/auth/*` to another public origin
- Authentication cookies remain secure and first-party
- `BETTER_AUTH_URL` matches what users see in the address bar

EDI Spark forces secure Better Auth cookies when `NODE_ENV=production`.

## Production rollout

Use this order:

1. Back up the production authentication database.
2. Configure the production URL, RP ID, origins, database, email, and secret values.
3. Apply the Better Auth migration.
4. Deploy the application behind HTTPS.
5. Verify password sign-in before enrolling any passkey.
6. Enrol a test passkey from **Your account → Security**.
7. Sign out completely.
8. Sign in with that passkey.
9. Rename and remove a test passkey.
10. Test recovery through password reset and, when enabled, two-step verification.

Roll out to internal users first. Keep password sign-in and account recovery available while adoption is monitored.

## Last sign-in method

EDI Spark uses Better Auth's last-login-method plugin to record whether an authenticated user most recently signed in with email and password or a passkey. The value is shown under **Your account → Profile**.

The value is persisted in the authentication database. The plugin's client-readable cookie is disabled because Better Auth classifies it as non-essential for GDPR purposes. Do not enable that cookie to annotate the signed-out login page until the product has a consent signal that can be checked by `beforeStoreCookie`.

## Verification checklist

### Service health

```bash
curl -i https://app.example.com/api/auth/ok
```

Expected response:

```json
{"ok":true}
```

### Browser checks

- The page is served over HTTPS without certificate warnings.
- **Add passkey** opens the operating-system or browser WebAuthn prompt.
- Cancelling shows a useful recovery message.
- A newly created passkey appears in the account's passkey list.
- The passkey can be renamed.
- **Sign in with a passkey** works after signing out.
- Conditional passkey autofill appears in a supported browser.
- Removing a passkey prevents that credential from signing in.
- Password sign-in remains available as a recovery path.

Test at least:

- Safari on a current Apple device
- Chrome or Edge on a current desktop platform
- A synced passkey provider, if the organisation permits one
- A hardware security key, if it is part of the support policy

## Domain changes

Do not casually change `BETTER_AUTH_PASSKEY_RP_ID`.

Existing credentials cannot be silently moved to an unrelated RP ID. For a planned domain migration:

1. Keep the old domain operational.
2. Ask users to sign in on the old domain.
3. Provide a controlled flow to enrol a credential for the new domain.
4. Maintain password or recovery access throughout the migration.
5. Retire the old RP only after adoption is confirmed.

Changing only the application path does not affect passkeys. Changing the hostname can.

## Secret rotation

Passkey public credentials do not depend on the Better Auth secret, but sessions, WebAuthn challenges, and encrypted two-factor recovery data do.

Plan secret rotation as a security event:

- Expect active sessions to be invalidated.
- Confirm two-factor encrypted data remains recoverable under the chosen rotation strategy.
- Do not overwrite the old secret without a tested recovery and rotation plan.

## Troubleshooting

### “We couldn't find a passkey for EDI Spark on this device”

- Confirm the user selected the correct passkey provider or security key.
- Confirm the browser hostname matches the configured RP ID.
- Try the device or password manager where the credential was enrolled.
- Use email and password to recover access.

### The browser does not show a passkey prompt

- Confirm the page is HTTPS or `localhost`.
- Confirm the browser supports WebAuthn.
- Confirm the request is not embedded in a restricted iframe.
- Check that RP ID and origin values match the browser address exactly.

### Registration works locally but fails in production

The most common cause is production using an internal URL or an origin mismatch. Compare:

- Browser address-bar origin
- `BETTER_AUTH_URL`
- `BETTER_AUTH_PASSKEY_RP_ID`
- `BETTER_AUTH_PASSKEY_ORIGINS`

### Passkeys disappear after deployment

The authentication database is not persistent or different instances are reading different SQLite files. Move the database to durable shared storage and restore from backup.

### A passkey works on one subdomain but not another

The RP ID or allowed origins do not cover both subdomains. Only broaden the RP ID to a parent domain after reviewing the security ownership of every participating subdomain.

## Relevant implementation

- Server configuration: `src/lib/auth.ts`
- Client plugin: `src/lib/auth-client.ts`
- Account management UI: `src/app/account/settings.tsx`
- Sign-in UI: `src/components/auth/auth-pages.tsx`
- User-facing error translation: `src/lib/passkey-errors.ts`

Reference: [Better Auth passkey documentation](https://better-auth.com/docs/plugins/passkey).

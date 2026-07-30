# JWT signing

EDI Spark uses Better Auth's JWT plugin to issue short-lived, JWKS-verifiable tokens from
`GET /api/auth/token`.

Every token is scoped to the session's active workspace through the required `organizationId`
claim. Better Auth validates workspace membership when the active organization is selected. Token
issuance fails when the session has no active organization, preventing accidentally unscoped
access. Resource services must compare `organizationId` with the tenant owning the requested data;
they must not accept a tenant identifier supplied separately by the caller.

## Local development

In non-production environments, Better Auth creates and stores a local ES256 keypair in the
authentication database. After signing in, open `/debug/jwt` to issue, inspect, copy, and verify
a token. The diagnostics route is omitted from production builds.

After enabling the JWT plugin against an existing local database, apply its schema:

```bash
pnpm dlx @better-auth/cli@latest migrate --config src/lib/auth.ts --yes
```

## Production with AWS KMS

Production fails at startup unless all remote-signing settings are present:

```dotenv
BETTER_AUTH_JWT_JWKS_URL=https://auth.example.com/.well-known/jwks.json
BETTER_AUTH_JWT_KID=[public-key-id]
BETTER_AUTH_JWT_KMS_KEY_ID=[kms-key-id-or-arn]
AWS_REGION=eu-west-2
```

The KMS key must be an asymmetric `ECC_NIST_P256` signing key and the application identity needs
only `kms:Sign` for that key. The signer sends a SHA-256 digest to KMS with
`ECDSA_SHA_256`; private key material never enters the application.

`BETTER_AUTH_JWT_JWKS_URL` must be HTTPS and serve every public key needed to validate unexpired
tokens, including the JWK whose `kid` matches `BETTER_AUTH_JWT_KID`. Publish the P-256 public key
with `alg: "ES256"` and `use: "sig"`. During rotation, publish both old and new public keys until
all tokens signed by the old key have expired.

The runtime uses the standard AWS credential provider chain. Prefer workload identity or an
instance/task role; do not configure long-lived AWS access keys in the repository.

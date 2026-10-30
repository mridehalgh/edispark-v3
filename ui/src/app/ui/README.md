# EDI Spark UI

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

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/basic-features/font-optimization) to automatically optimize and load Inter, a custom Google Font.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js/) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/deployment) for more details.

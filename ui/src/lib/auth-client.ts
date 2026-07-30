import { createAuthClient } from "better-auth/react"
import { passkeyClient } from "@better-auth/passkey/client"
import { ssoClient } from "@better-auth/sso/client"
import { jwtClient, lastLoginMethodClient, organizationClient, twoFactorClient } from "better-auth/client/plugins"

export const authClient = createAuthClient({
  plugins: [
    jwtClient(),
    lastLoginMethodClient(),
    passkeyClient(),
    twoFactorClient({ twoFactorPage: "/two-factor" }),
    organizationClient(),
    ssoClient(),
  ],
})

import { betterAuth } from "better-auth"
import { passkey } from "@better-auth/passkey"
import { sso } from "@better-auth/sso"
import { jwt, lastLoginMethod } from "better-auth/plugins"
import { organization } from "better-auth/plugins/organization"
import { twoFactor } from "better-auth/plugins/two-factor"
import Database from "better-sqlite3"

import { sendTransactionalEmail } from "@/lib/email"
import { createKmsJwtSigner, getRemoteJwtSigningConfig } from "@/lib/jwt-signing"

const database = new Database(process.env.BETTER_AUTH_DATABASE_PATH ?? "auth.db")

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

  return {
    ...user,
    organizationId,
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

export type AuthSession = typeof auth.$Infer.Session

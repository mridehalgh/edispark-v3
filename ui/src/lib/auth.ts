import { betterAuth } from "better-auth"
import { organization } from "better-auth/plugins/organization"
import Database from "better-sqlite3"

import { sendTransactionalEmail } from "@/lib/email"

const database = new Database(process.env.BETTER_AUTH_DATABASE_PATH ?? "auth.db")

const appUrl = process.env.BETTER_AUTH_URL ?? "http://localhost:3000"

export const auth = betterAuth({
  appName: "EDI Spark",
  database,
  baseURL: appUrl,
  trustedOrigins: [appUrl, ...(process.env.BETTER_AUTH_TRUSTED_ORIGINS?.split(",").filter(Boolean) ?? [])],
  emailAndPassword: {
    enabled: true,
    sendResetPassword: async ({ user, url }) => {
      await sendTransactionalEmail({
        to: user.email,
        subject: "Reset your EDI Spark password",
        text: `Use this link to reset your password: ${url}`,
        html: `<p>Use this link to reset your EDI Spark password:</p><p><a href="${url}">Reset password</a></p>`,
      })
    },
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
      "/forget-password": { window: 60, max: 3 },
    },
  },
  advanced: {
    useSecureCookies: process.env.NODE_ENV === "production",
  },
  plugins: [
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
          subject: `Join ${organization.name} on EDI Spark`,
          text: `${inviter.user.name} invited you to join ${organization.name}. Accept the invitation: ${url}`,
          html: `<p>${inviter.user.name} invited you to join ${organization.name}.</p><p><a href="${url}">Accept invitation</a></p>`,
        })
      },
    }),
  ],
})

export type AuthSession = typeof auth.$Infer.Session

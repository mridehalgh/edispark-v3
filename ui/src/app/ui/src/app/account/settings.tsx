"use client"

import { FormEvent, useEffect, useState } from "react"
import {
  AlertTriangle,
  Check,
  Copy,
  Fingerprint,
  KeyRound,
  Laptop,
  LogOut,
  MailCheck,
  MonitorSmartphone,
  ShieldCheck,
  Smartphone,
  Trash2,
  UserRound,
} from "lucide-react"
import { getAuthenticatorName, type Passkey } from "@better-auth/passkey"
import QRCode from "qrcode"
import Image from "next/image"
import { useNavigate } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { authClient } from "@/lib/auth-client"
import { getPasskeyError } from "@/lib/passkey-errors"
import { cn } from "@/lib/utils"

type Notice = { kind: "error" | "success"; text: string } | null
type SessionInfo = {
  id: string
  token: string
  createdAt: Date | string
  expiresAt: Date | string
  ipAddress?: string | null
  userAgent?: string | null
}
type TwoFactorSetup = { totpURI: string; backupCodes: string[] } | null

const getError = (error: unknown, fallback: string) =>
  error && typeof error === "object" && "message" in error ? String(error.message) : fallback

const initials = (name: string) =>
  name.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase()

const loginMethodLabel = (method?: string | null) => {
  if (method === "passkey") return "Passkey"
  if (method === "email") return "Email and password"
  return method ? method.replace(/-/g, " ") : null
}

function describeDevice(userAgent?: string | null) {
  if (!userAgent) return { name: "Unknown device", detail: "Browser details unavailable", mobile: false }
  const browser = userAgent.includes("Edg/") ? "Microsoft Edge"
    : userAgent.includes("Firefox/") ? "Firefox"
      : userAgent.includes("Chrome/") ? "Chrome"
        : userAgent.includes("Safari/") ? "Safari"
          : "Web browser"
  const system = userAgent.includes("iPhone") ? "iPhone"
    : userAgent.includes("iPad") ? "iPad"
      : userAgent.includes("Android") ? "Android"
        : userAgent.includes("Mac OS") ? "macOS"
          : userAgent.includes("Windows") ? "Windows"
            : userAgent.includes("Linux") ? "Linux"
              : "Unknown system"
  return { name: `${browser} on ${system}`, detail: system, mobile: /iPhone|iPad|Android/.test(userAgent) }
}

function formatDate(value: Date | string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
}

export function AccountSettingsPage() {
  const { data: session } = authClient.useSession()
  const navigate = useNavigate()
  const [notice, setNotice] = useState<Notice>(null)
  const [sessions, setSessions] = useState<SessionInfo[]>([])
  const [passkeys, setPasskeys] = useState<Passkey[]>([])
  const [isLoadingSessions, setIsLoadingSessions] = useState(true)
  const [isLoadingPasskeys, setIsLoadingPasskeys] = useState(true)
  const [pendingAction, setPendingAction] = useState<string | null>(null)
  const [twoFactorSetup, setTwoFactorSetup] = useState<TwoFactorSetup>(null)
  const [qrCode, setQrCode] = useState<string | null>(null)
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(Boolean(session?.user.twoFactorEnabled))
  const [showDeleteAccount, setShowDeleteAccount] = useState(false)

  useEffect(() => {
    setTwoFactorEnabled(Boolean(session?.user.twoFactorEnabled))
  }, [session?.user.twoFactorEnabled])

  useEffect(() => {
    let active = true
    void authClient.listSessions().then((result) => {
      if (!active) return
      if (result.error) setNotice({ kind: "error", text: getError(result.error, "We couldn’t load your signed-in devices.") })
      else setSessions((result.data ?? []) as SessionInfo[])
      setIsLoadingSessions(false)
    })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    void authClient.passkey.listUserPasskeys().then((result) => {
      if (!active) return
      if (result.error) setNotice({ kind: "error", text: getError(result.error, "We couldn’t load your passkeys.") })
      else setPasskeys(result.data ?? [])
      setIsLoadingPasskeys(false)
    })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!twoFactorSetup) {
      setQrCode(null)
      return
    }
    let active = true
    void QRCode.toDataURL(twoFactorSetup.totpURI, { width: 208, margin: 1 })
      .then((value) => { if (active) setQrCode(value) })
    return () => { active = false }
  }, [twoFactorSetup])

  const run = async (
    key: string,
    action: () => Promise<{ error?: unknown }>,
    success: string,
  ) => {
    setNotice(null)
    setPendingAction(key)
    const result = await action()
    setPendingAction(null)
    if (result.error) {
      setNotice({ kind: "error", text: getError(result.error, "We couldn’t complete that action.") })
      return false
    }
    setNotice({ kind: "success", text: success })
    return true
  }

  async function updateProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const name = String(form.get("name")).trim()
    const email = String(form.get("email")).trim().toLowerCase()
    const profileUpdated = await run("profile", () => authClient.updateUser({ name }), "Profile updated.")
    if (!profileUpdated || email === session?.user.email) return
    await run(
      "email",
      () => authClient.changeEmail({ newEmail: email, callbackURL: `${window.location.origin}/account/settings` }),
      `Check ${session?.user.email} to approve your email change.`,
    )
  }

  async function resendVerification() {
    if (!session?.user.email) return
    await run(
      "verification",
      () => authClient.sendVerificationEmail({ email: session.user.email, callbackURL: `${window.location.origin}/account/settings` }),
      "Verification email sent.",
    )
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const newPassword = String(form.get("newPassword"))
    if (newPassword !== String(form.get("confirmPassword"))) {
      setNotice({ kind: "error", text: "New passwords do not match." })
      return
    }
    const changed = await run(
      "password",
      () => authClient.changePassword({
        currentPassword: String(form.get("currentPassword")),
        newPassword,
        revokeOtherSessions: true,
      }),
      "Password updated. Your other sessions have been signed out.",
    )
    if (changed) {
      formElement.reset()
      setSessions((current) => current.filter((item) => item.token === session?.session.token))
    }
  }

  async function startTwoFactor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const password = String(new FormData(event.currentTarget).get("password"))
    setNotice(null)
    setPendingAction("enable-2fa")
    const result = await authClient.twoFactor.enable({ password })
    setPendingAction(null)
    if (result.error) return setNotice({ kind: "error", text: getError(result.error, "We couldn’t start two-step verification.") })
    setTwoFactorSetup(result.data)
  }

  async function confirmTwoFactor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const code = String(new FormData(event.currentTarget).get("code")).replace(/\s+/g, "")
    const verified = await run(
      "verify-2fa",
      () => authClient.twoFactor.verifyTotp({ code }),
      "Two-step verification is now protecting your account.",
    )
    if (verified) {
      setTwoFactorEnabled(true)
      setTwoFactorSetup((setup) => setup ? { ...setup } : null)
    }
  }

  async function disableTwoFactor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const disabled = await run(
      "disable-2fa",
      () => authClient.twoFactor.disable({ password: String(new FormData(event.currentTarget).get("password")) }),
      "Two-step verification disabled.",
    )
    if (disabled) {
      event.currentTarget.reset()
      setTwoFactorEnabled(false)
      setTwoFactorSetup(null)
    }
  }

  async function regenerateBackupCodes(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPendingAction("backup-codes")
    setNotice(null)
    const result = await authClient.twoFactor.generateBackupCodes({
      password: String(new FormData(event.currentTarget).get("password")),
    })
    setPendingAction(null)
    if (result.error) return setNotice({ kind: "error", text: getError(result.error, "We couldn’t create new recovery codes.") })
    setTwoFactorSetup({ totpURI: "", backupCodes: result.data.backupCodes })
    setNotice({ kind: "success", text: "New recovery codes created. Your previous codes no longer work." })
    event.currentTarget.reset()
  }

  async function revokeSession(token: string) {
    const revoked = await run("session", () => authClient.revokeSession({ token }), "Device signed out.")
    if (revoked) setSessions((current) => current.filter((item) => item.token !== token))
  }

  async function addPasskey(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const name = String(new FormData(formElement).get("name")).trim()
    setPendingAction("add-passkey")
    setNotice(null)
    const result = await authClient.passkey.addPasskey({ name: name || undefined })
    setPendingAction(null)
    if (result.error) return setNotice({ kind: "error", text: getPasskeyError(result.error, "registration") })
    setPasskeys((current) => [...current, result.data])
    formElement.reset()
    setNotice({ kind: "success", text: "Passkey added. You can now use it to sign in." })
  }

  async function renamePasskey(id: string, name: string) {
    const renamed = await run(
      "rename-passkey",
      () => authClient.passkey.updatePasskey({ id, name }),
      "Passkey renamed.",
    )
    if (renamed) setPasskeys((current) => current.map((item) => item.id === id ? { ...item, name } : item))
    return renamed
  }

  async function deletePasskey(id: string) {
    const deleted = await run(
      "delete-passkey",
      () => authClient.passkey.deletePasskey({ id }),
      "Passkey removed.",
    )
    if (deleted) setPasskeys((current) => current.filter((item) => item.id !== id))
  }

  async function revokeOtherSessions() {
    const revoked = await run("sessions", () => authClient.revokeOtherSessions(), "All other devices signed out.")
    if (revoked) setSessions((current) => current.filter((item) => item.token === session?.session.token))
  }

  async function deleteAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    if (String(form.get("confirmation")) !== "DELETE") {
      setNotice({ kind: "error", text: "Type DELETE to confirm permanent account deletion." })
      return
    }
    setPendingAction("delete")
    setNotice(null)
    const result = await authClient.deleteUser({
      password: String(form.get("password")),
      callbackURL: `${window.location.origin}/sign-in`,
    })
    setPendingAction(null)
    if (result.error) return setNotice({ kind: "error", text: getError(result.error, "We couldn’t delete your account.") })
    navigate("/sign-in", { replace: true })
  }

  if (!session) return <LayoutBody className="py-8 text-sm text-muted-foreground">Loading your account…</LayoutBody>
  const lastLoginMethod = "lastLoginMethod" in session.user && typeof session.user.lastLoginMethod === "string"
    ? session.user.lastLoginMethod
    : null

  return (
    <LayoutBody className="mx-auto w-full max-w-6xl py-8">
      <header className="border-b pb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Your account</h1>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">
          Manage your personal details, sign-in security, and active devices.
        </p>
      </header>

      {notice && (
        <p
          role={notice.kind === "error" ? "alert" : "status"}
          className={cn(
            "mt-5 rounded-md px-3 py-2 text-sm",
            notice.kind === "error" ? "bg-destructive/10 text-destructive" : "bg-success/30 text-foreground",
          )}
        >
          {notice.text}
        </p>
      )}

      <Tabs defaultValue="profile" className="mt-6">
        <TabsList className="h-auto w-full justify-start gap-5 overflow-x-auto rounded-none border-b bg-transparent p-0">
          <AccountTab value="profile" icon={UserRound}>Profile</AccountTab>
          <AccountTab value="security" icon={ShieldCheck}>Security</AccountTab>
          <AccountTab value="sessions" icon={MonitorSmartphone}>Devices</AccountTab>
        </TabsList>

        <TabsContent value="profile" className="mt-7">
          <section className="max-w-2xl" aria-labelledby="profile-title">
            <div className="flex items-center gap-4 border-b pb-6">
              <Avatar className="h-14 w-14">
                <AvatarFallback className="bg-primary text-sm font-semibold text-primary-foreground">{initials(session.user.name)}</AvatarFallback>
              </Avatar>
              <div>
                <h2 id="profile-title" className="text-lg font-semibold">{session.user.name}</h2>
                <p className="text-sm text-muted-foreground">{session.user.email}</p>
                {loginMethodLabel(lastLoginMethod) && (
                  <p className="mt-1 text-xs text-muted-foreground">
                    Last signed in with <span className="font-medium text-foreground">{loginMethodLabel(lastLoginMethod)}</span>
                  </p>
                )}
              </div>
            </div>
            <form onSubmit={updateProfile} className="mt-6 space-y-5">
              <label className="grid gap-1.5 text-sm font-medium">
                Your name
                <Input required name="name" autoComplete="name" defaultValue={session.user.name} />
              </label>
              <label className="grid gap-1.5 text-sm font-medium">
                Sign-in email
                <Input required name="email" type="email" autoComplete="email" defaultValue={session.user.email} />
                <span className="text-xs font-normal leading-5 text-muted-foreground">
                  Changing this requires approval from your current email and verification of the new address.
                </span>
              </label>
              <div className="flex flex-wrap items-center gap-3">
                <Button type="submit" disabled={pendingAction === "profile" || pendingAction === "email"}>
                  {pendingAction === "profile" || pendingAction === "email" ? "Saving…" : "Save profile"}
                </Button>
                {session.user.emailVerified ? (
                  <span className="inline-flex items-center gap-1.5 text-sm text-success-foreground">
                    <MailCheck className="h-4 w-4" aria-hidden="true" /> Email verified
                  </span>
                ) : (
                  <Button type="button" variant="outline" onClick={resendVerification} disabled={pendingAction === "verification"}>
                    {pendingAction === "verification" ? "Sending…" : "Verify email"}
                  </Button>
                )}
              </div>
            </form>
          </section>
        </TabsContent>

        <TabsContent value="security" className="mt-7 space-y-10">
          <section className="max-w-2xl border-b pb-9" aria-labelledby="password-title">
            <div className="flex items-start gap-3">
              <KeyRound className="mt-0.5 h-5 w-5 text-primary" aria-hidden="true" />
              <div>
                <h2 id="password-title" className="text-base font-semibold">Password</h2>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">Use at least 12 characters. Updating it signs out your other devices.</p>
              </div>
            </div>
            <form onSubmit={changePassword} className="mt-6 grid max-w-xl gap-4 sm:grid-cols-2">
              <label className="grid gap-1.5 text-sm font-medium sm:col-span-2">Current password<Input required name="currentPassword" type="password" autoComplete="current-password" /></label>
              <label className="grid gap-1.5 text-sm font-medium">New password<Input required minLength={12} maxLength={256} name="newPassword" type="password" autoComplete="new-password" /></label>
              <label className="grid gap-1.5 text-sm font-medium">Confirm new password<Input required minLength={12} maxLength={256} name="confirmPassword" type="password" autoComplete="new-password" /></label>
              <Button className="w-fit sm:col-span-2" type="submit" disabled={pendingAction === "password"}>
                {pendingAction === "password" ? "Updating…" : "Update password"}
              </Button>
            </form>
          </section>

          <PasskeySettings
            passkeys={passkeys}
            isLoading={isLoadingPasskeys}
            pendingAction={pendingAction}
            onAdd={addPasskey}
            onRename={renamePasskey}
            onDelete={deletePasskey}
          />

          <div className="max-w-2xl border-t pt-9">
            <TwoFactorSettings
            enabled={twoFactorEnabled}
            setup={twoFactorSetup}
            qrCode={qrCode}
            pendingAction={pendingAction}
            onStart={startTwoFactor}
            onConfirm={confirmTwoFactor}
            onDisable={disableTwoFactor}
            onRegenerate={regenerateBackupCodes}
            />
          </div>

          <section className="max-w-2xl border-t pt-9" aria-labelledby="delete-account-title">
            <div className="flex items-start gap-3">
              <AlertTriangle className="mt-0.5 h-5 w-5 text-destructive" aria-hidden="true" />
              <div className="flex-1">
                <h2 id="delete-account-title" className="text-base font-semibold">Delete account</h2>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">
                  Permanently removes your personal account and access. Transfer ownership of any shared workspaces first.
                </p>
              </div>
            </div>
            {!showDeleteAccount ? (
              <Button variant="outline" className="mt-5 text-destructive hover:text-destructive" onClick={() => setShowDeleteAccount(true)}>
                <Trash2 className="mr-2 h-4 w-4" aria-hidden="true" /> Delete account
              </Button>
            ) : (
              <form onSubmit={deleteAccount} className="mt-5 max-w-xl space-y-4 rounded-md border border-destructive/30 bg-destructive/5 p-4">
                <p className="text-sm font-medium">This cannot be undone. Enter your password and type DELETE to continue.</p>
                <label className="grid gap-1.5 text-sm font-medium">Current password<Input required name="password" type="password" autoComplete="current-password" /></label>
                <label className="grid gap-1.5 text-sm font-medium">Confirmation<Input required name="confirmation" autoComplete="off" placeholder="Type DELETE" /></label>
                <div className="flex gap-2">
                  <Button type="submit" variant="destructive" disabled={pendingAction === "delete"}>
                    {pendingAction === "delete" ? "Deleting…" : "Permanently delete account"}
                  </Button>
                  <Button type="button" variant="ghost" onClick={() => setShowDeleteAccount(false)}>Keep account</Button>
                </div>
              </form>
            )}
          </section>
        </TabsContent>

        <TabsContent value="sessions" className="mt-7">
          <section aria-labelledby="devices-title">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <h2 id="devices-title" className="text-base font-semibold">Signed-in devices</h2>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">Review where your account is active and sign out devices you no longer use.</p>
              </div>
              {sessions.length > 1 && (
                <Button variant="outline" onClick={revokeOtherSessions} disabled={pendingAction === "sessions"}>
                  <LogOut className="mr-2 h-4 w-4" aria-hidden="true" />
                  {pendingAction === "sessions" ? "Signing out…" : "Sign out other devices"}
                </Button>
              )}
            </div>
            <div className="mt-6 max-w-3xl divide-y rounded-md border">
              {isLoadingSessions ? (
                <>
                  <div className="h-20 animate-pulse bg-muted/60" />
                  <div className="h-20 animate-pulse bg-muted/35" />
                </>
              ) : sessions.length === 0 ? (
                <p className="px-4 py-6 text-sm text-muted-foreground">No active devices were found.</p>
              ) : sessions.map((item) => {
                const device = describeDevice(item.userAgent)
                const current = item.token === session.session.token
                const DeviceIcon = device.mobile ? Smartphone : Laptop
                return (
                  <div key={item.id} className="flex min-h-20 items-center gap-3 px-4 py-3">
                    <span className="grid h-9 w-9 shrink-0 place-items-center rounded-md bg-muted">
                      <DeviceIcon className="h-4 w-4" aria-hidden="true" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="truncate text-sm font-medium">{device.name}</p>
                        {current && <Badge variant="secondary" className="font-medium">This device</Badge>}
                      </div>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {item.ipAddress || "IP unavailable"} · Signed in {formatDate(item.createdAt)}
                      </p>
                    </div>
                    {!current && (
                      <Button variant="ghost" size="sm" onClick={() => revokeSession(item.token)} disabled={pendingAction === "session"}>
                        Sign out
                      </Button>
                    )}
                  </div>
                )
              })}
            </div>
          </section>
        </TabsContent>
      </Tabs>
    </LayoutBody>
  )
}

function AccountTab({ value, icon: Icon, children }: { value: string; icon: typeof UserRound; children: React.ReactNode }) {
  return (
    <TabsTrigger value={value} className="relative gap-2 rounded-none px-1 pb-3 pt-1 shadow-none after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-transparent data-[state=active]:bg-transparent data-[state=active]:shadow-none data-[state=active]:after:bg-primary">
      <Icon className="h-4 w-4" aria-hidden="true" /> {children}
    </TabsTrigger>
  )
}

function PasskeySettings({
  passkeys,
  isLoading,
  pendingAction,
  onAdd,
  onRename,
  onDelete,
}: {
  passkeys: Passkey[]
  isLoading: boolean
  pendingAction: string | null
  onAdd: (event: FormEvent<HTMLFormElement>) => void
  onRename: (id: string, name: string) => Promise<boolean>
  onDelete: (id: string) => void
}) {
  const [editingId, setEditingId] = useState<string | null>(null)

  async function rename(event: FormEvent<HTMLFormElement>, id: string) {
    event.preventDefault()
    const name = String(new FormData(event.currentTarget).get("name")).trim()
    if (!name) return
    if (await onRename(id, name)) setEditingId(null)
  }

  return (
    <section className="max-w-2xl" aria-labelledby="passkeys-title">
      <div className="flex items-start gap-3">
        <Fingerprint className="mt-0.5 h-5 w-5 text-primary" aria-hidden="true" />
        <div>
          <h2 id="passkeys-title" className="text-base font-semibold">Passkeys</h2>
          <p className="mt-1 text-sm leading-6 text-muted-foreground">
            Sign in securely with your fingerprint, face, device PIN, or a hardware security key.
          </p>
        </div>
      </div>

      <form onSubmit={onAdd} className="mt-6 flex max-w-xl flex-col gap-3 sm:flex-row">
        <label className="grid flex-1 gap-1.5 text-sm font-medium">
          Passkey name
          <Input name="name" maxLength={64} placeholder="Work laptop" />
        </label>
        <Button className="self-end" type="submit" disabled={pendingAction === "add-passkey"}>
          <Fingerprint className="mr-2 h-4 w-4" aria-hidden="true" />
          {pendingAction === "add-passkey" ? "Waiting for device…" : "Add passkey"}
        </Button>
      </form>

      <div className="mt-5 divide-y rounded-md border">
        {isLoading ? (
          <div className="h-16 animate-pulse bg-muted/50" />
        ) : passkeys.length === 0 ? (
          <div className="px-4 py-5">
            <p className="text-sm font-medium">No passkeys yet</p>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">Add one to get phishing-resistant, passwordless sign-in.</p>
          </div>
        ) : passkeys.map((passkey) => {
          const label = passkey.name || getAuthenticatorName(passkey.aaguid) || "Passkey"
          return (
            <div key={passkey.id} className="flex min-h-16 flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center">
              <span className="grid h-9 w-9 shrink-0 place-items-center rounded-md bg-muted">
                <Fingerprint className="h-4 w-4" aria-hidden="true" />
              </span>
              {editingId === passkey.id ? (
                <form onSubmit={(event) => rename(event, passkey.id)} className="flex min-w-0 flex-1 gap-2">
                  <label className="sr-only">Passkey name</label>
                  <Input required name="name" maxLength={64} defaultValue={label} autoFocus />
                  <Button type="submit" size="sm" disabled={pendingAction === "rename-passkey"}>Save</Button>
                  <Button type="button" size="sm" variant="ghost" onClick={() => setEditingId(null)}>Cancel</Button>
                </form>
              ) : (
                <>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{label}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      Added {formatDate(passkey.createdAt)}
                      {passkey.backedUp ? " · Synced" : passkey.deviceType === "multiDevice" ? " · Multi-device" : " · This device"}
                    </p>
                  </div>
                  <Button type="button" size="sm" variant="ghost" onClick={() => setEditingId(passkey.id)}>Rename</Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    className="text-destructive hover:text-destructive"
                    disabled={pendingAction === "delete-passkey"}
                    onClick={() => onDelete(passkey.id)}
                  >
                    Remove
                  </Button>
                </>
              )}
            </div>
          )
        })}
      </div>
      <p className="mt-3 text-xs leading-5 text-muted-foreground">
        Keep at least one other sign-in method available before removing a passkey.
      </p>
    </section>
  )
}

function TwoFactorSettings({
  enabled,
  setup,
  qrCode,
  pendingAction,
  onStart,
  onConfirm,
  onDisable,
  onRegenerate,
}: {
  enabled: boolean
  setup: TwoFactorSetup
  qrCode: string | null
  pendingAction: string | null
  onStart: (event: FormEvent<HTMLFormElement>) => void
  onConfirm: (event: FormEvent<HTMLFormElement>) => void
  onDisable: (event: FormEvent<HTMLFormElement>) => void
  onRegenerate: (event: FormEvent<HTMLFormElement>) => void
}) {
  const [showDisable, setShowDisable] = useState(false)
  const [showRecovery, setShowRecovery] = useState(false)
  const [copied, setCopied] = useState(false)

  async function copyCodes() {
    if (!setup?.backupCodes.length) return
    await navigator.clipboard.writeText(setup.backupCodes.join("\n"))
    setCopied(true)
  }

  return (
    <section className="max-w-2xl" aria-labelledby="two-factor-title">
      <div className="flex items-start gap-3">
        <ShieldCheck className="mt-0.5 h-5 w-5 text-primary" aria-hidden="true" />
        <div className="flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id="two-factor-title" className="text-base font-semibold">Two-step verification</h2>
            <Badge variant={enabled ? "secondary" : "outline"} className="font-medium">
              {enabled ? <><Check className="mr-1 h-3.5 w-3.5" aria-hidden="true" /> On</> : "Off"}
            </Badge>
          </div>
          <p className="mt-1 text-sm leading-6 text-muted-foreground">
            Require a code from your authenticator app when signing in on a new device.
          </p>
        </div>
      </div>

      {!enabled && !setup && (
        <form onSubmit={onStart} className="mt-6 max-w-md space-y-4">
          <label className="grid gap-1.5 text-sm font-medium">
            Confirm your password
            <Input required name="password" type="password" autoComplete="current-password" />
          </label>
          <Button type="submit" disabled={pendingAction === "enable-2fa"}>
            {pendingAction === "enable-2fa" ? "Preparing…" : "Set up authenticator"}
          </Button>
        </form>
      )}

      {!enabled && setup && setup.totpURI && (
        <div className="mt-6 border-t pt-6">
          <ol className="space-y-6">
            <li>
              <p className="text-sm font-semibold">1. Scan this code with your authenticator app</p>
              <div className="mt-3 flex min-h-52 w-52 items-center justify-center rounded-md border bg-white p-2">
                {qrCode ? <Image unoptimized src={qrCode} alt="QR code for setting up EDI Spark two-step verification" width={208} height={208} /> : <span className="text-sm text-muted-foreground">Creating QR code…</span>}
              </div>
            </li>
            <li>
              <p className="text-sm font-semibold">2. Enter the six-digit code</p>
              <form onSubmit={onConfirm} className="mt-3 flex max-w-sm flex-col gap-3 sm:flex-row">
                <label className="sr-only">Authentication code</label>
                <Input required name="code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} className="font-mono tracking-widest" />
                <Button type="submit" disabled={pendingAction === "verify-2fa"}>{pendingAction === "verify-2fa" ? "Verifying…" : "Verify code"}</Button>
              </form>
            </li>
          </ol>
        </div>
      )}

      {enabled && (
        <div className="mt-6 flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => setShowRecovery((open) => !open)}>
            <KeyRound className="mr-2 h-4 w-4" aria-hidden="true" /> Create new recovery codes
          </Button>
          <Button variant="ghost" className="text-destructive hover:text-destructive" onClick={() => setShowDisable((open) => !open)}>Turn off</Button>
        </div>
      )}

      {showRecovery && enabled && (
        <form onSubmit={onRegenerate} className="mt-4 max-w-md rounded-md border bg-muted/35 p-4">
          <p className="text-sm font-medium">Creating new codes invalidates all previous recovery codes.</p>
          <label className="mt-3 grid gap-1.5 text-sm font-medium">Confirm your password<Input required name="password" type="password" autoComplete="current-password" /></label>
          <Button className="mt-3" type="submit" disabled={pendingAction === "backup-codes"}>{pendingAction === "backup-codes" ? "Creating…" : "Create new codes"}</Button>
        </form>
      )}

      {showDisable && enabled && (
        <form onSubmit={onDisable} className="mt-4 max-w-md rounded-md border border-destructive/30 bg-destructive/5 p-4">
          <p className="text-sm font-medium">Turning this off makes your account less secure.</p>
          <label className="mt-3 grid gap-1.5 text-sm font-medium">Confirm your password<Input required name="password" type="password" autoComplete="current-password" /></label>
          <Button className="mt-3" type="submit" variant="destructive" disabled={pendingAction === "disable-2fa"}>{pendingAction === "disable-2fa" ? "Turning off…" : "Turn off verification"}</Button>
        </form>
      )}

      {setup?.backupCodes.length ? (
        <div className="mt-6 rounded-md border bg-muted/35 p-4" role="region" aria-labelledby="recovery-codes-title">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h3 id="recovery-codes-title" className="text-sm font-semibold">Save your recovery codes</h3>
              <p className="mt-1 text-xs leading-5 text-muted-foreground">Each code works once. Store them somewhere secure and never share them.</p>
            </div>
            <Button type="button" variant="outline" size="sm" onClick={copyCodes}>
              {copied ? <Check className="mr-2 h-4 w-4" aria-hidden="true" /> : <Copy className="mr-2 h-4 w-4" aria-hidden="true" />}
              {copied ? "Copied" : "Copy"}
            </Button>
          </div>
          <ul className="mt-4 grid grid-cols-2 gap-x-6 gap-y-2 font-mono text-sm">
            {setup.backupCodes.map((code) => <li key={code}>{code}</li>)}
          </ul>
        </div>
      ) : null}
    </section>
  )
}

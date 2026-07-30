"use client"

import { FormEvent, useEffect, useMemo, useState } from "react"
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom"
import { Building2, CheckCircle2, Fingerprint, KeyRound, LockKeyhole, ShieldCheck } from "lucide-react"

import Logo from "@/components/logo/logo"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { authClient } from "@/lib/auth-client"
import { getPasskeyError } from "@/lib/passkey-errors"

function AuthShell({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return <main className="grid min-h-screen place-items-center bg-muted/35 px-4 py-10">
    <Card className="w-full max-w-md shadow-md">
      <CardHeader className="space-y-3">
        <Link to="/" className="flex w-fit items-center gap-2 text-primary" aria-label="EDI Spark">
          <Logo className="h-7 w-7" /><span className="font-semibold text-foreground">EDI Spark</span>
        </Link>
        <div><CardTitle>{title}</CardTitle><CardDescription className="mt-1">{description}</CardDescription></div>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  </main>
}

function ErrorMessage({ message }: { message: string | null }) {
  return message ? <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{message}</p> : null
}

function workspaceSlug(name: string) {
  const base = name.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "") || "workspace"
  return `${base}-${Math.random().toString(36).slice(2, 8)}`
}

export function SignInPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { data: session, isPending } = authClient.useSession()
  const [error, setError] = useState<string | null>(() => {
    const code = params.get("error")
    if (code === "invalid_saml_response") return "Your identity provider returned an invalid or expired sign-in response. Start again or contact your workspace administrator."
    if (code === "unsolicited_response") return "This workspace requires sign-in to start from EDI Spark. Enter your work email below."
    return code ? "Company sign-in could not be completed. Try again or contact your workspace administrator." : null
  })
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [showSSO, setShowSSO] = useState(false)

  useEffect(() => {
    if (!window.PublicKeyCredential?.isConditionalMediationAvailable) return
    let active = true
    void window.PublicKeyCredential.isConditionalMediationAvailable().then((available) => {
      if (active && available) void authClient.signIn.passkey({ autoFill: true })
    })
    return () => { active = false }
  }, [])

  if (!isPending && session) return <Navigate to="/" replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(null); setIsSubmitting(true)
    const form = new FormData(event.currentTarget)
    const result = await authClient.signIn.email({ email: String(form.get("email")), password: String(form.get("password")), callbackURL: "/" })
    setIsSubmitting(false)
    if (result.error) return setError(result.error.message ?? "Unable to sign in.")
    navigate("/workspace", { replace: true })
  }

  async function signInWithPasskey() {
    setError(null)
    setIsSubmitting(true)
    const result = await authClient.signIn.passkey()
    setIsSubmitting(false)
    if (result.error) return setError(getPasskeyError(result.error, "sign-in"))
    navigate("/workspace", { replace: true })
  }

  async function signInWithSSO(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    const email = String(new FormData(event.currentTarget).get("ssoEmail")).trim().toLowerCase()
    const result = await authClient.signIn.sso({
      email,
      callbackURL: "/workspace",
      errorCallbackURL: "/sign-in",
    })
    setIsSubmitting(false)
    if (result.error) setError(result.error.message ?? "No company sign-in was found for that email address.")
  }

  return <AuthShell title="Welcome back" description="Sign in to manage your EDI workspace.">
    <form onSubmit={submit} className="space-y-4">
      <ErrorMessage message={error} />
      <label className="grid gap-1.5 text-sm font-medium">Email<Input required name="email" type="email" autoComplete="email webauthn" /></label>
      <label className="grid gap-1.5 text-sm font-medium">Password<Input required name="password" type="password" autoComplete="current-password webauthn" /></label>
      <Button className="w-full" type="submit" disabled={isSubmitting}>{isSubmitting ? "Signing in…" : "Sign in"}</Button>
    </form>
    <div className="my-5 flex items-center gap-3 text-xs text-muted-foreground" aria-hidden="true"><span className="h-px flex-1 bg-border" /><span>or</span><span className="h-px flex-1 bg-border" /></div>
    <Button className="w-full" type="button" variant="outline" disabled={isSubmitting} onClick={signInWithPasskey}>
      <Fingerprint className="mr-2 h-4 w-4" aria-hidden="true" /> Sign in with a passkey
    </Button>
    <Button className="mt-3 w-full" type="button" variant="outline" disabled={isSubmitting} aria-expanded={showSSO} onClick={() => setShowSSO((visible) => !visible)}>
      <ShieldCheck className="mr-2 h-4 w-4" aria-hidden="true" /> Sign in with company SSO
    </Button>
    {showSSO && (
      <form onSubmit={signInWithSSO} className="mt-4 space-y-3 border-t pt-4">
        <label className="grid gap-1.5 text-sm font-medium">
          Work email
          <Input required name="ssoEmail" type="email" autoComplete="email" placeholder="[email]" />
          <span className="text-xs font-normal leading-5 text-muted-foreground">We’ll find your organization’s secure sign-in provider from your email domain.</span>
        </label>
        <Button className="w-full" type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Opening company sign-in…" : "Continue with SSO"}
        </Button>
      </form>
    )}
    <div className="mt-5 flex justify-between text-sm"><Link className="text-primary hover:underline" to="/forgot-password">Forgot password?</Link><Link className="text-primary hover:underline" to="/sign-up">Create account</Link></div>
  </AuthShell>
}

export function SignUpPage() {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [pendingOrganizationName, setPendingOrganizationName] = useState<string | null>(null)

  async function createWorkspace(organizationName: string) {
    const organization = await authClient.organization.create({ name: organizationName, slug: workspaceSlug(organizationName) })
    if (organization.error) {
      setPendingOrganizationName(organizationName)
      setError(organization.error.message ?? "Your account is ready, but we couldn't create its workspace yet.")
      return
    }
    navigate("/workspace", { replace: true })
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(null); setIsSubmitting(true)
    const form = new FormData(event.currentTarget)
    const name = String(form.get("name")).trim()
    const organizationName = String(form.get("organizationName")).trim()
    const result = await authClient.signUp.email({ name, email: String(form.get("email")), password: String(form.get("password")), callbackURL: "/" })
    if (result.error) { setIsSubmitting(false); return setError(result.error.message ?? "Unable to create your account.") }
    await createWorkspace(organizationName)
    setIsSubmitting(false)
  }
  if (pendingOrganizationName) return <AuthShell title="Finish setting up your workspace" description="Your account is ready. Create the workspace where your EDI activity will live.">
    <div className="space-y-5"><ErrorMessage message={error} /><div className="rounded-md border bg-muted/50 p-4"><div className="flex items-start gap-3"><Building2 className="mt-0.5 h-5 w-5 text-primary" aria-hidden="true" /><div><p className="font-medium">{pendingOrganizationName}</p><p className="mt-1 text-sm text-muted-foreground">This workspace keeps your documents, members, and retailer connections together.</p></div></div></div><Button className="w-full" disabled={isSubmitting} onClick={async () => { setError(null); setIsSubmitting(true); await createWorkspace(pendingOrganizationName); setIsSubmitting(false) }}>{isSubmitting ? "Creating workspace…" : "Retry workspace creation"}</Button></div>
  </AuthShell>

  return <AuthShell title="Create your EDI workspace" description="Set up the business workspace your operations team will use every day.">
    <form onSubmit={submit} className="space-y-6"><ErrorMessage message={error} />
      <section aria-labelledby="workspace-details"><div className="mb-3 flex items-center gap-2"><Building2 className="h-4 w-4 text-primary" aria-hidden="true" /><h2 id="workspace-details" className="text-sm font-semibold">Your workspace</h2></div><label className="grid gap-1.5 text-sm font-medium">Workspace name<Input required name="organizationName" autoComplete="organization" placeholder="Example Supplies" /><span className="text-xs font-normal leading-5 text-muted-foreground">This names the shared workspace for your EDI documents, retailer connections, and colleagues.</span></label></section>
      <section aria-labelledby="account-owner" className="border-t pt-5"><div className="mb-3 flex items-center gap-2"><LockKeyhole className="h-4 w-4 text-primary" aria-hidden="true" /><h2 id="account-owner" className="text-sm font-semibold">Your account</h2></div><div className="space-y-4"><label className="grid gap-1.5 text-sm font-medium">Your name<Input required name="name" autoComplete="name" /></label><label className="grid gap-1.5 text-sm font-medium">Work email<Input required name="email" type="email" autoComplete="email" /><span className="text-xs font-normal text-muted-foreground">We’ll use this to verify your account and send invitations.</span></label><label className="grid gap-1.5 text-sm font-medium">Password<Input required minLength={12} maxLength={256} name="password" type="password" autoComplete="new-password" /><span className="text-xs font-normal text-muted-foreground">Use at least 12 characters.</span></label></div></section>
      <div className="rounded-md border border-success/35 bg-success/15 px-4 py-4 text-sm"><div className="flex gap-2.5"><CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success-foreground" aria-hidden="true" /><div><p className="font-semibold">Start with everything your operations team needs.</p><p className="mt-1 leading-5 text-success-foreground">Your shared workspace is ready straight away. Invite colleagues and begin setting up retailer connections when you’re inside. No credit card required.</p></div></div></div>
      <Button className="w-full" type="submit" disabled={isSubmitting}>{isSubmitting ? "Creating your workspace…" : "Create workspace"}</Button>
    </form>
    <p className="mt-5 text-center text-sm text-muted-foreground">Already have an account? <Link className="text-primary hover:underline" to="/sign-in">Sign in</Link></p>
  </AuthShell>
}

export function ForgotPasswordPage() {
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(null); setMessage(null)
    const email = String(new FormData(event.currentTarget).get("email"))
    const result = await authClient.requestPasswordReset({ email, redirectTo: `${window.location.origin}/reset-password` })
    if (result.error) return setError(result.error.message ?? "Unable to request a password reset.")
    setMessage("If an account exists for that address, a reset link has been sent.")
  }
  return <AuthShell title="Reset your password" description="We’ll email you a secure reset link.">
    <form onSubmit={submit} className="space-y-4"><ErrorMessage message={error} />{message && <p role="status" className="rounded-md bg-success/15 px-3 py-2 text-sm">{message}</p>}
      <label className="grid gap-1.5 text-sm font-medium">Email<Input required name="email" type="email" autoComplete="email" /></label><Button className="w-full" type="submit">Send reset link</Button>
    </form><p className="mt-5 text-center text-sm"><Link className="text-primary hover:underline" to="/sign-in">Back to sign in</Link></p>
  </AuthShell>
}

export function ResetPasswordPage() {
  const [params] = useSearchParams(); const navigate = useNavigate(); const [error, setError] = useState<string | null>(null)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const token = params.get("token"); if (!token) return setError("This reset link is invalid or expired.")
    const result = await authClient.resetPassword({ token, newPassword: String(new FormData(event.currentTarget).get("password")) })
    if (result.error) return setError(result.error.message ?? "Unable to reset your password.")
    navigate("/sign-in", { replace: true })
  }
  return <AuthShell title="Choose a new password" description="Use a password with at least 12 characters."><form onSubmit={submit} className="space-y-4"><ErrorMessage message={error} /><label className="grid gap-1.5 text-sm font-medium">New password<Input required minLength={12} maxLength={256} name="password" type="password" autoComplete="new-password" /></label><Button className="w-full" type="submit">Reset password</Button></form></AuthShell>
}

export function TwoFactorPage() {
  const navigate = useNavigate()
  const [mode, setMode] = useState<"totp" | "backup">("totp")
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    const form = new FormData(event.currentTarget)
    const code = String(form.get("code")).replace(/\s+/g, "")
    const trustDevice = form.get("trustDevice") === "on"
    const result = mode === "totp"
      ? await authClient.twoFactor.verifyTotp({ code, trustDevice })
      : await authClient.twoFactor.verifyBackupCode({ code, trustDevice })
    setIsSubmitting(false)
    if (result.error) return setError(result.error.message ?? "That code could not be verified.")
    navigate("/workspace", { replace: true })
  }

  return (
    <AuthShell title="Confirm it’s you" description={mode === "totp" ? "Enter the six-digit code from your authenticator app." : "Enter one of your recovery codes."}>
      <div className="mb-5 flex rounded-md bg-muted p-1" role="tablist" aria-label="Verification method">
        <button type="button" role="tab" aria-selected={mode === "totp"} onClick={() => setMode("totp")} className={`min-h-9 flex-1 rounded-sm px-3 text-sm font-medium ${mode === "totp" ? "bg-background text-foreground" : "text-muted-foreground"}`}>Authenticator</button>
        <button type="button" role="tab" aria-selected={mode === "backup"} onClick={() => setMode("backup")} className={`min-h-9 flex-1 rounded-sm px-3 text-sm font-medium ${mode === "backup" ? "bg-background text-foreground" : "text-muted-foreground"}`}>Recovery code</button>
      </div>
      <form onSubmit={submit} className="space-y-4">
        <ErrorMessage message={error} />
        <label className="grid gap-1.5 text-sm font-medium">
          {mode === "totp" ? "Authentication code" : "Recovery code"}
          <Input
            required
            name="code"
            inputMode={mode === "totp" ? "numeric" : "text"}
            autoComplete="one-time-code"
            pattern={mode === "totp" ? "[0-9]{6}" : undefined}
            maxLength={mode === "totp" ? 6 : 32}
            className="font-mono tracking-widest"
            autoFocus
          />
        </label>
        <label className="flex min-h-11 items-center gap-2 text-sm">
          <input name="trustDevice" type="checkbox" className="h-4 w-4 rounded border-input accent-[oklch(var(--primary))]" />
          Trust this device for 30 days
        </label>
        <Button className="w-full" type="submit" disabled={isSubmitting}>
          <KeyRound className="mr-2 h-4 w-4" aria-hidden="true" />
          {isSubmitting ? "Verifying…" : "Verify and continue"}
        </Button>
      </form>
      <p className="mt-5 text-center text-sm"><Link className="text-primary hover:underline" to="/sign-in">Back to sign in</Link></p>
    </AuthShell>
  )
}

export function AuthGate({ children }: { children: React.ReactNode }) {
  const { data: session, isPending } = authClient.useSession()
  if (isPending) return <main className="grid min-h-screen place-items-center text-sm text-muted-foreground">Loading your workspace…</main>
  return session ? <>{children}</> : <Navigate to="/sign-in" replace />
}

export function WorkspaceSelectionPage() {
  const navigate = useNavigate()
  const { data: session, isPending: isSessionPending } = authClient.useSession()
  const { data: organizations, isPending: isOrganizationsPending } = authClient.useListOrganizations()
  const [error, setError] = useState<string | null>(null)
  const organizationList = useMemo(() => Array.isArray(organizations) ? organizations : [], [organizations])

  useEffect(() => {
    if (!isSessionPending && !session) navigate("/sign-in", { replace: true })
    if (!isOrganizationsPending && organizationList.length === 0) navigate("/organization", { replace: true })
    if (!isOrganizationsPending && organizationList.length === 1) {
      authClient.organization.setActive({ organizationId: organizationList[0].id }).then((result) => {
        if (result.error) setError(result.error.message ?? "Unable to select your workspace.")
        else navigate("/", { replace: true })
      })
    }
  }, [isOrganizationsPending, isSessionPending, navigate, organizationList, session])

  async function choose(organizationId: string) {
    setError(null)
    const result = await authClient.organization.setActive({ organizationId })
    if (result.error) return setError(result.error.message ?? "Unable to select your workspace.")
    navigate("/", { replace: true })
  }

  if (isSessionPending || isOrganizationsPending || organizationList.length <= 1) return <main className="grid min-h-screen place-items-center text-sm text-muted-foreground">Preparing your workspace…</main>
  return <AuthShell title="Choose a workspace" description="Select the workspace you want to work in."><div className="space-y-3"><ErrorMessage message={error} />{organizationList.map((organization) => <Button key={organization.id} variant="outline" className="h-auto w-full justify-between px-4 py-3" onClick={() => choose(organization.id)}><span className="text-left"><span className="block font-medium">{organization.name}</span><span className="block text-xs font-normal text-muted-foreground">{organization.slug}</span></span><span aria-hidden="true">→</span></Button>)}</div></AuthShell>
}

"use client"

import { FormEvent, useEffect, useMemo, useState } from "react"
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom"
import { Building2, CheckCircle2, LockKeyhole } from "lucide-react"

import Logo from "@/components/logo/logo"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { authClient } from "@/lib/auth-client"

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
  const { data: session, isPending } = authClient.useSession()
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (!isPending && session) return <Navigate to="/" replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(null); setIsSubmitting(true)
    const form = new FormData(event.currentTarget)
    const result = await authClient.signIn.email({ email: String(form.get("email")), password: String(form.get("password")), callbackURL: "/" })
    setIsSubmitting(false)
    if (result.error) return setError(result.error.message ?? "Unable to sign in.")
    navigate("/workspace", { replace: true })
  }

  return <AuthShell title="Welcome back" description="Sign in to manage your EDI workspace.">
    <form onSubmit={submit} className="space-y-4">
      <ErrorMessage message={error} />
      <label className="grid gap-1.5 text-sm font-medium">Email<Input required name="email" type="email" autoComplete="email" /></label>
      <label className="grid gap-1.5 text-sm font-medium">Password<Input required name="password" type="password" autoComplete="current-password" /></label>
      <Button className="w-full" type="submit" disabled={isSubmitting}>{isSubmitting ? "Signing in…" : "Sign in"}</Button>
    </form>
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
      <section aria-labelledby="workspace-details"><div className="mb-3 flex items-center gap-2"><Building2 className="h-4 w-4 text-primary" aria-hidden="true" /><h2 id="workspace-details" className="text-sm font-semibold">Your organization</h2></div><label className="grid gap-1.5 text-sm font-medium">Organization name<Input required name="organizationName" autoComplete="organization" placeholder="Acme Supplies" /><span className="text-xs font-normal leading-5 text-muted-foreground">This names the shared workspace for your EDI documents, retailer connections, and colleagues.</span></label></section>
      <section aria-labelledby="account-owner" className="border-t pt-5"><div className="mb-3 flex items-center gap-2"><LockKeyhole className="h-4 w-4 text-primary" aria-hidden="true" /><h2 id="account-owner" className="text-sm font-semibold">Your account</h2></div><div className="space-y-4"><label className="grid gap-1.5 text-sm font-medium">Your name<Input required name="name" autoComplete="name" /></label><label className="grid gap-1.5 text-sm font-medium">Work email<Input required name="email" type="email" autoComplete="email" /><span className="text-xs font-normal text-muted-foreground">We’ll use this to verify your account and send invitations.</span></label><label className="grid gap-1.5 text-sm font-medium">Password<Input required minLength={8} name="password" type="password" autoComplete="new-password" /></label></div></section>
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
  return <AuthShell title="Choose a new password" description="Use a password with at least eight characters."><form onSubmit={submit} className="space-y-4"><ErrorMessage message={error} /><label className="grid gap-1.5 text-sm font-medium">New password<Input required minLength={8} name="password" type="password" autoComplete="new-password" /></label><Button className="w-full" type="submit">Reset password</Button></form></AuthShell>
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
  return <AuthShell title="Choose a workspace" description="Select the organization you want to work in."><div className="space-y-3"><ErrorMessage message={error} />{organizationList.map((organization) => <Button key={organization.id} variant="outline" className="h-auto w-full justify-between px-4 py-3" onClick={() => choose(organization.id)}><span className="text-left"><span className="block font-medium">{organization.name}</span><span className="block text-xs font-normal text-muted-foreground">{organization.slug}</span></span><span aria-hidden="true">→</span></Button>)}</div></AuthShell>
}

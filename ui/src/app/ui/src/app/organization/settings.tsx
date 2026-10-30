"use client"

import { FormEvent, useCallback, useEffect, useRef, useState } from "react"
import {
  Building2,
  Check,
  ChevronRight,
  CircleUserRound,
  Copy,
  ExternalLink,
  KeyRound,
  Plus,
  Settings2,
  ShieldCheck,
  Trash2,
  UserPlus,
  Users,
  X,
} from "lucide-react"
import { Navigate } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { authClient } from "@/lib/auth-client"
import { cn } from "@/lib/utils"

type Notice = { kind: "error" | "success"; text: string } | null
type OrganizationAction = (action: () => Promise<{ error?: unknown }>, success: string) => Promise<boolean>

const getError = (error: unknown, fallback: string) =>
  error && typeof error === "object" && "message" in error ? String(error.message) : fallback

const initials = (value: string) =>
  value
    .split(/\s+/)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase()

export function OrganizationSettingsPage() {
  const { data: activeOrganization, isPending } = authClient.useActiveOrganization()
  const { data: organizations, isPending: isOrganizationsPending } = authClient.useListOrganizations()
  const { data: session } = authClient.useSession()
  const [notice, setNotice] = useState<Notice>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [isAddingOrganization, setIsAddingOrganization] = useState(false)
  const [switchingOrganizationId, setSwitchingOrganizationId] = useState<string | null>(null)
  const attemptedWorkspaceId = useRef<string | null>(null)
  const organizationList = Array.isArray(organizations) ? organizations : []
  const soleOrganization = organizationList.length === 1 ? organizationList[0] : null

  useEffect(() => {
    if (activeOrganization || !soleOrganization || attemptedWorkspaceId.current === soleOrganization.id) return

    attemptedWorkspaceId.current = soleOrganization.id
    setSwitchingOrganizationId(soleOrganization.id)
    void authClient.organization.setActive({ organizationId: soleOrganization.id }).then((result) => {
      if (result.error) setNotice({ kind: "error", text: getError(result.error, "Unable to open your workspace.") })
      setSwitchingOrganizationId(null)
    })
  }, [activeOrganization, soleOrganization])

  const currentMember = activeOrganization?.members.find((member) => member.userId === session?.user.id)
  const role = Array.isArray(currentMember?.role) ? currentMember.role[0] : currentMember?.role
  const canManage = role === "owner" || role === "admin"

  const execute: OrganizationAction = async (action, success) => {
    setNotice(null)
    const result = await action()
    if (result.error) {
      setNotice({ kind: "error", text: getError(result.error, "Unable to complete that action.") })
      return false
    }
    setNotice({ kind: "success", text: success })
    return true
  }

  async function chooseOrganization(organizationId: string) {
    if (organizationId === activeOrganization?.id) return
    setNotice(null)
    setSwitchingOrganizationId(organizationId)
    const result = await authClient.organization.setActive({ organizationId })
    if (result.error) setNotice({ kind: "error", text: getError(result.error, "Unable to switch workspace.") })
    setSwitchingOrganizationId(null)
  }

  async function createOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsCreating(true)
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const created = await execute(
      () => authClient.organization.create({
        name: String(form.get("name")).trim(),
        slug: String(form.get("slug")).trim().toLowerCase(),
      }),
      "Workspace created.",
    )
    setIsCreating(false)
    if (created) {
      formElement.reset()
      setIsAddingOrganization(false)
    }
  }

  if (isPending || isOrganizationsPending || (soleOrganization && !activeOrganization)) {
    return <OrganizationPageSkeleton label={notice?.kind === "error" ? notice.text : "Preparing your workspace…"} />
  }
  if (!activeOrganization && organizationList.length > 1) return <Navigate to="/workspace" replace />
  if (!activeOrganization) {
    return (
      <LayoutBody className="mx-auto w-full max-w-xl py-10">
        <section className="border bg-card p-6 sm:p-8" aria-labelledby="create-workspace-title">
          <Building2 className="h-8 w-8 text-primary" aria-hidden="true" />
          <h1 id="create-workspace-title" className="mt-5 text-2xl font-semibold tracking-tight">Create your workspace</h1>
          <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">
            Your workspace keeps retailer connections, EDI documents, and colleagues together in one secure place.
          </p>
          <CreateOrganizationForm onSubmit={createOrganization} isCreating={isCreating} className="mt-6" />
        </section>
      </LayoutBody>
    )
  }

  return (
    <LayoutBody className="mx-auto w-full max-w-7xl py-8">
      <header className="flex flex-col gap-4 border-b pb-6 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Workspaces</h1>
          <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">
            Switch between workspaces and manage the people and settings for each one.
          </p>
        </div>
        <Button variant="outline" onClick={() => setIsAddingOrganization((open) => !open)} aria-expanded={isAddingOrganization}>
          {isAddingOrganization ? <X className="mr-2 h-4 w-4" aria-hidden="true" /> : <Plus className="mr-2 h-4 w-4" aria-hidden="true" />}
          {isAddingOrganization ? "Cancel" : "New workspace"}
        </Button>
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

      {isAddingOrganization && (
        <section className="mt-6 border bg-muted/45 p-5 sm:p-6" aria-labelledby="new-workspace-title">
          <div className="max-w-2xl">
            <h2 id="new-workspace-title" className="text-base font-semibold">Create a separate workspace</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Use this for a different business or operating company. Your data and members remain separate.
            </p>
            <CreateOrganizationForm onSubmit={createOrganization} isCreating={isCreating} className="mt-5" />
          </div>
        </section>
      )}

      <div className="mt-6 grid gap-8 lg:grid-cols-[17rem_minmax(0,1fr)]">
        <aside aria-labelledby="your-workspaces-title">
          <div className="flex items-center justify-between px-1">
            <h2 id="your-workspaces-title" className="text-sm font-semibold">Your workspaces</h2>
            <span className="text-xs tabular-nums text-muted-foreground">{organizationList.length} of 5</span>
          </div>
          <div className="mt-2 overflow-hidden rounded-md border bg-card">
            {organizationList.map((organization) => {
              const isActive = organization.id === activeOrganization.id
              const isSwitching = switchingOrganizationId === organization.id
              return (
                <button
                  key={organization.id}
                  type="button"
                  onClick={() => chooseOrganization(organization.id)}
                  disabled={Boolean(switchingOrganizationId)}
                  aria-current={isActive ? "true" : undefined}
                  className={cn(
                    "flex min-h-16 w-full items-center gap-3 border-b px-3 py-2.5 text-left transition-colors last:border-b-0 hover:bg-accent focus-visible:relative disabled:cursor-wait",
                    isActive && "bg-accent",
                  )}
                >
                  <Avatar className="h-9 w-9 rounded-md">
                    <AvatarFallback className={cn("rounded-md text-xs font-semibold", isActive && "bg-primary text-primary-foreground")}>
                      {initials(organization.name)}
                    </AvatarFallback>
                  </Avatar>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{organization.name}</span>
                    <span className="block truncate text-xs text-muted-foreground">
                      {isSwitching ? "Switching…" : isActive ? "Current workspace" : organization.slug}
                    </span>
                  </span>
                  {isActive ? <Check className="h-4 w-4 text-primary" aria-label="Selected" /> : <ChevronRight className="h-4 w-4 text-muted-foreground" aria-hidden="true" />}
                </button>
              )
            })}
          </div>
          <p className="mt-3 px-1 text-xs leading-5 text-muted-foreground">
            Switching changes the documents, connections, and people shown across EDI Spark.
          </p>
        </aside>

        <main className="min-w-0">
          <div className="flex items-center gap-3 border-b pb-5">
            <Avatar className="h-11 w-11 rounded-md">
              <AvatarFallback className="rounded-md bg-primary text-sm font-semibold text-primary-foreground">
                {initials(activeOrganization.name)}
              </AvatarFallback>
            </Avatar>
            <div className="min-w-0">
              <h2 className="truncate text-lg font-semibold">{activeOrganization.name}</h2>
              <p className="text-sm text-muted-foreground">
                {activeOrganization.members.length} {activeOrganization.members.length === 1 ? "member" : "members"}
                {role ? ` · You are ${role === "owner" ? "the owner" : `an ${role}`}` : ""}
              </p>
            </div>
          </div>

          <Tabs defaultValue="people" className="mt-5">
            <TabsList className="h-auto w-full justify-start gap-5 rounded-none border-b bg-transparent p-0">
              <TabsTrigger value="people" className="relative gap-2 rounded-none px-1 pb-3 pt-1 shadow-none after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-transparent data-[state=active]:bg-transparent data-[state=active]:shadow-none data-[state=active]:after:bg-primary">
                <Users className="h-4 w-4" aria-hidden="true" /> People
              </TabsTrigger>
              <TabsTrigger value="settings" className="relative gap-2 rounded-none px-1 pb-3 pt-1 shadow-none after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-transparent data-[state=active]:bg-transparent data-[state=active]:shadow-none data-[state=active]:after:bg-primary">
                <Settings2 className="h-4 w-4" aria-hidden="true" /> Settings
              </TabsTrigger>
              <TabsTrigger value="sso" className="relative gap-2 rounded-none px-1 pb-3 pt-1 shadow-none after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-transparent data-[state=active]:bg-transparent data-[state=active]:shadow-none data-[state=active]:after:bg-primary">
                <KeyRound className="h-4 w-4" aria-hidden="true" /> Single sign-on
              </TabsTrigger>
            </TabsList>
            <TabsContent value="people" className="mt-6">
              <Members organization={activeOrganization} canManage={canManage} execute={execute} />
            </TabsContent>
            <TabsContent value="settings" className="mt-6">
              <OrganizationDetails organization={activeOrganization} canManage={canManage} execute={execute} />
            </TabsContent>
            <TabsContent value="sso" className="mt-6">
              <SingleSignOn organization={activeOrganization} canManage={canManage} />
            </TabsContent>
          </Tabs>
        </main>
      </div>
    </LayoutBody>
  )
}

function OrganizationPageSkeleton({ label }: { label: string }) {
  return (
    <LayoutBody className="mx-auto w-full max-w-7xl py-8" aria-live="polite">
      <div className="h-7 w-40 animate-pulse rounded bg-muted" />
      <div className="mt-3 h-4 w-80 max-w-full animate-pulse rounded bg-muted" />
      <div className="mt-8 grid gap-8 lg:grid-cols-[17rem_minmax(0,1fr)]">
        <div className="h-48 animate-pulse rounded-md bg-muted" />
        <div className="h-72 animate-pulse rounded-md bg-muted" />
      </div>
      <span className="sr-only">{label}</span>
    </LayoutBody>
  )
}

function CreateOrganizationForm({
  onSubmit,
  isCreating,
  className,
}: {
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  isCreating: boolean
  className?: string
}) {
  return (
    <form onSubmit={onSubmit} className={cn("grid gap-4 sm:grid-cols-2", className)}>
      <label className="grid gap-1.5 text-sm font-medium">
        Workspace name
        <Input required name="name" autoComplete="organization" placeholder="Example Supplies" />
      </label>
      <label className="grid gap-1.5 text-sm font-medium">
        Workspace URL name
        <Input required name="slug" pattern="[a-z0-9-]+" placeholder="example-supplies" aria-describedby="slug-help" />
        <span id="slug-help" className="text-xs font-normal text-muted-foreground">Lowercase letters, numbers, and hyphens.</span>
      </label>
      <Button className="w-fit sm:col-span-2" type="submit" disabled={isCreating}>
        {isCreating ? "Creating…" : "Create workspace"}
      </Button>
    </form>
  )
}

function OrganizationDetails({ organization, canManage, execute }: { organization: any; canManage: boolean; execute: OrganizationAction }) {
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = String(new FormData(event.currentTarget).get("name")).trim()
    await execute(
      () => authClient.organization.update({ organizationId: organization.id, data: { name } }),
      "Workspace details updated.",
    )
  }

  return (
    <section aria-labelledby="workspace-details-title">
      <div className="flex items-start gap-3">
        <ShieldCheck className="mt-0.5 h-5 w-5 text-primary" aria-hidden="true" />
        <div>
          <h3 id="workspace-details-title" className="text-base font-semibold">Workspace details</h3>
          <p className="mt-1 text-sm leading-6 text-muted-foreground">This name appears throughout EDI Spark and in member invitations.</p>
        </div>
      </div>
      <form onSubmit={submit} className="mt-6 max-w-xl">
        <label className="grid gap-1.5 text-sm font-medium">
          Workspace name
          <Input required name="name" defaultValue={organization.name} disabled={!canManage} />
        </label>
        <div className="mt-4 flex items-center gap-3">
          <Button type="submit" disabled={!canManage}>Save changes</Button>
          {!canManage && <p className="text-xs text-muted-foreground">Only owners and admins can change workspace settings.</p>}
        </div>
      </form>
    </section>
  )
}

type SSOProvider = {
  providerId: string
  type: string
  issuer: string
  domain: string
  organizationId: string | null
  spMetadataUrl: string
}

function SingleSignOn({ organization, canManage }: { organization: any; canManage: boolean }) {
  const [providers, setProviders] = useState<SSOProvider[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isAdding, setIsAdding] = useState(false)
  const [protocol, setProtocol] = useState<"oidc" | "saml">("oidc")
  const [signRequests, setSignRequests] = useState(false)
  const [confirmingProviderId, setConfirmingProviderId] = useState<string | null>(null)
  const [pendingProviderId, setPendingProviderId] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice>(null)

  const loadProviders = useCallback(async () => {
    setIsLoading(true)
    const result = await authClient.sso.providers()
    if (result.error) {
      setNotice({ kind: "error", text: getError(result.error, "Unable to load single sign-on providers.") })
    } else {
      setProviders((result.data?.providers ?? []).filter((provider: SSOProvider) => provider.organizationId === organization.id))
    }
    setIsLoading(false)
  }, [organization.id])

  useEffect(() => {
    void loadProviders()
  }, [loadProviders])

  async function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const providerId = String(form.get("providerId")).trim().toLowerCase()
    const domain = String(form.get("domain")).trim().toLowerCase().replace(/^@/, "")
    const issuer = String(form.get("issuer")).trim()
    const origin = window.location.origin
    const privateKey = String(form.get("privateKey") ?? "").trim()

    setNotice(null)
    setPendingProviderId(providerId)
    const common = { providerId, domain, issuer, organizationId: organization.id }
    const result = protocol === "oidc"
      ? await authClient.sso.register({
        ...common,
        oidcConfig: {
          clientId: String(form.get("clientId")).trim(),
          clientSecret: String(form.get("clientSecret")),
          pkce: true,
        },
      })
      : await authClient.sso.register({
        ...common,
        samlConfig: {
          entryPoint: String(form.get("entryPoint")).trim(),
          cert: String(form.get("certificate")).trim(),
          callbackUrl: `${origin}/api/auth/sso/saml2/callback/${encodeURIComponent(providerId)}`,
          wantAssertionsSigned: true,
          authnRequestsSigned: signRequests,
          signatureAlgorithm: "sha256",
          digestAlgorithm: "sha256",
          identifierFormat: "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
          spMetadata: {
            entityID: `${origin}/api/auth/sso/saml2/sp/metadata?providerId=${encodeURIComponent(providerId)}`,
            binding: "post",
            privateKey: privateKey || undefined,
          },
        },
      })
    setPendingProviderId(null)
    if (result.error) {
      setNotice({ kind: "error", text: getError(result.error, "Unable to add the single sign-on provider.") })
      return
    }
    formElement.reset()
    setIsAdding(false)
    setNotice({ kind: "success", text: `${protocol === "oidc" ? "OIDC" : "SAML"} provider added to ${organization.name}.` })
    await loadProviders()
  }

  async function remove(providerId: string) {
    setNotice(null)
    setPendingProviderId(providerId)
    const result = await authClient.sso.deleteProvider({ providerId })
    setPendingProviderId(null)
    if (result.error) {
      setNotice({ kind: "error", text: getError(result.error, "Unable to remove the single sign-on provider.") })
      return
    }
    setProviders((current) => current.filter((provider) => provider.providerId !== providerId))
    setConfirmingProviderId(null)
    setNotice({ kind: "success", text: "Single sign-on provider removed." })
  }

  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value)
      setNotice({ kind: "success", text: "Configuration URL copied." })
    } catch {
      setNotice({ kind: "error", text: "Your browser could not copy that URL. Select and copy it manually." })
    }
  }

  return (
    <section aria-labelledby="single-sign-on-title">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex max-w-2xl items-start gap-3">
          <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
          <div>
            <h3 id="single-sign-on-title" className="text-base font-semibold">Single sign-on</h3>
            <p className="mt-1 text-sm leading-6 text-muted-foreground">
              Connect each company domain to its identity provider. People who sign in through a provider are added to this workspace as members automatically.
            </p>
          </div>
        </div>
        {canManage && (
          <Button variant="outline" onClick={() => setIsAdding((open) => !open)} aria-expanded={isAdding}>
            {isAdding ? <X className="mr-2 h-4 w-4" aria-hidden="true" /> : <Plus className="mr-2 h-4 w-4" aria-hidden="true" />}
            {isAdding ? "Cancel" : "Add provider"}
          </Button>
        )}
      </div>

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

      {!canManage && (
        <p className="mt-5 rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">
          Only workspace owners and admins can configure single sign-on.
        </p>
      )}

      {isAdding && canManage && (
        <form onSubmit={register} className="mt-6 border-y bg-muted/35 px-4 py-5 sm:px-5" aria-labelledby="add-sso-provider-title">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h4 id="add-sso-provider-title" className="text-sm font-semibold">Add an identity provider</h4>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">Credentials are stored by the authentication service and are never returned to the browser.</p>
            </div>
            <div className="flex w-fit rounded-md bg-background p-1" role="tablist" aria-label="Identity provider protocol">
              {(["oidc", "saml"] as const).map((value) => (
                <button
                  key={value}
                  type="button"
                  role="tab"
                  aria-selected={protocol === value}
                  onClick={() => setProtocol(value)}
                  className={cn(
                    "min-h-9 rounded-sm px-3 text-sm font-medium transition-colors",
                    protocol === value ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
                  )}
                >
                  {value.toUpperCase()}
                </button>
              ))}
            </div>
          </div>

          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <label className="grid gap-1.5 text-sm font-medium">
              Provider ID
              <Input required name="providerId" pattern="[a-z0-9][a-z0-9-]*" placeholder="company-okta" />
              <span className="text-xs font-normal leading-5 text-muted-foreground">Lowercase letters, numbers, and hyphens. This cannot be changed later.</span>
            </label>
            <label className="grid gap-1.5 text-sm font-medium">
              Company email domain
              <Input required name="domain" inputMode="url" placeholder="company.example" />
              <span className="text-xs font-normal leading-5 text-muted-foreground">People with this email domain will be routed to this provider.</span>
            </label>
            <label className="grid gap-1.5 text-sm font-medium sm:col-span-2">
              {protocol === "oidc" ? "Issuer URL" : "Identity provider issuer"}
              <Input required name="issuer" type={protocol === "oidc" ? "url" : "text"} placeholder={protocol === "oidc" ? "https://company.okta.com" : "https://idp.example/entity"} />
            </label>

            {protocol === "oidc" ? (
              <>
                <label className="grid gap-1.5 text-sm font-medium">
                  Client ID
                  <Input required name="clientId" autoComplete="off" />
                </label>
                <label className="grid gap-1.5 text-sm font-medium">
                  Client secret
                  <Input required name="clientSecret" type="password" autoComplete="new-password" />
                </label>
              </>
            ) : (
              <>
                <label className="grid gap-1.5 text-sm font-medium sm:col-span-2">
                  SSO entry point
                  <Input required name="entryPoint" type="url" placeholder="https://idp.example/sso" />
                </label>
                <label className="grid gap-1.5 text-sm font-medium sm:col-span-2">
                  Signing certificate
                  <textarea
                    required
                    name="certificate"
                    rows={6}
                    spellCheck={false}
                    className="w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs leading-5 ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                    placeholder={"-----BEGIN CERTIFICATE-----\n…\n-----END CERTIFICATE-----"}
                  />
                  <span className="text-xs font-normal leading-5 text-muted-foreground">Paste the X.509 certificate supplied by the identity provider. Signed assertions are required.</span>
                </label>
                <label className="flex min-h-11 items-center gap-2 text-sm font-medium sm:col-span-2">
                  <input
                    name="authnRequestsSigned"
                    type="checkbox"
                    checked={signRequests}
                    onChange={(event) => setSignRequests(event.target.checked)}
                    className="h-4 w-4 rounded border-input accent-[oklch(var(--primary))]"
                  />
                  Sign authentication requests
                  <span className="font-normal text-muted-foreground">Required by some enterprise identity providers.</span>
                </label>
                {signRequests && (
                  <label className="grid gap-1.5 text-sm font-medium sm:col-span-2">
                    Service provider private key
                    <textarea
                      required
                      name="privateKey"
                      rows={6}
                      spellCheck={false}
                      autoComplete="off"
                      className="w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs leading-5 ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                      placeholder={"-----BEGIN PRIVATE KEY-----\n…\n-----END PRIVATE KEY-----"}
                    />
                    <span className="text-xs font-normal leading-5 text-muted-foreground">Used only to sign AuthnRequests. Keep this key separate from the identity provider certificate.</span>
                  </label>
                )}
              </>
            )}
          </div>
          <div className="mt-5 flex flex-wrap items-center gap-3">
            <Button type="submit" disabled={Boolean(pendingProviderId)}>
              {pendingProviderId ? "Checking provider…" : "Add provider"}
            </Button>
            <p className="text-xs leading-5 text-muted-foreground">New users receive the member role. Owners can promote them after their first sign-in.</p>
          </div>
        </form>
      )}

      <div className="mt-6">
        <div className="flex items-center justify-between">
          <h4 className="text-sm font-semibold">Connected providers</h4>
          {!isLoading && <span className="text-xs tabular-nums text-muted-foreground">{providers.length} connected</span>}
        </div>
        {isLoading ? (
          <div className="mt-2 h-24 animate-pulse rounded-md bg-muted" aria-label="Loading single sign-on providers" />
        ) : providers.length === 0 ? (
          <div className="mt-2 border-y bg-muted/25 px-4 py-7 text-center">
            <KeyRound className="mx-auto h-6 w-6 text-muted-foreground" aria-hidden="true" />
            <p className="mt-3 text-sm font-medium">No company sign-in providers yet</p>
            <p className="mx-auto mt-1 max-w-lg text-sm leading-6 text-muted-foreground">
              Add an OIDC or SAML provider to route people into this workspace using their company credentials.
            </p>
          </div>
        ) : (
          <div className="mt-2 divide-y rounded-md border">
            {providers.map((provider) => {
              const isSaml = provider.type.toLowerCase() === "saml"
              return (
                <article key={provider.providerId} className="px-4 py-4">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
                    <div className="flex min-w-0 flex-1 items-start gap-3">
                      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-md bg-accent text-primary">
                        <ShieldCheck className="h-4 w-4" aria-hidden="true" />
                      </span>
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <h5 className="font-medium">{provider.providerId}</h5>
                          <Badge variant="secondary" className="font-medium">{isSaml ? "SAML" : "OIDC"}</Badge>
                        </div>
                        <p className="mt-1 text-sm text-muted-foreground">{provider.domain}</p>
                        <p className="mt-1 truncate text-xs text-muted-foreground">{provider.issuer}</p>
                      </div>
                    </div>
                    {canManage && (confirmingProviderId === provider.providerId ? (
                      <div className="flex flex-wrap items-center gap-1" role="group" aria-label={`Confirm removal of ${provider.providerId}`}>
                        <span className="mr-1 text-xs text-destructive">Stop company sign-in?</span>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:text-destructive"
                          disabled={pendingProviderId === provider.providerId}
                          onClick={() => remove(provider.providerId)}
                        >
                          {pendingProviderId === provider.providerId ? "Removing…" : "Yes, remove"}
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => setConfirmingProviderId(null)}>Cancel</Button>
                      </div>
                    ) : (
                      <Button
                        variant="ghost"
                        size="sm"
                        className="w-fit text-destructive hover:text-destructive"
                        onClick={() => setConfirmingProviderId(provider.providerId)}
                      >
                        <Trash2 className="mr-2 h-4 w-4" aria-hidden="true" />
                        Remove
                      </Button>
                    ))}
                  </div>
                  {isSaml && provider.spMetadataUrl && (
                    <div className="mt-4 flex flex-col gap-2 border-t pt-3 sm:flex-row sm:items-center">
                      <span className="text-xs font-medium">SP metadata</span>
                      <code className="min-w-0 flex-1 truncate text-xs text-muted-foreground">{provider.spMetadataUrl}</code>
                      <div className="flex gap-1">
                        <Button variant="ghost" size="sm" onClick={() => copy(provider.spMetadataUrl)}>
                          <Copy className="mr-2 h-3.5 w-3.5" aria-hidden="true" /> Copy
                        </Button>
                        <Button asChild variant="ghost" size="sm">
                          <a href={provider.spMetadataUrl} target="_blank" rel="noreferrer">
                            <ExternalLink className="mr-2 h-3.5 w-3.5" aria-hidden="true" /> Open
                          </a>
                        </Button>
                      </div>
                    </div>
                  )}
                </article>
              )
            })}
          </div>
        )}
      </div>
    </section>
  )
}

function Members({ organization, canManage, execute }: { organization: any; canManage: boolean; execute: OrganizationAction }) {
  async function invite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const invited = await execute(
      () => authClient.organization.inviteMember({
        email: String(form.get("email")).trim(),
        role: String(form.get("role")) as "admin" | "member",
      }),
      "Invitation sent.",
    )
    if (invited) formElement.reset()
  }

  return (
    <section aria-labelledby="people-title">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 id="people-title" className="text-base font-semibold">People with access</h3>
          <p className="mt-1 text-sm leading-6 text-muted-foreground">Manage who can work with this workspace’s documents and connections.</p>
        </div>
        <Badge variant="secondary" className="w-fit font-medium">
          {organization.members.length} {organization.members.length === 1 ? "member" : "members"}
        </Badge>
      </div>

      {canManage && (
        <form onSubmit={invite} className="mt-6 grid gap-3 border-y bg-muted/35 px-4 py-4 sm:grid-cols-[minmax(0,1fr)_9rem_auto]">
          <label className="grid gap-1.5 text-sm font-medium">
            Email address
            <Input required name="email" type="email" autoComplete="email" placeholder="[email]" />
          </label>
          <label className="grid gap-1.5 text-sm font-medium">
            Role
            <Select name="role" defaultValue="member">
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="member">Member</SelectItem>
                <SelectItem value="admin">Admin</SelectItem>
              </SelectContent>
            </Select>
          </label>
          <Button type="submit" className="self-end">
            <UserPlus className="mr-2 h-4 w-4" aria-hidden="true" /> Send invite
          </Button>
        </form>
      )}

      <div className="mt-5 divide-y rounded-md border">
        {organization.members.map((member: any) => {
          const memberRole = Array.isArray(member.role) ? member.role[0] : member.role
          return (
            <div key={member.id} className="flex min-h-16 flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center">
              <Avatar className="h-9 w-9">
                <AvatarFallback className="text-xs font-medium">{initials(member.user.name || member.user.email)}</AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{member.user.name || "Unnamed member"}</p>
                <p className="truncate text-xs text-muted-foreground">{member.user.email}</p>
              </div>
              <Badge variant="outline" className="w-fit capitalize font-medium">
                {memberRole === "owner" && <CircleUserRound className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />}
                {memberRole}
              </Badge>
              {canManage && memberRole !== "owner" && (
                <div className="flex gap-1 sm:ml-2">
                  <Button variant="ghost" size="sm" onClick={() => execute(
                    () => authClient.organization.updateMemberRole({ memberId: member.id, role: memberRole === "admin" ? "member" : "admin" }),
                    "Member role updated.",
                  )}>
                    {memberRole === "admin" ? "Make member" : "Make admin"}
                  </Button>
                  <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive" onClick={() => execute(
                    () => authClient.organization.removeMember({ memberIdOrEmail: member.id }),
                    "Member removed.",
                  )}>
                    Remove
                  </Button>
                </div>
              )}
            </div>
          )
        })}
      </div>

      {organization.invitations.length > 0 && (
        <section className="mt-7" aria-labelledby="pending-invitations-title">
          <h4 id="pending-invitations-title" className="text-sm font-semibold">Pending invitations</h4>
          <div className="mt-2 divide-y rounded-md border">
            {organization.invitations.map((invitation: any) => (
              <div key={invitation.id} className="flex min-h-14 items-center gap-3 px-4 py-2.5 text-sm">
                <span className="min-w-0 flex-1 truncate">{invitation.email}</span>
                <span className="capitalize text-muted-foreground">{invitation.role}</span>
                {canManage && (
                  <Button variant="ghost" size="sm" onClick={() => execute(
                    () => authClient.organization.cancelInvitation({ invitationId: invitation.id }),
                    "Invitation cancelled.",
                  )}>
                    Cancel
                  </Button>
                )}
              </div>
            ))}
          </div>
        </section>
      )}
    </section>
  )
}

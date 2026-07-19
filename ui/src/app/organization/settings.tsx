"use client"

import { FormEvent, useEffect, useRef, useState } from "react"
import { Navigate } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { authClient } from "@/lib/auth-client"

type Notice = { kind: "error" | "success"; text: string } | null
const getError = (error: unknown, fallback: string) => error && typeof error === "object" && "message" in error ? String(error.message) : fallback

export function OrganizationSettingsPage() {
  const { data: activeOrganization, isPending } = authClient.useActiveOrganization()
  const { data: organizations, isPending: isOrganizationsPending } = authClient.useListOrganizations()
  const { data: session } = authClient.useSession()
  const [notice, setNotice] = useState<Notice>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [isActivatingWorkspace, setIsActivatingWorkspace] = useState(false)
  const attemptedWorkspaceId = useRef<string | null>(null)
  const organizationList = Array.isArray(organizations) ? organizations : []
  const soleOrganization = organizationList.length === 1 ? organizationList[0] : null

  useEffect(() => {
    if (activeOrganization || !soleOrganization || attemptedWorkspaceId.current === soleOrganization.id) return

    attemptedWorkspaceId.current = soleOrganization.id
    setIsActivatingWorkspace(true)
    void authClient.organization.setActive({ organizationId: soleOrganization.id }).then((result) => {
      if (result.error) setNotice({ kind: "error", text: getError(result.error, "Unable to open your workspace.") })
      setIsActivatingWorkspace(false)
    })
  }, [activeOrganization, soleOrganization])
  const currentMember = activeOrganization?.members.find((member) => member.userId === session?.user.id)
  const role = Array.isArray(currentMember?.role) ? currentMember?.role[0] : currentMember?.role
  const canManage = role === "owner" || role === "admin"

  const execute = async (action: () => Promise<{ error?: unknown }>, success: string) => {
    setNotice(null)
    const result = await action()
    setNotice(result.error ? { kind: "error", text: getError(result.error, "Unable to complete that action.") } : { kind: "success", text: success })
  }

  async function createOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setIsCreating(true)
    const form = new FormData(event.currentTarget)
    await execute(() => authClient.organization.create({ name: String(form.get("name")), slug: String(form.get("slug")).trim().toLowerCase() }), "Organization created.")
    setIsCreating(false)
  }

  if (isPending || isOrganizationsPending || (soleOrganization && !activeOrganization)) return <LayoutBody className="py-8 text-sm text-muted-foreground">{notice?.kind === "error" ? notice.text : isActivatingWorkspace ? "Opening your workspace…" : "Preparing your workspace…"}</LayoutBody>
  if (!activeOrganization && organizationList.length > 1) return <Navigate to="/workspace" replace />
  if (!activeOrganization) return <LayoutBody className="mx-auto w-full max-w-xl py-8"><Card><CardHeader><CardTitle>Create your workspace</CardTitle><CardDescription>Organizations keep your members and EDI workspaces separate.</CardDescription></CardHeader><CardContent><form onSubmit={createOrganization} className="space-y-4"><label className="grid gap-1.5 text-sm font-medium">Organization name<Input required name="name" placeholder="Acme Supplies" /></label><label className="grid gap-1.5 text-sm font-medium">Workspace URL name<Input required name="slug" pattern="[a-z0-9-]+" placeholder="acme-supplies" /><span className="text-xs font-normal text-muted-foreground">Use lowercase letters, numbers, and hyphens.</span></label><Button type="submit" disabled={isCreating}>{isCreating ? "Creating…" : "Create organization"}</Button></form></CardContent></Card></LayoutBody>

  return <LayoutBody className="mx-auto w-full max-w-5xl space-y-6 py-8">
    <header><h1 className="text-2xl font-semibold tracking-tight">Organization settings</h1><p className="mt-1 text-sm text-muted-foreground">Manage {activeOrganization.name} and its members.</p></header>
    {notice && <p role={notice.kind === "error" ? "alert" : "status"} className={notice.kind === "error" ? "rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive" : "rounded-md bg-success/15 px-3 py-2 text-sm text-success-foreground"}>{notice.text}</p>}
    <Card><CardHeader><CardTitle className="text-lg">Create another organization</CardTitle><CardDescription>Create a separate workspace for another business or operating context. You can own up to five organizations.</CardDescription></CardHeader><CardContent><form onSubmit={createOrganization} className="grid max-w-2xl gap-3 sm:grid-cols-2"><label className="grid gap-1.5 text-sm font-medium">Organization name<Input required name="name" placeholder="Acme Supplies" /></label><label className="grid gap-1.5 text-sm font-medium">Workspace URL name<Input required name="slug" pattern="[a-z0-9-]+" placeholder="acme-supplies" /></label><Button className="w-fit sm:col-span-2" type="submit" disabled={isCreating}>{isCreating ? "Creating…" : "Create organization"}</Button></form></CardContent></Card>
    <OrganizationDetails organization={activeOrganization} canManage={canManage} execute={execute} />
    <Members organization={activeOrganization} canManage={canManage} execute={execute} />
  </LayoutBody>
}

function OrganizationDetails({ organization, canManage, execute }: { organization: any; canManage: boolean; execute: OrganizationAction }) {
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const name = String(new FormData(event.currentTarget).get("name")); await execute(() => authClient.organization.update({ organizationId: organization.id, data: { name } }), "Organization details updated.") }
  return <Card><CardHeader><CardTitle className="text-lg">Workspace details</CardTitle><CardDescription>Only owners and admins can change these settings.</CardDescription></CardHeader><CardContent><form onSubmit={submit} className="flex max-w-xl flex-col gap-3 sm:flex-row"><label className="sr-only">Organization name</label><Input required name="name" defaultValue={organization.name} disabled={!canManage} /><Button type="submit" disabled={!canManage}>Save changes</Button></form></CardContent></Card>
}

type OrganizationAction = (action: () => Promise<{ error?: unknown }>, success: string) => Promise<void>

function Members({ organization, canManage, execute }: { organization: any; canManage: boolean; execute: OrganizationAction }) {
  async function invite(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = new FormData(event.currentTarget); await execute(() => authClient.organization.inviteMember({ email: String(form.get("email")), role: String(form.get("role")) as "admin" | "member" }), "Invitation sent."); event.currentTarget.reset() }
  return <Card><CardHeader><CardTitle className="text-lg">Members</CardTitle><CardDescription>Invite collaborators and manage their organization role.</CardDescription></CardHeader><CardContent className="space-y-5">
    {canManage && <form onSubmit={invite} className="grid gap-3 sm:grid-cols-[1fr_10rem_auto]"><label className="sr-only">Invite email</label><Input required name="email" type="email" placeholder="[email]" /><Select name="role" defaultValue="member"><SelectTrigger aria-label="Invitation role"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="member">Member</SelectItem><SelectItem value="admin">Admin</SelectItem></SelectContent></Select><Button type="submit">Invite member</Button></form>}
    <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b text-xs text-muted-foreground"><tr><th className="px-2 py-2 font-medium">Member</th><th className="px-2 py-2 font-medium">Role</th><th className="px-2 py-2 text-right font-medium">Actions</th></tr></thead><tbody>{organization.members.map((member: any) => <tr key={member.id} className="border-b last:border-0"><td className="px-2 py-3"><p className="font-medium">{member.user.name}</p><p className="text-xs text-muted-foreground">{member.user.email}</p></td><td className="px-2 py-3 capitalize">{Array.isArray(member.role) ? member.role.join(", ") : member.role}</td><td className="px-2 py-3 text-right">{canManage && member.role !== "owner" && <div className="inline-flex items-center gap-2"><Button variant="outline" size="sm" onClick={() => execute(() => authClient.organization.updateMemberRole({ memberId: member.id, role: member.role === "admin" ? "member" : "admin" }), "Member role updated.")}>{member.role === "admin" ? "Make member" : "Make admin"}</Button><Button variant="ghost" size="sm" onClick={() => execute(() => authClient.organization.removeMember({ memberIdOrEmail: member.id }), "Member removed.")}>Remove</Button></div>}</td></tr>)}</tbody></table></div>
    {organization.invitations.length > 0 && <div><h3 className="mb-2 text-sm font-semibold">Pending invitations</h3>{organization.invitations.map((invitation: any) => <div key={invitation.id} className="flex items-center justify-between border-t px-2 py-3 text-sm"><span>{invitation.email} <span className="capitalize text-muted-foreground">· {invitation.role}</span></span>{canManage && <Button variant="ghost" size="sm" onClick={() => execute(() => authClient.organization.cancelInvitation({ invitationId: invitation.id }), "Invitation cancelled.")}>Cancel</Button>}</div>)}</div>}
  </CardContent></Card>
}

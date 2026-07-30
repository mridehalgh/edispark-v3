"use client"

import { Building2, Check, ChevronDown, Plus, Settings } from "lucide-react"
import { useState } from "react"
import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { authClient } from "@/lib/auth-client"

export function OrgSwitcher() {
  const { data: organizations } = authClient.useListOrganizations()
  const { data: activeOrganization, isPending: isActiveOrganizationPending } = authClient.useActiveOrganization()
  const organizationList = Array.isArray(organizations) ? organizations : []
  const [switchingId, setSwitchingId] = useState<string | null>(null)
  const selectedOrganization = activeOrganization ?? (organizationList.length === 1 ? organizationList[0] : null)
  const label = selectedOrganization?.name ?? (isActiveOrganizationPending ? "Loading workspace…" : "Set up workspace")
  const choose = async (organizationId: string) => {
    if (organizationId === selectedOrganization?.id) return
    setSwitchingId(organizationId)
    await authClient.organization.setActive({ organizationId })
    setSwitchingId(null)
  }

  return <DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" className="h-10 gap-2 px-2 text-sm font-medium" aria-label={`Change workspace: ${label}`}><Building2 className="h-4 w-4 text-muted-foreground" aria-hidden="true" /><span className="max-w-44 truncate">{label}</span><ChevronDown className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" /></Button></DropdownMenuTrigger>
    <DropdownMenuContent align="start" className="w-72"><DropdownMenuLabel><span className="block">Workspaces</span><span className="mt-0.5 block text-xs font-normal text-muted-foreground">Switch your current workspace</span></DropdownMenuLabel><DropdownMenuSeparator />{organizationList.map((organization) => <DropdownMenuItem key={organization.id} disabled={Boolean(switchingId)} onSelect={() => choose(organization.id)} className="flex min-h-11 items-center gap-2"><span className="min-w-0 flex-1"><span className="block truncate">{organization.name}</span><span className="block truncate text-xs text-muted-foreground">{switchingId === organization.id ? "Switching…" : organization.id === selectedOrganization?.id ? "Current workspace" : organization.slug}</span></span>{organization.id === selectedOrganization?.id && <Check className="h-4 w-4 text-primary" aria-label="Selected" />}</DropdownMenuItem>)}{organizationList.length === 0 && <DropdownMenuItem asChild><Link to="/organization">Create your first workspace</Link></DropdownMenuItem>}<DropdownMenuSeparator /><DropdownMenuItem asChild><Link to="/organization"><Settings className="mr-2 h-4 w-4" />Manage workspaces</Link></DropdownMenuItem><DropdownMenuItem asChild><Link to="/organization"><Plus className="mr-2 h-4 w-4" />Create workspace</Link></DropdownMenuItem></DropdownMenuContent>
  </DropdownMenu>
}

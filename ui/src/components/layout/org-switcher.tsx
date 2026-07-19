"use client"

import { Building2, Check, ChevronDown, Plus, Settings } from "lucide-react"
import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { authClient } from "@/lib/auth-client"

export function OrgSwitcher() {
  const { data: organizations } = authClient.useListOrganizations()
  const { data: activeOrganization, isPending: isActiveOrganizationPending } = authClient.useActiveOrganization()
  const organizationList = Array.isArray(organizations) ? organizations : []
  const selectedOrganization = activeOrganization ?? (organizationList.length === 1 ? organizationList[0] : null)
  const label = selectedOrganization?.name ?? (isActiveOrganizationPending ? "Loading workspace…" : "Set up workspace")
  const choose = async (organizationId: string) => { await authClient.organization.setActive({ organizationId }) }

  return <DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" className="h-10 gap-2 px-2 text-sm font-medium" aria-label={`Change workspace: ${label}`}><Building2 className="h-4 w-4 text-muted-foreground" aria-hidden="true" /><span className="max-w-44 truncate">{label}</span><ChevronDown className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" /></Button></DropdownMenuTrigger>
    <DropdownMenuContent align="start" className="w-64"><DropdownMenuLabel>Workspaces</DropdownMenuLabel>{organizationList.map((organization) => <DropdownMenuItem key={organization.id} onSelect={() => choose(organization.id)} className="flex items-center gap-2"><span className="min-w-0 flex-1 truncate">{organization.name}</span>{organization.id === selectedOrganization?.id && <Check className="h-4 w-4 text-primary" />}</DropdownMenuItem>)}{organizationList.length === 0 && <DropdownMenuItem asChild><Link to="/organization">Create your first workspace</Link></DropdownMenuItem>}<DropdownMenuSeparator /><DropdownMenuItem asChild><Link to="/organization"><Plus className="mr-2 h-4 w-4" />Create another workspace</Link></DropdownMenuItem><DropdownMenuItem asChild><Link to="/organization"><Settings className="mr-2 h-4 w-4" />Organization settings</Link></DropdownMenuItem></DropdownMenuContent>
  </DropdownMenu>
}

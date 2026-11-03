"use client"

import { Building2, Check, ChevronDown } from "lucide-react"
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
  const label = selectedOrganization?.name ?? (isActiveOrganizationPending ? "Loading organisation…" : "Set up organisation")
  const choose = async (organizationId: string) => {
    if (organizationId === selectedOrganization?.id) return
    setSwitchingId(organizationId)
    await authClient.organization.setActive({ organizationId })
    setSwitchingId(null)
  }

  return <DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" className="h-10 gap-2 px-2 text-sm font-medium" aria-label={`Change organisation: ${label}`}><Building2 className="h-4 w-4 text-muted-foreground" aria-hidden="true" /><span className="max-w-44 truncate">{label}</span><ChevronDown className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" /></Button></DropdownMenuTrigger>
    <DropdownMenuContent align="start" className="w-72"><DropdownMenuLabel>Organisations</DropdownMenuLabel><DropdownMenuSeparator />{organizationList.map((organization) => <DropdownMenuItem key={organization.id} disabled={Boolean(switchingId)} onSelect={() => choose(organization.id)} className="flex min-h-11 items-center gap-2"><span className="min-w-0 flex-1 truncate">{switchingId === organization.id ? "Switching…" : organization.name}</span>{organization.id === selectedOrganization?.id && <Check className="h-4 w-4 text-primary" aria-label="Selected" />}</DropdownMenuItem>)}{organizationList.length === 0 && <DropdownMenuItem asChild><Link to="/workspace/setup">Create organisation</Link></DropdownMenuItem>}</DropdownMenuContent>
  </DropdownMenu>
}

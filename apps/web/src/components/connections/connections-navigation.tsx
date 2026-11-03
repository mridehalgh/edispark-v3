"use client"

import { KeyRound, Link2 } from "lucide-react"
import { NavLink } from "react-router-dom"

import { cn } from "@/lib/utils"

const links = [
  { href: "/connections", label: "Connections", icon: Link2, end: true },
  { href: "/connections/certificates", label: "Certificates", icon: KeyRound, end: false },
]

export function ConnectionsNavigation() {
  return <nav aria-label="Connection settings" className="mb-6 flex overflow-x-auto border-b">
    {links.map(({ href, label, icon: Icon, end }) => <NavLink key={href} to={href} end={end} className={({ isActive }) => cn("relative flex min-h-11 shrink-0 items-center gap-2 px-3 text-sm font-medium text-muted-foreground after:absolute after:inset-x-3 after:bottom-0 after:h-0.5 after:bg-transparent hover:text-foreground", isActive && "text-primary after:bg-primary")}><Icon className="h-4 w-4" aria-hidden="true" />{label}</NavLink>)}
  </nav>
}

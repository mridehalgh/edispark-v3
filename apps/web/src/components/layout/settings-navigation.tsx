"use client"

import { NavLink } from "react-router-dom"

import { cn } from "@/lib/utils"

const links = [
  ["Connections", "/settings/connections"],
  ["Mappings", "/settings/mappings"],
  ["Workspace", "/settings/workspace"],
] as const

export function SettingsNavigation() {
  return (
    <nav aria-label="Settings" className="border-b bg-card px-4 sm:px-6 lg:px-8">
      <div className="mx-auto flex w-full max-w-[96rem] gap-5 overflow-x-auto">
        {links.map(([label, href]) => (
          <NavLink
            key={href}
            to={href}
            className={({ isActive }) => cn(
              "relative flex min-h-12 shrink-0 items-center text-sm font-medium text-muted-foreground after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-transparent",
              isActive && "text-foreground after:bg-primary",
            )}
          >
            {label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}

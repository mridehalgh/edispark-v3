'use client'

import { Link } from "react-router-dom"

import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { authClient } from "@/lib/auth-client"

export function UserNav({ admin }: { admin: boolean }) {
  const { data: session } = authClient.useSession()
  const name = session?.user.name ?? "User"
  const initials = name.split(" ").map((part) => part[0]).join("").slice(0, 2).toUpperCase()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="h-10 gap-2 px-1.5" aria-label="Open user menu">
          <Avatar className="h-8 w-8">
            <AvatarFallback className="bg-accent text-xs font-semibold text-primary">{initials}</AvatarFallback>
          </Avatar>
          <span className="hidden text-left sm:block">
            <span className="block max-w-32 truncate text-xs font-medium leading-4">{name}</span>
          </span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>{name}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild><Link to="/account/settings">Account settings</Link></DropdownMenuItem>
        {admin && <DropdownMenuItem asChild><Link to="/organisation/people">Organisation</Link></DropdownMenuItem>}
        {process.env.NODE_ENV === "development" && (
          <DropdownMenuItem asChild><Link to="/debug/jwt">JWT diagnostics</Link></DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => authClient.signOut()}>Sign out</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

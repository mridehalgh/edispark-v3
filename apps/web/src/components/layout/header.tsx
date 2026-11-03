"use client"

import { FileText, Menu, Settings } from "lucide-react"
import { NavLink } from "react-router-dom"

import Logo from "@/components/logo/logo"
import { Button } from "@/components/ui/button"
import { Sheet, SheetClose, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet"
import { canManageOrganization } from "@/lib/access"
import { cn } from "@/lib/utils"
import { useAccess } from "@/lib/use-access"
import { OrgSwitcher } from "./org-switcher"
import { UserNav } from "./user-nav"

const documentsLink = { label: "Documents", href: "/documents", icon: FileText }
const settingsLink = { label: "Settings", href: "/settings", icon: Settings }

function NavigationLinks({ admin, mobile = false }: { admin: boolean; mobile?: boolean }) {
  const links = admin ? [documentsLink, settingsLink] : [documentsLink]
  return (
    <nav aria-label="Primary navigation" className="space-y-1">
      {links.map(({ label, href, icon: Icon }) => {
        const link = (
          <NavLink
            key={href}
            to={href}
            className={({ isActive }) => cn(
              "flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-foreground",
              isActive && "bg-accent text-primary",
            )}
          >
            <Icon className="h-4 w-4" aria-hidden="true" />
            {label}
          </NavLink>
        )
        return mobile ? <SheetClose asChild key={href}>{link}</SheetClose> : link
      })}
    </nav>
  )
}

function Brand() {
  return (
    <NavLink to="/documents" className="flex items-center gap-2.5" aria-label="EDI Spark documents">
      <Logo className="h-7 w-7 text-primary" />
      <span className="text-base font-semibold tracking-tight">EDI Spark</span>
    </NavLink>
  )
}

export default function Header() {
  const { access } = useAccess()
  const admin = canManageOrganization(access?.organizationRole)

  return (
    <>
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r bg-card lg:flex">
        <div className="flex h-16 items-center border-b px-5"><Brand /></div>
        <div className="flex-1 px-3 py-5"><NavigationLinks admin={admin} /></div>
        <div className="border-t px-3 py-3"><OrgSwitcher /></div>
      </aside>

      <header className="fixed inset-x-0 top-0 z-20 flex h-16 items-center border-b bg-background px-4 sm:px-6 lg:left-60 lg:px-8">
        <div className="flex w-full items-center gap-3">
          <Sheet>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Open navigation">
                <Menu className="h-5 w-5" />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-72 p-0">
              <SheetHeader className="border-b px-5 py-4 text-left"><SheetTitle><Brand /></SheetTitle></SheetHeader>
              <div className="border-b px-3 py-3"><OrgSwitcher /></div>
              <div className="px-3 py-5"><NavigationLinks admin={admin} mobile /></div>
            </SheetContent>
          </Sheet>
          <div className="lg:hidden"><Brand /></div>
          <div className="ml-auto"><UserNav admin={admin} /></div>
        </div>
      </header>
    </>
  )
}

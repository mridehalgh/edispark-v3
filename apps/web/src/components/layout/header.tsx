"use client"

import { Building2, FileText, Files, Handshake, LayoutDashboard, Link2, Menu, Shield, Sparkles } from "lucide-react"
import { NavLink } from "react-router-dom"

import styles from "@/app/overview/dispatch.module.css"
import Logo from "@/components/logo/logo"
import { Button } from "@/components/ui/button"
import { Sheet, SheetClose, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet"
import { canManageOrganization } from "@/lib/access"
import { cn } from "@/lib/utils"
import { useAccess } from "@/lib/use-access"
import { OrgSwitcher } from "./org-switcher"
import { UserNav } from "./user-nav"

const workLinks = [
  { label: "Desk", href: "/overview", icon: LayoutDashboard },
  { label: "Documents", href: "/documents", icon: FileText },
  { label: "Files", href: "/files", icon: Files },
]
const networkLinks = [
  { label: "Partners", href: "/partners", icon: Handshake },
  { label: "Connections", href: "/connections", icon: Link2 },
  { label: "AI", href: "/ai", icon: Sparkles },
]

function NavigationLinks({ admin, platform, hasWorkspace, mobile = false }: { admin: boolean; platform: boolean; hasWorkspace: boolean; mobile?: boolean }) {
  const groups: { label?: string; links: typeof workLinks }[] = admin
    ? [{ label: "Work", links: workLinks }, { label: "Network", links: networkLinks }]
    : hasWorkspace ? [{ label: undefined, links: workLinks.slice(0, 2) }] : []
  if (platform) groups.push({ label: "Platform", links: [{ label: "Managed partners", href: "/platform", icon: Shield }] })
  return (
    <nav aria-label="Primary navigation" className="space-y-6">
      {groups.map((group, index) => <div key={group.label ?? index}>
        {group.label ? <p className="mb-2 px-3 text-xs font-medium text-muted-foreground">{group.label}</p> : null}
        <div className="space-y-1">{group.links.map(({ label, href, icon: Icon }) => {
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
        })}</div>
      </div>)}
    </nav>
  )
}

function Brand() {
  return (
    <NavLink to="/" className={styles.wordmark} aria-label="EDI Spark home">
      <Logo className={styles.shellLogo} />
      <span>EDI Spark</span>
    </NavLink>
  )
}

export default function Header() {
  const { access } = useAccess()
  const admin = canManageOrganization(access?.organizationRole)

  return (
    <>
      <aside className={cn("fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r lg:flex", styles.shellRail)}>
        <div className="flex h-16 items-center border-b px-5"><Brand /></div>
        <div className="flex-1 px-3 py-5"><NavigationLinks admin={admin} platform={access?.platformRole === "platform_admin"} hasWorkspace={!!access?.organizationRole} /></div>
        <div className="border-t px-3 py-3">
          {admin ? <NavLink to="/organisation/people" className={({ isActive }) => cn("mb-2 flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground", isActive && "bg-accent text-primary")}><Building2 className="h-4 w-4" />Organisation</NavLink> : null}
        </div>
      </aside>

      <header className={cn("fixed inset-x-0 top-0 z-20 flex h-16 items-center border-b px-4 sm:px-6 lg:left-60 lg:px-8", styles.shellHeader)}>
        <div className="flex w-full items-center gap-3">
          <Sheet>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Open navigation">
                <Menu className="h-5 w-5" />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className={cn("w-72 p-0", styles.shellDrawer)}>
              <SheetHeader className="border-b px-5 py-4 text-left"><SheetTitle><Brand /></SheetTitle></SheetHeader>
              <div className="border-b px-3 py-3"><OrgSwitcher /></div>
              <div className="px-3 py-5">
                <NavigationLinks admin={admin} platform={access?.platformRole === "platform_admin"} hasWorkspace={!!access?.organizationRole} mobile />
                {admin ? <div className="mt-6 border-t pt-4"><SheetClose asChild><NavLink to="/organisation/people" className={({ isActive }) => cn("flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground", isActive && "bg-accent text-primary")}><Building2 className="h-4 w-4" />Organisation</NavLink></SheetClose></div> : null}
              </div>
            </SheetContent>
          </Sheet>
          <div className="lg:hidden"><Brand /></div>
          <div className="hidden lg:block"><OrgSwitcher /></div>
          <div className="ml-auto"><UserNav admin={admin} /></div>
        </div>
      </header>
    </>
  )
}

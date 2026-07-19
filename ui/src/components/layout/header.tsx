'use client'

import {
  Bell,
  FileText,
  LayoutDashboard,
  Menu,
  Search,
  Settings,
} from "lucide-react"
import { NavLink } from "react-router-dom"

import Logo from "@/components/logo/logo"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet"
import { cn } from "@/lib/utils"
import { OrgSwitcher } from "./org-switcher"
import { UserNav } from "./user-nav"

const navigation = [
  { label: "Overview", href: "/", icon: LayoutDashboard },
  { label: "Documents", href: "/file", icon: FileText },
  { label: "Settings", href: "/organization", icon: Settings },
]

function NavigationLinks({ mobile = false }: { mobile?: boolean }) {
  return (
    <nav aria-label="Primary navigation" className="space-y-1">
      {navigation.map(({ label, href, icon: Icon }) => {
        const link = (
          <NavLink
            key={href}
            to={href}
            end={href === "/"}
            className={({ isActive }) => cn(
              "group relative flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-foreground",
              isActive && "bg-accent text-primary before:absolute before:inset-y-2 before:left-0 before:w-0.5 before:rounded-full before:bg-primary"
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
    <NavLink to="/" className="flex items-center gap-2.5 text-primary" aria-label="EDI Spark overview">
      <Logo className="h-7 w-7" />
      <span className="text-base font-semibold tracking-tight text-foreground">EDI Spark</span>
    </NavLink>
  )
}

export default function Header() {
  return (
    <>
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r bg-card lg:flex">
        <div className="flex h-16 items-center border-b px-5">
          <Brand />
        </div>
        <div className="flex-1 px-3 py-5">
          <NavigationLinks />
        </div>
        <div className="border-t px-5 py-4">
          <div className="flex items-center gap-2 text-xs font-medium text-success-foreground">
            <span className="h-2 w-2 rounded-full bg-success-foreground" aria-hidden="true" />
            All systems ready
          </div>
          <p className="mt-1 pl-4 text-xs text-muted-foreground">Connections operating normally</p>
        </div>
      </aside>

      <header className="fixed inset-x-0 top-0 z-20 flex h-16 items-center border-b bg-background/95 px-4 backdrop-blur sm:px-6 lg:left-60 lg:px-8">
        <div className="flex w-full items-center gap-3">
          <Sheet>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Open navigation">
                <Menu className="h-5 w-5" />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-72 p-0">
              <SheetHeader className="border-b px-5 py-4 text-left">
                <SheetTitle><Brand /></SheetTitle>
                <SheetDescription className="sr-only">Navigate EDI Spark</SheetDescription>
              </SheetHeader>
              <div className="px-3 py-5"><NavigationLinks mobile /></div>
            </SheetContent>
          </Sheet>

          <div className="hidden sm:block lg:hidden"><Brand /></div>
          <div className="hidden lg:block"><OrgSwitcher /></div>

          <form action="/file" className="relative mx-auto hidden w-full max-w-md md:block" role="search">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <Input name="q" aria-label="Search documents" placeholder="Search documents…" className="h-9 bg-card pl-9 pr-14" />
            <kbd className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 rounded border bg-muted px-1.5 py-0.5 text-xs font-medium text-muted-foreground">⌘ K</kbd>
          </form>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" aria-label="Notifications" className="relative ml-auto md:ml-0">
                <Bell className="h-4 w-4" />
                <span className="absolute right-2 top-2 h-1.5 w-1.5 rounded-full bg-destructive" aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-72">
              <DropdownMenuLabel>Notifications</DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem asChild className="items-start py-2.5">
                <NavLink to="/file?status=attention" className="flex-col">
                  <span className="text-sm font-medium">3 documents need attention</span>
                  <span className="mt-0.5 text-xs text-muted-foreground">Review issues before fulfilment is delayed.</span>
                </NavLink>
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <UserNav />
        </div>
      </header>
    </>
  )
}

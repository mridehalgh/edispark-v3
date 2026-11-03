import * as React from "react"

import { LayoutBody } from "@/components/layout/layout"
import { cn } from "@/lib/utils"

export function AdminPage({ className, ...props }: React.ComponentProps<typeof LayoutBody>) {
  return <LayoutBody className={cn("mx-auto w-full max-w-6xl py-7 sm:py-8", className)} {...props} />
}

export function AdminPageHeader({
  title,
  description,
  actions,
  className,
}: {
  title: string
  description?: string
  actions?: React.ReactNode
  className?: string
}) {
  return (
    <header className={cn("mb-7 flex min-h-10 items-center justify-between gap-4", className)}>
      <div><h1 className="text-2xl font-semibold tracking-tight text-balance">{title}</h1>{description ? <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">{description}</p> : null}</div>
      {actions && <div className="flex shrink-0 flex-wrap items-center justify-end gap-2">{actions}</div>}
    </header>
  )
}

export function AdminNotice({
  children,
  tone = "error",
}: React.PropsWithChildren<{ tone?: "error" | "success" }>) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={cn(
        "mb-5 rounded-md px-4 py-3 text-sm",
        tone === "error" ? "bg-destructive/10 text-destructive" : "bg-success/25 text-success-foreground",
      )}
    >
      {children}
    </div>
  )
}

export function AdminEmptyState({ title, action }: { title: string; action?: React.ReactNode }) {
  return (
    <div className="grid min-h-40 place-items-center px-6 py-10 text-center">
      <div>
        <p className="text-sm font-medium">{title}</p>
        {action && <div className="mt-4">{action}</div>}
      </div>
    </div>
  )
}

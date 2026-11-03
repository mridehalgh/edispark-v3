"use client"

import { ArrowRight, ChevronDown } from "lucide-react"
import { Link } from "react-router-dom"

import { canManageOrganization } from "@/lib/access"
import { useAccess } from "@/lib/use-access"

export function DocumentTechnicalDetails({
  setId,
  ublVersion,
  profileId,
  representation,
}: {
  setId?: string
  ublVersion?: string
  profileId?: string
  representation?: string | null
}) {
  const { access } = useAccess()
  if (!canManageOrganization(access?.organizationRole)) return null

  return (
    <details className="group border-t py-5 text-sm">
      <summary className="flex min-h-11 cursor-pointer list-none items-center gap-2 font-medium focus-visible:outline-none">
        Technical details
        <ChevronDown className="h-4 w-4 text-muted-foreground transition-transform group-open:rotate-180 motion-reduce:transition-none" aria-hidden="true" />
      </summary>
      <div className="flex flex-col gap-3 pb-2 text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
        <span>UBL {ublVersion ?? "unknown"}{profileId ? ` · ${profileId}` : ""}{representation ? ` · ${representation}` : ""}</span>
        {setId ? <Link to={`/files/${setId}`} className="inline-flex min-h-11 items-center gap-1.5 font-medium text-primary hover:underline">Open files<ArrowRight className="h-4 w-4" aria-hidden="true" /></Link> : null}
      </div>
    </details>
  )
}

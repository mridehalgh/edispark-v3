"use client"

import { ArrowRight } from "lucide-react"
import { Link } from "react-router-dom"

import { canManageOrganization } from "@/lib/access"
import { useAccess } from "@/lib/use-access"

export function DocumentTechnicalDetails({
  setId,
}: {
  setId?: string
  ublVersion?: string
  profileId?: string
  representation?: string | null
}) {
  const { access } = useAccess()
  if (!canManageOrganization(access?.organizationRole)) return null

  return setId ? <div className="flex flex-col gap-2 border-t py-5 text-sm sm:flex-row sm:items-center sm:justify-between"><span className="text-muted-foreground">Need the original file or processing history?</span><Link to={`/files/${setId}`} className="inline-flex min-h-10 items-center gap-1.5 font-medium text-primary hover:underline">Open source files<ArrowRight className="h-4 w-4" aria-hidden="true" /></Link></div> : null
}

"use client"

import { Navigate, Outlet } from "react-router-dom"

import { canManageOrganization, isPlatformAdmin } from "@/lib/access"
import { useAccess } from "@/lib/use-access"

function AccessLoading() {
  return <div className="grid min-h-[calc(100vh-4rem)] place-items-center" aria-label="Checking access"><span className="h-5 w-32 animate-pulse rounded bg-muted" /></div>
}

export function RequireOrganizationAdmin() {
  const { access, loading } = useAccess()
  if (loading) return <AccessLoading />
  return canManageOrganization(access?.organizationRole) ? <Outlet /> : <Navigate to="/documents" replace />
}

export function RequirePlatformAdmin() {
  const { access, loading } = useAccess()
  if (loading) return <AccessLoading />
  return isPlatformAdmin(access?.platformRole) ? <Outlet /> : <Navigate to="/documents" replace />
}

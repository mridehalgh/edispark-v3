"use client"

import { useEffect, useState } from "react"

import type { AccessContext } from "@/lib/access"
import { authClient } from "@/lib/auth-client"

export function useAccess() {
  const { data: organisation, isPending } = authClient.useActiveOrganization()
  const organisationId = organisation?.id
  const [result, setResult] = useState<{ organisationId: typeof organisationId; access?: AccessContext }>()

  useEffect(() => {
    if (isPending) return
    const controller = new AbortController()
    void (async () => {
      let access: AccessContext | undefined
      try {
        const response = await fetch("/api/access", { cache: "no-store", signal: controller.signal })
        if (response.ok) access = await response.json() as AccessContext
      } finally {
        if (!controller.signal.aborted) setResult({ organisationId, access })
      }
    })().catch(() => {})
    return () => controller.abort()
  }, [organisationId, isPending])

  const loading = isPending || !result || result.organisationId !== organisationId
  return { access: loading ? undefined : result.access, loading }
}

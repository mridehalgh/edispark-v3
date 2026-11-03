"use client"

import { useCallback, useEffect, useState } from "react"

import type { AccessContext } from "@/lib/access"

export function useAccess() {
  const [access, setAccess] = useState<AccessContext>()
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      const response = await fetch("/api/access", { cache: "no-store" })
      setAccess(response.ok ? await response.json() as AccessContext : undefined)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  return { access, loading, refresh }
}

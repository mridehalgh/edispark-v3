"use client"

import type { PartnerResponse, TransferResponse } from "@edispark/mft-client"
import { ArrowRight, Link2, Plus, RefreshCw, Search, ShieldCheck } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { Link } from "react-router-dom"

import { AdminEmptyState, AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { ConnectionsNavigation } from "@/components/connections/connections-navigation"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { adminApi, formatAdminDate, sentenceCase } from "@/lib/admin-api"
import { cn } from "@/lib/utils"

function currentConnections(items: PartnerResponse[]) {
  const families = new Map<string, PartnerResponse>()
  for (const item of items) {
    const key = item.familyId ?? item.id ?? item.name ?? "connection"
    const current = families.get(key)
    if (!current || item.status === "ACTIVE" || (current.status !== "ACTIVE" && (item.revision ?? 0) > (current.revision ?? 0))) families.set(key, item)
  }
  return [...families.values()].sort((left, right) => (left.name ?? "").localeCompare(right.name ?? ""))
}

export function ConnectionsPage() {
  const [connections, setConnections] = useState<PartnerResponse[]>([])
  const [transfers, setTransfers] = useState<TransferResponse[]>([])
  const [query, setQuery] = useState("")
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()

  const refresh = useCallback(async () => {
    setLoading(true); setError(undefined)
    try {
      const [nextConnections, nextTransfers] = await Promise.all([adminApi<PartnerResponse[]>("/api/mft/partners"), adminApi<TransferResponse[]>("/api/mft/transfers")])
      setConnections(nextConnections); setTransfers(nextTransfers)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Connections could not be loaded.")
    } finally { setLoading(false) }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const visible = useMemo(() => {
    const search = query.trim().toLowerCase()
    return currentConnections(connections).filter((item) => !search || [item.name, item.localAs2Id, item.remoteAs2Id, item.endpoint].some((value) => value?.toLowerCase().includes(search)))
  }, [connections, query])

  function lastTransfer(connectionId?: string) {
    return transfers.filter((item) => item.partnerId === connectionId).toSorted((a, b) => new Date(b.updatedAt ?? 0).getTime() - new Date(a.updatedAt ?? 0).getTime())[0]
  }

  return (
    <AdminPage className="max-w-[96rem]">
      <AdminPageHeader title="Connections" actions={<><Button variant="ghost" size="icon" onClick={() => void refresh()} disabled={loading} aria-label="Refresh connections"><RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} /></Button><Button asChild><Link to="/connections/new"><Plus className="mr-2 h-4 w-4" />Add connection</Link></Button></>} />
      <ConnectionsNavigation />
      {error ? <AdminNotice>{error}</AdminNotice> : null}
      <label className="relative mb-4 block max-w-lg"><span className="sr-only">Search connections</span><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" /><Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search connections" className="pl-9" /></label>
      <section className="border-y" aria-label="Connections">
        <div className="hidden border-b bg-muted/35 px-4 py-2.5 text-xs text-muted-foreground md:grid md:grid-cols-[minmax(14rem,1fr)_10rem_12rem_12rem_2rem] md:gap-4"><span>Connection</span><span>Protocol</span><span>Status</span><span>Last file</span><span /></div>
        <div className="divide-y">{visible.map((connection) => { const latest = lastTransfer(connection.id); return <Link key={connection.id} to={`/connections/${connection.id}`} className="grid min-h-16 items-center gap-2 px-4 py-3 hover:bg-muted/40 md:grid-cols-[minmax(14rem,1fr)_10rem_12rem_12rem_2rem] md:gap-4"><span className="min-w-0"><span className="block truncate text-sm font-semibold">{connection.name}</span><span className="mt-0.5 block truncate font-mono text-xs text-muted-foreground">{connection.remoteAs2Id}</span></span><span className="inline-flex items-center gap-2 text-xs"><Link2 className="h-3.5 w-3.5 text-primary" />AS2</span><span><Badge variant="secondary">{sentenceCase(connection.status)}</Badge>{connection.signingRequired && connection.encryptionRequired ? <span className="ml-2 inline-flex items-center gap-1 text-xs text-success-foreground"><ShieldCheck className="h-3.5 w-3.5" />Secure</span> : null}</span><span className="text-xs text-muted-foreground">{latest ? formatAdminDate(latest.updatedAt) : "No files"}</span><ArrowRight className="h-4 w-4 text-muted-foreground" /></Link> })}</div>
        {!loading && visible.length === 0 ? <AdminEmptyState title={connections.length ? "No connections match this search" : "No connections"} action={!connections.length ? <Button asChild><Link to="/connections/new">Add connection</Link></Button> : undefined} /> : null}
      </section>
    </AdminPage>
  )
}

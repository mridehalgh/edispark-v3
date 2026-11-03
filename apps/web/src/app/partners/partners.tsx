"use client"

import type { TradingPartner } from "@edispark/integration-client"
import type { PartnerResponse, TransferResponse } from "@edispark/mft-client"
import { ArrowRight, Plus, RefreshCw, Search } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { Link } from "react-router-dom"

import { AdminEmptyState, AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { adminApi, formatAdminDate, sentenceCase } from "@/lib/admin-api"
import { cn } from "@/lib/utils"

function currentPartners(partners: TradingPartner[]) {
  const families = new Map<string, TradingPartner>()
  for (const partner of partners) {
    const key = partner.familyId ?? partner.id ?? partner.displayName ?? "partner"
    const current = families.get(key)
    if (!current || partner.status === "ACTIVE" || (current.status !== "ACTIVE" && (partner.revision ?? 0) > (current.revision ?? 0))) families.set(key, partner)
  }
  return [...families.values()].sort((left, right) => (left.displayName ?? "").localeCompare(right.displayName ?? ""))
}

export function PartnersPage() {
  const [partners, setPartners] = useState<TradingPartner[]>([])
  const [connections, setConnections] = useState<PartnerResponse[]>([])
  const [transfers, setTransfers] = useState<TransferResponse[]>([])
  const [query, setQuery] = useState("")
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()

  const refresh = useCallback(async () => {
    setLoading(true); setError(undefined)
    try {
      const [nextPartners, nextConnections, nextTransfers] = await Promise.all([adminApi<TradingPartner[]>("/api/integration/partners"), adminApi<PartnerResponse[]>("/api/mft/partners"), adminApi<TransferResponse[]>("/api/mft/transfers")])
      setPartners(nextPartners); setConnections(nextConnections); setTransfers(nextTransfers)
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "Partners could not be loaded.") }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const visible = useMemo(() => {
    const search = query.trim().toLowerCase()
    return currentPartners(partners).filter((partner) => !search || [partner.displayName, ...(partner.identities?.flatMap((identity) => [identity.scheme, identity.value]) ?? [])].some((value) => value?.toLowerCase().includes(search)))
  }, [partners, query])

  function relatedConnections(partner: TradingPartner) {
    return connections.filter((connection) => connection.name?.toLowerCase() === partner.displayName?.toLowerCase())
  }

  return <AdminPage className="max-w-[96rem]">
    <AdminPageHeader title="Partners" actions={<><Button variant="ghost" size="icon" onClick={() => void refresh()} disabled={loading} aria-label="Refresh partners"><RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} /></Button><Button asChild><Link to="/partners/new"><Plus className="mr-2 h-4 w-4" />Add partner</Link></Button></>} />
    {error ? <AdminNotice>{error}</AdminNotice> : null}
    <label className="relative mb-4 block max-w-lg"><span className="sr-only">Search partners</span><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" /><Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search partners" className="pl-9" /></label>
    <section className="border-y" aria-label="Partners"><div className="hidden border-b bg-muted/35 px-4 py-2.5 text-xs text-muted-foreground md:grid md:grid-cols-[minmax(14rem,1fr)_12rem_10rem_12rem_2rem] md:gap-4"><span>Partner</span><span>Identifier</span><span>Connections</span><span>Last file</span><span /></div><div className="divide-y">{visible.map((partner) => { const partnerConnections = relatedConnections(partner); const ids = new Set(partnerConnections.map((item) => item.id)); const latest = transfers.filter((item) => ids.has(item.partnerId)).toSorted((a, b) => new Date(b.updatedAt ?? 0).getTime() - new Date(a.updatedAt ?? 0).getTime())[0]; return <Link key={partner.id} to={`/partners/${partner.id}`} className="grid min-h-16 items-center gap-2 px-4 py-3 hover:bg-muted/40 md:grid-cols-[minmax(14rem,1fr)_12rem_10rem_12rem_2rem] md:gap-4"><span className="min-w-0"><span className="block truncate text-sm font-semibold">{partner.displayName}</span><Badge variant="secondary" className="mt-1">{sentenceCase(partner.status)}</Badge></span><span className="truncate text-xs text-muted-foreground">{partner.identities?.map((identity) => `${identity.scheme} ${identity.value}`).join(" · ") || "Not set"}</span><span className="text-xs text-muted-foreground">{partnerConnections.length}</span><span className="text-xs text-muted-foreground">{latest ? formatAdminDate(latest.updatedAt) : "No files"}</span><ArrowRight className="h-4 w-4 text-muted-foreground" /></Link> })}</div>{!loading && visible.length === 0 ? <AdminEmptyState title={partners.length ? "No partners match this search" : "No partners"} action={!partners.length ? <Button asChild><Link to="/partners/new">Add partner</Link></Button> : undefined} /> : null}</section>
  </AdminPage>
}

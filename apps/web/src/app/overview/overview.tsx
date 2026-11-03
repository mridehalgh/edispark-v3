"use client"

import type { CertificateResponse, PartnerResponse, TransferResponse } from "@edispark/mft-client"
import { AlertCircle, ArrowRight, FileUp, Link2, RefreshCw, ShieldAlert } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { Link } from "react-router-dom"

import { AdminEmptyState, AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { TransferStatus } from "@/components/mft/transfer-status"
import { Button } from "@/components/ui/button"
import { adminApi, formatAdminDate } from "@/lib/admin-api"
import { cn } from "@/lib/utils"

type OverviewData = {
  transfers: TransferResponse[]
  connections: PartnerResponse[]
  certificates: CertificateResponse[]
}

const EMPTY: OverviewData = { transfers: [], connections: [], certificates: [] }

export function OverviewPage() {
  const [data, setData] = useState(EMPTY)
  const [loading, setLoading] = useState(true)
  const [errors, setErrors] = useState<string[]>([])

  const refresh = useCallback(async () => {
    setLoading(true)
    const results = await Promise.allSettled([
      adminApi<TransferResponse[]>("/api/mft/transfers"),
      adminApi<PartnerResponse[]>("/api/mft/partners"),
      adminApi<CertificateResponse[]>("/api/mft/certificates"),
    ])
    setData({
      transfers: results[0].status === "fulfilled" ? results[0].value : [],
      connections: results[1].status === "fulfilled" ? results[1].value : [],
      certificates: results[2].status === "fulfilled" ? results[2].value : [],
    })
    setErrors(results.flatMap((result) => result.status === "rejected" ? [result.reason instanceof Error ? result.reason.message : "A workspace section is unavailable."] : []))
    setLoading(false)
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const connectionNames = useMemo(() => new Map(data.connections.map((item) => [item.id, item.name])), [data.connections])
  const attention = data.transfers.filter((item) => item.status === "FAILED" || item.status === "NEEDS_REVIEW" || item.status === "RETRY_SCHEDULED")
  const inProgress = data.transfers.filter((item) => item.status === "QUEUED" || item.status === "PROCESSING" || item.status === "AWAITING_MDN")
  const recent = [...data.transfers].sort((left, right) => new Date(right.updatedAt ?? 0).getTime() - new Date(left.updatedAt ?? 0).getTime()).slice(0, 7)
  const expiring = data.certificates.filter((item) => (item.daysUntilExpiry ?? Number.POSITIVE_INFINITY) < 30)

  return (
    <AdminPage className="max-w-[96rem]">
      <AdminPageHeader title="Overview" actions={(
        <div className="flex gap-2">
          <Button variant="ghost" size="icon" onClick={() => void refresh()} disabled={loading} aria-label="Refresh overview"><RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} /></Button>
          <Button asChild><Link to="/files/send"><FileUp className="mr-2 h-4 w-4" />Send file</Link></Button>
        </div>
      )} />

      {errors.length ? <AdminNotice>{errors[0]} Other sections remain available.</AdminNotice> : null}

      {(attention.length > 0 || expiring.length > 0) && (
        <section className="border-y" aria-labelledby="attention-title">
          <div className="flex items-center gap-2 border-b px-4 py-3"><AlertCircle className="h-4 w-4 text-destructive" /><h2 id="attention-title" className="text-sm font-semibold">Needs attention</h2></div>
          <div className="divide-y">
            {attention.slice(0, 5).map((transfer) => <Link key={transfer.id} to={`/files/transfers/${transfer.id}`} className="flex min-h-14 items-center gap-4 px-4 py-3 hover:bg-muted/40"><span className="min-w-0 flex-1 truncate text-sm font-medium">{transfer.filename}</span><span className="hidden text-xs text-muted-foreground sm:block">{connectionNames.get(transfer.partnerId) ?? "Unassigned connection"}</span><TransferStatus status={transfer.status} /><ArrowRight className="h-4 w-4 text-muted-foreground" /></Link>)}
            {expiring.map((certificate) => <Link key={certificate.id} to="/connections/certificates" className="flex min-h-14 items-center gap-4 px-4 py-3 hover:bg-muted/40"><ShieldAlert className="h-4 w-4 text-warning-foreground" /><span className="min-w-0 flex-1 truncate text-sm font-medium">{certificate.name}</span><span className="text-xs text-muted-foreground">Expires {formatAdminDate(certificate.notAfter)}</span><ArrowRight className="h-4 w-4 text-muted-foreground" /></Link>)}
          </div>
        </section>
      )}

      {inProgress.length > 0 && (
        <section className="mt-8 border-y" aria-labelledby="progress-title">
          <div className="border-b px-4 py-3"><h2 id="progress-title" className="text-sm font-semibold">In progress</h2></div>
          <div className="divide-y">{inProgress.map((transfer) => <Link key={transfer.id} to={`/files/transfers/${transfer.id}`} className="flex min-h-14 items-center gap-4 px-4 py-3 hover:bg-muted/40"><span className="min-w-0 flex-1 truncate text-sm font-medium">{transfer.filename}</span><TransferStatus status={transfer.status} /><span className="hidden text-xs text-muted-foreground sm:block">{formatAdminDate(transfer.updatedAt)}</span></Link>)}</div>
        </section>
      )}

      <section className="mt-8 border-y" aria-labelledby="recent-title">
        <div className="flex items-center justify-between border-b px-4 py-3"><h2 id="recent-title" className="text-sm font-semibold">Recent files</h2><Button asChild variant="ghost" size="sm"><Link to="/files">View all</Link></Button></div>
        {recent.length ? <div className="divide-y">{recent.map((transfer) => <Link key={transfer.id} to={`/files/transfers/${transfer.id}`} className="grid min-h-14 items-center gap-3 px-4 py-3 hover:bg-muted/40 sm:grid-cols-[minmax(12rem,1fr)_12rem_10rem_11rem]"><span className="truncate text-sm font-medium">{transfer.filename}</span><span className="truncate text-xs text-muted-foreground">{connectionNames.get(transfer.partnerId) ?? "Unassigned connection"}</span><TransferStatus status={transfer.status} /><time className="text-xs text-muted-foreground">{formatAdminDate(transfer.updatedAt)}</time></Link>)}</div> : <AdminEmptyState title="No files yet" action={<Button asChild><Link to="/files/send"><FileUp className="mr-2 h-4 w-4" />Send file</Link></Button>} />}
      </section>

      {!loading && data.connections.length === 0 ? <section className="mt-8 border-y"><AdminEmptyState title="Add a connection before sending files" action={<Button asChild variant="outline"><Link to="/connections/new"><Link2 className="mr-2 h-4 w-4" />Add connection</Link></Button>} /></section> : null}
    </AdminPage>
  )
}

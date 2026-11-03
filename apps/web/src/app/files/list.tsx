"use client"

import type { PartnerResponse, TransferResponse } from "@edispark/mft-client"
import { ArrowDownToLine, ArrowRight, ArrowUpFromLine, Download, FileUp, RefreshCw, Search } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { Link, useSearchParams } from "react-router-dom"

import { AdminEmptyState, AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { TransferStatus } from "@/components/mft/transfer-status"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { adminApi, formatAdminDate } from "@/lib/admin-api"
import { cn } from "@/lib/utils"

function formatSize(value?: number) {
  if (value === undefined) return "Unknown size"
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

export function FilesList() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [transfers, setTransfers] = useState<TransferResponse[]>([])
  const [connections, setConnections] = useState<PartnerResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [query, setQuery] = useState(searchParams.get("q") ?? "")
  const [connectionId, setConnectionId] = useState(searchParams.get("connection") ?? "all")
  const [direction, setDirection] = useState(searchParams.get("direction") ?? "all")
  const [status, setStatus] = useState(searchParams.get("status") ?? "all")

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(undefined)
    try {
      const [nextTransfers, nextConnections] = await Promise.all([
        adminApi<TransferResponse[]>("/api/mft/transfers"),
        adminApi<PartnerResponse[]>("/api/mft/partners"),
      ])
      setTransfers(nextTransfers)
      setConnections(nextConnections)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Files could not be loaded.")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const names = useMemo(() => new Map(connections.map((item) => [item.id, item.name])), [connections])
  const filtered = useMemo(() => {
    const search = query.trim().toLowerCase()
    return [...transfers]
      .filter((item) => connectionId === "all" || item.partnerId === connectionId)
      .filter((item) => direction === "all" || item.direction === direction)
      .filter((item) => status === "all" || item.status === status)
      .filter((item) => !search || [item.filename, item.sha256, item.as2MessageId, names.get(item.partnerId)].some((value) => value?.toLowerCase().includes(search)))
      .sort((left, right) => new Date(right.updatedAt ?? 0).getTime() - new Date(left.updatedAt ?? 0).getTime())
  }, [connectionId, direction, names, query, status, transfers])

  const selected = transfers.find((item) => item.id === searchParams.get("selected")) ?? filtered[0]

  function selectTransfer(transfer: TransferResponse) {
    const next = new URLSearchParams(searchParams)
    if (transfer.id) next.set("selected", transfer.id)
    setSearchParams(next, { replace: true })
  }

  function exportFiles() {
    const rows = filtered.map((item) => [item.filename, names.get(item.partnerId), item.direction, item.status, item.size, item.updatedAt, item.sha256])
    const csv = [["File", "Connection", "Direction", "Status", "Bytes", "Updated", "SHA-256"], ...rows]
      .map((row) => row.map((value) => `"${String(value ?? "").replaceAll('"', '""')}"`).join(","))
      .join("\n")
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }))
    const link = window.document.createElement("a")
    link.href = url
    link.download = "edispark-files.csv"
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <AdminPage className="max-w-none px-0 py-0 sm:py-0">
      <div className="border-b px-4 py-5 sm:px-6 lg:px-8">
        <AdminPageHeader title="Files" className="mb-0" actions={<><Button variant="ghost" size="icon" onClick={() => void refresh()} disabled={loading} aria-label="Refresh files"><RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} /></Button><Button variant="outline" onClick={exportFiles} disabled={!filtered.length}><Download className="mr-2 h-4 w-4" />Export</Button><Button asChild><Link to="/files/send"><FileUp className="mr-2 h-4 w-4" />Send file</Link></Button></>} />
      </div>

      {error ? <div className="px-4 pt-5 sm:px-6 lg:px-8"><AdminNotice>{error}</AdminNotice></div> : null}

      <div className="grid min-h-[calc(100vh-8.1rem)] xl:grid-cols-[minmax(36rem,1fr)_24rem]">
        <section className="min-w-0 border-r" aria-label="Transferred files">
          <div className="grid gap-2 border-b p-4 sm:grid-cols-2 lg:grid-cols-[minmax(14rem,1fr)_12rem_10rem_11rem]">
            <label className="relative"><span className="sr-only">Search files</span><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" /><Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search files" className="pl-9" /></label>
            <label><span className="sr-only">Connection</span><select value={connectionId} onChange={(event) => setConnectionId(event.target.value)} className="h-10 w-full rounded-md border bg-background px-3 text-sm"><option value="all">All connections</option>{connections.filter((item) => item.id).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
            <label><span className="sr-only">Direction</span><select value={direction} onChange={(event) => setDirection(event.target.value)} className="h-10 w-full rounded-md border bg-background px-3 text-sm"><option value="all">Sent and received</option><option value="INBOUND">Received</option><option value="OUTBOUND">Sent</option></select></label>
            <label><span className="sr-only">Status</span><select value={status} onChange={(event) => setStatus(event.target.value)} className="h-10 w-full rounded-md border bg-background px-3 text-sm"><option value="all">All statuses</option><option value="DELIVERED">Delivered</option><option value="AWAITING_MDN">Waiting for receipt</option><option value="RETRY_SCHEDULED">Retry scheduled</option><option value="NEEDS_REVIEW">Needs attention</option><option value="FAILED">Failed</option></select></label>
          </div>

          <div className="hidden border-b bg-muted/35 px-4 py-2.5 text-xs text-muted-foreground md:grid md:grid-cols-[minmax(14rem,1fr)_10rem_8rem_10rem_9rem] md:gap-4"><span>File</span><span>Connection</span><span>Direction</span><span>Status</span><span>Time</span></div>
          <div className="divide-y">
            {filtered.map((transfer) => (
              <button key={transfer.id} type="button" onClick={() => selectTransfer(transfer)} className={cn("grid min-h-16 w-full items-center gap-2 px-4 py-3 text-left transition-colors hover:bg-muted/40 md:grid-cols-[minmax(14rem,1fr)_10rem_8rem_10rem_9rem] md:gap-4", selected?.id === transfer.id && "bg-accent/70")}>
                <span className="min-w-0"><span className="block truncate text-sm font-medium">{transfer.filename}</span><span className="mt-0.5 block text-xs text-muted-foreground">{formatSize(transfer.size)}</span></span>
                <span className="truncate text-xs text-muted-foreground">{names.get(transfer.partnerId) ?? "Unassigned"}</span>
                <span className="inline-flex items-center gap-1.5 text-xs">{transfer.direction === "INBOUND" ? <ArrowDownToLine className="h-3.5 w-3.5" /> : <ArrowUpFromLine className="h-3.5 w-3.5" />}{transfer.direction === "INBOUND" ? "Received" : "Sent"}</span>
                <TransferStatus status={transfer.status} />
                <time className="text-xs text-muted-foreground">{formatAdminDate(transfer.updatedAt)}</time>
              </button>
            ))}
            {!loading && filtered.length === 0 ? <AdminEmptyState title={transfers.length ? "No files match these filters" : "No files yet"} action={!transfers.length ? <Button asChild><Link to="/files/send">Send file</Link></Button> : undefined} /> : null}
            {loading ? Array.from({ length: 6 }, (_, index) => <div key={index} className="h-16 animate-pulse border-b bg-muted/30" />) : null}
          </div>
        </section>

        <aside className="hidden min-w-0 xl:block" aria-label="Selected file">
          {selected ? <FileInspector transfer={selected} connectionName={names.get(selected.partnerId)} /> : <AdminEmptyState title="Select a file" />}
        </aside>
      </div>
    </AdminPage>
  )
}

function FileInspector({ transfer, connectionName }: { transfer: TransferResponse; connectionName?: string }) {
  const relatedDocument = transfer.provenance?.documentSetId && transfer.provenance.documentId
    ? `/documents/${transfer.provenance.documentSetId}/${transfer.provenance.documentId}`
    : undefined
  return (
    <div className="sticky top-16 p-6">
      <div className="flex items-start justify-between gap-3"><div className="min-w-0"><h2 className="break-words text-lg font-semibold">{transfer.filename}</h2><TransferStatus status={transfer.status} className="mt-2" /></div><Button asChild variant="ghost" size="icon"><Link to={`/files/transfers/${transfer.id}`} aria-label="Open file details"><ArrowRight className="h-4 w-4" /></Link></Button></div>
      <dl className="mt-6 divide-y border-y text-sm">
        <Definition label="Connection" value={connectionName ?? "Unassigned"} />
        <Definition label="Direction" value={transfer.direction === "INBOUND" ? "Received" : "Sent"} />
        <Definition label="Size" value={formatSize(transfer.size)} />
        <Definition label="Updated" value={formatAdminDate(transfer.updatedAt)} />
      </dl>
      {transfer.failureReason ? <p className="mt-5 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{transfer.failureReason}</p> : null}
      {relatedDocument ? <section className="mt-6 border-t pt-5"><h3 className="text-sm font-semibold">Document</h3><Link to={relatedDocument} className="mt-3 inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline">Open related document<ArrowRight className="h-4 w-4" /></Link></section> : null}
      <div className="mt-6 grid grid-cols-2 gap-2"><Button asChild variant="outline"><a href={`/api/mft/transfers/${transfer.id}/content`}><Download className="mr-2 h-4 w-4" />Download</a></Button><Button asChild><Link to={`/files/transfers/${transfer.id}`}>View details</Link></Button></div>
    </div>
  )
}

function Definition({ label, value }: { label: string; value: string }) {
  return <div className="grid grid-cols-[7rem_1fr] gap-3 py-3"><dt className="text-muted-foreground">{label}</dt><dd className="break-words font-medium">{value}</dd></div>
}

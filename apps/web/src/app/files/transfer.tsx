"use client"

import type { PartnerResponse, TransferResponse } from "@edispark/mft-client"
import { ArrowLeft, ArrowRight, Copy, Download, FileCode2, FileStack, RefreshCw, RotateCcw } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"

import { AdminEmptyState, AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { TransferStatus } from "@/components/mft/transfer-status"
import { Button } from "@/components/ui/button"
import { adminApi, formatAdminDate } from "@/lib/admin-api"
import { getDocumentSetDetail, getDocumentSets, type DocumentSetDetail } from "@/lib/documents-api"
import { cn } from "@/lib/utils"

function formatSize(value?: number) {
  if (value === undefined) return "Not recorded"
  return new Intl.NumberFormat("en-GB", { style: "unit", unit: value < 1024 * 1024 ? "kilobyte" : "megabyte", maximumFractionDigits: 1 }).format(value < 1024 * 1024 ? value / 1024 : value / (1024 * 1024))
}

export function TransferPage() {
  const { transferId } = useParams<{ transferId: string }>()
  const navigate = useNavigate()
  const [transfer, setTransfer] = useState<TransferResponse>()
  const [connection, setConnection] = useState<PartnerResponse>()
  const [documentSet, setDocumentSet] = useState<DocumentSetDetail>()
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [copied, setCopied] = useState(false)

  const refresh = useCallback(async () => {
    if (!transferId) return
    setLoading(true)
    setError(undefined)
    try {
      const [nextTransfer, connections] = await Promise.all([
        adminApi<TransferResponse>(`/api/mft/transfers/${transferId}`),
        adminApi<PartnerResponse[]>("/api/mft/partners"),
      ])
      setTransfer(nextTransfer)
      setConnection(connections.find((item) => item.id === nextTransfer.partnerId))
      const documentSetId = nextTransfer.provenance?.documentSetId ?? (await getDocumentSets({ limit: 100 }).catch(() => undefined))?.items
        ?.find((item) => item.metadata?.mftTransferId === nextTransfer.id)?.id
      if (documentSetId) {
        setDocumentSet(await getDocumentSetDetail(documentSetId).catch(() => undefined))
      } else {
        setDocumentSet(undefined)
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "The file could not be loaded.")
    } finally {
      setLoading(false)
    }
  }, [transferId])

  useEffect(() => { void refresh() }, [refresh])

  async function resend() {
    if (!transferId) return
    setBusy(true)
    setError(undefined)
    try {
      const next = await adminApi<TransferResponse>(`/api/mft/transfers/${transferId}/resend`, { method: "POST" })
      navigate(next.id ? `/files/transfers/${next.id}` : "/files")
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "The file could not be resent.")
    } finally {
      setBusy(false)
    }
  }

  async function copyHash() {
    if (!transfer?.sha256) return
    await navigator.clipboard.writeText(transfer.sha256)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }

  if (loading) return <AdminPage><div className="h-96 animate-pulse bg-muted/40" /></AdminPage>
  if (!transfer || error) return <AdminPage><Button asChild variant="ghost"><Link to="/files"><ArrowLeft className="mr-2 h-4 w-4" />Files</Link></Button><div className="mt-6"><AdminNotice>{error ?? "The file was not found."}</AdminNotice></div></AdminPage>

  const relatedSetId = transfer.provenance?.documentSetId ?? documentSet?.documentSet.id
  const relatedDocumentId = transfer.provenance?.documentId ?? documentSet?.documents[0]?.id
  const relatedDocument = relatedSetId && relatedDocumentId
    ? `/documents/${relatedSetId}/${relatedDocumentId}`
    : undefined
  const canResend = transfer.direction === "OUTBOUND" && (transfer.status === "FAILED" || transfer.status === "NEEDS_REVIEW" || transfer.status === "RETRY_SCHEDULED")

  return (
    <AdminPage className="max-w-5xl">
      <Button asChild variant="ghost" className="-ml-3 mb-3"><Link to="/files"><ArrowLeft className="mr-2 h-4 w-4" />Files</Link></Button>
      <AdminPageHeader title={transfer.filename ?? "File"} actions={<><Button variant="ghost" size="icon" onClick={() => void refresh()} aria-label="Refresh file"><RefreshCw className="h-4 w-4" /></Button><Button asChild variant="outline"><a href={`/api/mft/transfers/${transfer.id}/content`}><Download className="mr-2 h-4 w-4" />Download</a></Button>{canResend ? <Button onClick={() => void resend()} disabled={busy}><RotateCcw className={cn("mr-2 h-4 w-4", busy && "animate-spin")} />Resend</Button> : null}</>} />
      <TransferStatus status={transfer.status} className="mb-6 text-sm" />
      {error ? <AdminNotice>{error}</AdminNotice> : null}
      {transfer.failureReason ? <AdminNotice>{transfer.failureReason}</AdminNotice> : null}

      <dl className="grid border-y sm:grid-cols-2">
        <Definition label="Connection" value={connection?.name ?? "Unassigned"} href={connection?.id ? `/connections/${connection.id}` : undefined} />
        <Definition label="Direction" value={transfer.direction === "INBOUND" ? "Received" : "Sent"} />
        <Definition label="Size" value={formatSize(transfer.size)} />
        <Definition label="Content type" value={transfer.contentType ?? "Not recorded"} />
        <Definition label="Created" value={formatAdminDate(transfer.createdAt)} />
        <Definition label="Last updated" value={formatAdminDate(transfer.updatedAt)} />
      </dl>

      <section className="mt-8 border-y" aria-labelledby="timeline-title">
        <div className="border-b px-4 py-3"><h2 id="timeline-title" className="text-sm font-semibold">Timeline</h2></div>
        {transfer.attempts?.length ? <ol className="divide-y">{transfer.attempts.map((attempt) => <li key={attempt.number} className="grid gap-1 px-4 py-4 sm:grid-cols-[8rem_1fr_12rem]"><span className="text-sm font-medium">Attempt {attempt.number}</span><span className="text-sm text-muted-foreground">{attempt.detail ?? attempt.outcome ?? "Transfer attempted"}</span><time className="text-xs text-muted-foreground">{formatAdminDate(attempt.completedAt ?? attempt.startedAt)}</time></li>)}</ol> : <AdminEmptyState title="No attempts recorded" />}
      </section>

      {documentSet ? <DocumentLineage detail={documentSet} relatedDocument={relatedDocument} /> : relatedDocument ? <section className="mt-8 border-y"><div className="border-b px-4 py-3"><h2 className="text-sm font-semibold">Document</h2></div><Link to={relatedDocument} className="flex min-h-14 items-center justify-between px-4 py-3 text-sm font-medium hover:bg-muted/40">Open related document<ArrowRight className="h-4 w-4 text-muted-foreground" /></Link></section> : null}

      <details className="mt-8 border-y"><summary className="cursor-pointer px-4 py-3 text-sm font-semibold">Technical details</summary><dl className="divide-y border-t"><TechnicalDefinition label="Transfer ID" value={transfer.id} /><TechnicalDefinition label="AS2 message ID" value={transfer.as2MessageId} /><TechnicalDefinition label="Receipt" value={transfer.mdnDisposition} /><TechnicalDefinition label="Exchange ID" value={transfer.provenance?.exchangeId} /><TechnicalDefinition label="Agreement" value={transfer.provenance?.agreementId ? `${transfer.provenance.agreementId} · revision ${transfer.provenance.agreementRevision ?? 1}` : undefined} /><TechnicalDefinition label="Document rule" value={transfer.provenance?.mappingRevisionId} /><TechnicalDefinition label="Document set ID" value={relatedSetId} /><TechnicalDefinition label="Document ID" value={relatedDocumentId} /><TechnicalDefinition label="SHA-256" value={transfer.sha256} action={transfer.sha256 ? <Button variant="ghost" size="sm" onClick={() => void copyHash()}><Copy className="mr-2 h-3.5 w-3.5" />{copied ? "Copied" : "Copy"}</Button> : undefined} /></dl></details>
    </AdminPage>
  )
}

function DocumentLineage({ detail, relatedDocument }: { detail: DocumentSetDetail; relatedDocument?: string }) {
  const setId = detail.documentSet.id
  return <section className="mt-8 border-y" aria-labelledby="document-set-title">
    <div className="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3"><div className="flex items-center gap-2"><FileStack className="h-4 w-4 text-primary" /><h2 id="document-set-title" className="text-sm font-semibold">Document set</h2><span className="text-xs text-muted-foreground">{detail.documents.length} {detail.documents.length === 1 ? "document" : "documents"}</span></div>{setId ? <Button asChild variant="ghost" size="sm"><Link to={`/files/${setId}`}>Open full file set<ArrowRight className="ml-2 h-4 w-4" /></Link></Button> : null}</div>
    <dl className="grid border-b bg-muted/15 sm:grid-cols-2"><Definition label="Set name" value={detail.documentSet.metadata?.name || `File set ${setId?.slice(0, 8) ?? ""}`} /><Definition label="Created" value={formatAdminDate(detail.documentSet.createdAt)} /></dl>
    <div className="divide-y">{detail.documents.map((document) => {
      const version = document.currentVersion
      const documentPath = setId && document.id ? `/documents/${setId}/${document.id}` : relatedDocument
      return <article key={document.id} className="px-4 py-4">
        <div className="flex flex-wrap items-start justify-between gap-3"><div className="flex min-w-0 gap-3"><FileCode2 className="mt-0.5 h-4 w-4 shrink-0 text-primary" /><div><h3 className="text-sm font-medium">{document.type?.toLowerCase().replaceAll("_", " ") ?? "Document"}</h3><p className="mt-1 text-xs text-muted-foreground">{version?.format ?? "Unknown format"} · version {version?.versionNumber ?? 1} of {document.versionCount ?? 1} · {version?.parseStatus?.toLowerCase().replaceAll("_", " ") ?? "parse status unavailable"}</p></div></div>{documentPath ? <Button asChild variant="outline" size="sm"><Link to={documentPath}>Open document</Link></Button> : null}</div>
        <div className="mt-4 grid gap-2 sm:grid-cols-2">
          {version?.versionNumber && setId && document.id ? <a href={`/api/documents/${setId}/documents/${document.id}/versions/${version.versionNumber}/content`} className="flex min-h-11 items-center justify-between rounded-md border px-3 text-sm hover:bg-muted/40"><span><span className="block font-medium">Source</span><span className="text-xs text-muted-foreground">{version.format} · version {version.versionNumber}</span></span><Download className="h-4 w-4 text-muted-foreground" /></a> : null}
          {document.derivatives?.map((derivative) => derivative.id && setId && document.id ? <a key={derivative.id} href={`/api/documents/${setId}/documents/${document.id}/derivatives/${derivative.id}/content`} className="flex min-h-11 items-center justify-between rounded-md border px-3 text-sm hover:bg-muted/40"><span><span className="block font-medium">{derivative.targetFormat} derivative</span><span className="text-xs text-muted-foreground">{derivative.variantKey || derivative.transformationMethod || "Generated representation"}</span></span><Download className="h-4 w-4 text-muted-foreground" /></a> : null)}
        </div>
      </article>
    })}</div>
  </section>
}

function Definition({ label, value, href }: { label: string; value: string; href?: string }) {
  return <div className="border-b px-4 py-4 sm:odd:border-r"><dt className="text-xs text-muted-foreground">{label}</dt><dd className="mt-1 text-sm font-medium">{href ? <Link to={href} className="text-primary hover:underline">{value}</Link> : value}</dd></div>
}

function TechnicalDefinition({ label, value, action }: { label: string; value?: string | null; action?: React.ReactNode }) {
  return <div className="grid gap-2 px-4 py-3 sm:grid-cols-[10rem_1fr_auto] sm:items-center"><dt className="text-xs text-muted-foreground">{label}</dt><dd className="break-all font-mono text-xs">{value || "Not recorded"}</dd>{action}</div>
}

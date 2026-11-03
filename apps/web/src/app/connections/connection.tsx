"use client"

import type { As2ConnectionDetailsResponse, CertificateResponse, PartnerResponse, TransferResponse } from "@edispark/mft-client"
import { ArrowLeft, ArrowRight, Copy, Download, Edit3, FileUp, Pause, Play, RefreshCw } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"

import { AdminEmptyState, AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { TransferStatus } from "@/components/mft/transfer-status"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { adminApi, formatAdminDate, sentenceCase } from "@/lib/admin-api"
import { cn } from "@/lib/utils"

type ConnectionData = {
  connections: PartnerResponse[]
  transfers: TransferResponse[]
  certificates: CertificateResponse[]
  details: As2ConnectionDetailsResponse[]
}

const EMPTY: ConnectionData = { connections: [], transfers: [], certificates: [], details: [] }

export function ConnectionPage() {
  const { connectionId } = useParams<{ connectionId: string }>()
  const navigate = useNavigate()
  const [data, setData] = useState(EMPTY)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  const refresh = useCallback(async () => {
    setLoading(true); setError(undefined)
    try {
      const [connections, transfers, certificates, details] = await Promise.all([
        adminApi<PartnerResponse[]>("/api/mft/partners"), adminApi<TransferResponse[]>("/api/mft/transfers"), adminApi<CertificateResponse[]>("/api/mft/certificates"), adminApi<As2ConnectionDetailsResponse[]>("/api/mft/connection-details"),
      ])
      setData({ connections, transfers, certificates, details })
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The connection could not be loaded.") }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const connection = data.connections.find((item) => item.id === connectionId)
  const family = data.connections.filter((item) => connection?.familyId ? item.familyId === connection.familyId : item.id === connectionId).toSorted((a, b) => (b.revision ?? 0) - (a.revision ?? 0))
  const files = data.transfers.filter((item) => item.partnerId === connectionId || family.some((revision) => revision.id === item.partnerId)).toSorted((a, b) => new Date(b.updatedAt ?? 0).getTime() - new Date(a.updatedAt ?? 0).getTime())
  const publicDetails = data.details.find((item) => item.partnerId === connectionId || family.some((revision) => revision.id === item.partnerId))
  const certificateNames = useMemo(() => new Map(data.certificates.map((item) => [item.id, item.name])), [data.certificates])

  async function lifecycle(action: "activate" | "suspend") {
    if (!connectionId) return
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await adminApi(`/api/mft/partners/${connectionId}/${action}`, { method: "POST" })
      setNotice(action === "activate" ? "Connection activated." : "Connection paused.")
      await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The connection could not be changed.") }
    finally { setBusy(false) }
  }

  async function copyDetails() {
    if (!publicDetails) return
    await navigator.clipboard.writeText([`AS2 identifier: ${publicDetails.as2Identifier}`, `Receiving URL: ${publicDetails.inboundUrl}`, `Asynchronous receipt URL: ${publicDetails.asynchronousMdnUrl}`, `Signing certificate SHA-256: ${publicDetails.certificateFingerprintSha256}`, `Encryption certificate SHA-256: ${publicDetails.encryptionCertificateFingerprintSha256}`].join("\n"))
    setNotice("Connection details copied.")
  }

  function downloadCertificate(pem?: string, name = "edispark-public.pem") {
    if (!pem) return
    const url = URL.createObjectURL(new Blob([pem], { type: "application/x-pem-file" }))
    const link = window.document.createElement("a"); link.href = url; link.download = name; link.click(); URL.revokeObjectURL(url)
  }

  if (loading) return <AdminPage><div className="h-96 animate-pulse bg-muted/40" /></AdminPage>
  if (!connection) return <AdminPage><Button asChild variant="ghost"><Link to="/connections"><ArrowLeft className="mr-2 h-4 w-4" />Connections</Link></Button><div className="mt-6"><AdminNotice>{error ?? "Connection not found."}</AdminNotice></div></AdminPage>

  return (
    <AdminPage className="max-w-6xl">
      <Button asChild variant="ghost" className="-ml-3 mb-3"><Link to="/connections"><ArrowLeft className="mr-2 h-4 w-4" />Connections</Link></Button>
      <AdminPageHeader title={connection.name ?? "Connection"} actions={<><Button variant="ghost" size="icon" onClick={() => void refresh()} aria-label="Refresh connection"><RefreshCw className="h-4 w-4" /></Button><Button asChild variant="outline"><Link to={`/connections/${connection.id}/edit`}><Edit3 className="mr-2 h-4 w-4" />Edit</Link></Button>{connection.status === "ACTIVE" ? <Button variant="outline" onClick={() => void lifecycle("suspend")} disabled={busy}><Pause className="mr-2 h-4 w-4" />Pause</Button> : <Button onClick={() => void lifecycle("activate")} disabled={busy}><Play className="mr-2 h-4 w-4" />Activate</Button>}<Button asChild disabled={connection.status !== "ACTIVE"}><Link to={`/files/send?connection=${connection.id}`}><FileUp className="mr-2 h-4 w-4" />Send file</Link></Button></>} />
      <div className="mb-6"><Badge variant="secondary">{sentenceCase(connection.status)}</Badge><span className="ml-2 text-xs text-muted-foreground">AS2 · revision {connection.revision ?? 1}</span></div>
      {error ? <AdminNotice>{error}</AdminNotice> : null}{notice ? <AdminNotice tone="success">{notice}</AdminNotice> : null}

      <section className="border-y" aria-labelledby="setup-title"><div className="border-b px-4 py-3"><h2 id="setup-title" className="text-sm font-semibold">Setup</h2></div><dl className="grid sm:grid-cols-2"><Definition label="Remote receiving URL" value={connection.endpoint?.toString()} /><Definition label="Receipt" value={connection.mdnMode === "ASYNCHRONOUS" ? "Separate" : "Immediate"} /><Definition label="Our AS2 identifier" value={connection.localAs2Id} mono /><Definition label="Remote AS2 identifier" value={connection.remoteAs2Id} mono /><Definition label="Our certificate" value={certificateNames.get(connection.localCertificateId)} /><Definition label="Remote signing certificate" value={certificateNames.get(connection.partnerSigningCertificateId)} /><Definition label="Remote encryption certificate" value={certificateNames.get(connection.partnerEncryptionCertificateId)} /><Definition label="Security" value={connection.signingRequired && connection.encryptionRequired ? "Signed and encrypted" : "Review required"} /></dl></section>

      {publicDetails ? <section className="mt-8 border-y" aria-labelledby="share-title"><div className="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3"><h2 id="share-title" className="text-sm font-semibold">Share with the remote team</h2><div className="flex gap-2"><Button variant="ghost" size="sm" onClick={() => void copyDetails()}><Copy className="mr-2 h-4 w-4" />Copy details</Button><Button variant="outline" size="sm" onClick={() => downloadCertificate(publicDetails.publicCertificatePem, "edispark-signing.pem")}><Download className="mr-2 h-4 w-4" />Signing certificate</Button><Button size="sm" onClick={() => downloadCertificate(publicDetails.encryptionPublicCertificatePem, "edispark-encryption.pem")}><Download className="mr-2 h-4 w-4" />Encryption certificate</Button></div></div><dl className="grid sm:grid-cols-2"><Definition label="Our receiving URL" value={publicDetails.inboundUrl} mono /><Definition label="Our receipt URL" value={publicDetails.asynchronousMdnUrl} mono /><Definition label="Signing fingerprint" value={publicDetails.certificateFingerprintSha256} mono /><Definition label="Encryption fingerprint" value={publicDetails.encryptionCertificateFingerprintSha256} mono /></dl></section> : null}

      <section className="mt-8 border-y" aria-labelledby="files-title"><div className="flex items-center justify-between border-b px-4 py-3"><h2 id="files-title" className="text-sm font-semibold">Files</h2><Button asChild variant="ghost" size="sm"><Link to={`/files?connection=${connection.id}`}>View all</Link></Button></div>{files.length ? <div className="divide-y">{files.slice(0, 7).map((file) => <Link key={file.id} to={`/files/transfers/${file.id}`} className="grid min-h-14 items-center gap-3 px-4 py-3 hover:bg-muted/40 sm:grid-cols-[minmax(14rem,1fr)_10rem_11rem_2rem]"><span className="truncate text-sm font-medium">{file.filename}</span><TransferStatus status={file.status} /><time className="text-xs text-muted-foreground">{formatAdminDate(file.updatedAt)}</time><ArrowRight className="h-4 w-4 text-muted-foreground" /></Link>)}</div> : <AdminEmptyState title="No files on this connection" action={connection.status === "ACTIVE" ? <Button asChild><Link to={`/files/send?connection=${connection.id}`}>Send file</Link></Button> : undefined} />}</section>

      <details className="mt-8 border-y"><summary className="cursor-pointer px-4 py-3 text-sm font-semibold">Version history</summary><div className="divide-y border-t">{family.map((revision) => <div key={revision.id} className="grid gap-2 px-4 py-3 text-sm sm:grid-cols-[6rem_10rem_1fr]"><span>Revision {revision.revision ?? 1}</span><Badge variant="secondary" className="w-fit">{sentenceCase(revision.status)}</Badge><span className="text-xs text-muted-foreground">{formatAdminDate(revision.updatedAt)}</span></div>)}</div></details>
    </AdminPage>
  )
}

function Definition({ label, value, mono = false }: { label: string; value?: string | null; mono?: boolean }) {
  return <div className="border-b px-4 py-4 sm:odd:border-r"><dt className="text-xs text-muted-foreground">{label}</dt><dd className={cn("mt-1 break-all text-sm font-medium", mono && "font-mono text-xs")}>{value || "Not set"}</dd></div>
}

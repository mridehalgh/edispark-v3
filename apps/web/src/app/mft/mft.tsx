"use client"

import type { As2ConnectionDetailsResponse, CertificateResponse, MdnDeliveryResponse, PartnerResponse, TransferResponse } from "@edispark/mft-client"
import { AlertTriangle, ArrowDownToLine, ArrowUpFromLine, BookOpen, CheckCircle2, ChevronDown, CircleAlert, Clock3, Copy, Download, FileUp, KeyRound, Plus, RefreshCw, ShieldCheck, X } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { Link } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { MftConfigurationControls } from "@/components/mft/mft-configuration-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { LOCAL_MFT_SIMULATOR_EVENT } from "@/lib/local-mft-simulator"
import { cn } from "@/lib/utils"

type ViewState = { transfers: TransferResponse[]; partners: PartnerResponse[]; certificates: CertificateResponse[]; connectionDetails: As2ConnectionDetailsResponse[]; mdnDeliveries: MdnDeliveryResponse[] }
type MftSection = "activity" | "partners" | "certificates"

const EMPTY_STATE: ViewState = { transfers: [], partners: [], certificates: [], connectionDetails: [], mdnDeliveries: [] }
const LOCAL_CERTIFICATE_UPLOADS = process.env.NODE_ENV === "development"
const STATUS_LABELS: Record<NonNullable<TransferResponse["status"]>, string> = {
  RECEIVED: "Received", QUEUED: "Queued", PROCESSING: "Sending", AWAITING_MDN: "Waiting for receipt",
  DELIVERED: "Delivered", RETRY_SCHEDULED: "Retry scheduled", NEEDS_REVIEW: "Needs attention", FAILED: "Failed",
}

async function readApi<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: "no-store" })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.error?.message ?? "The request could not be completed.")
  return body as T
}

function formatDate(value?: string) {
  if (!value) return "Not recorded"
  return new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
}

function StatusBadge({ status }: { status?: TransferResponse["status"] }) {
  const needsAttention = status === "NEEDS_REVIEW" || status === "FAILED"
  const delivered = status === "DELIVERED"
  const waiting = status === "RETRY_SCHEDULED" || status === "AWAITING_MDN"
  const Icon = needsAttention ? CircleAlert : delivered ? CheckCircle2 : waiting ? Clock3 : RefreshCw
  return <Badge className={cn("gap-1.5 border-0 font-medium", needsAttention && "bg-destructive/10 text-destructive", delivered && "bg-success/30 text-success-foreground", waiting && "bg-warning/25 text-warning-foreground", !needsAttention && !delivered && !waiting && "bg-primary/10 text-primary")}><Icon className="h-3 w-3" aria-hidden="true" />{status ? STATUS_LABELS[status] : "Unknown"}</Badge>
}

function EmptyState({ title, children }: { title: string; children: React.ReactNode }) {
  return <div className="px-6 py-12 text-center"><h3 className="text-sm font-semibold">{title}</h3><p className="mx-auto mt-1 max-w-md text-sm leading-6 text-muted-foreground">{children}</p></div>
}

export function MftPage() {
  const [state, setState] = useState<ViewState>(EMPTY_STATE)
  const [section, setSection] = useState<MftSection>("activity")
  const [showSendForm, setShowSendForm] = useState(false)
  const [showPartnerForm, setShowPartnerForm] = useState(false)
  const [showCertificateForm, setShowCertificateForm] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  const refresh = useCallback(async () => {
    setError(undefined)
    try {
      const [transfers, partners, certificates, connectionDetails, mdnDeliveries] = await Promise.all([
        readApi<TransferResponse[]>("/api/mft/transfers"),
        readApi<PartnerResponse[]>("/api/mft/partners"),
        readApi<CertificateResponse[]>("/api/mft/certificates"),
        readApi<As2ConnectionDetailsResponse[]>("/api/mft/connection-details"),
        readApi<MdnDeliveryResponse[]>("/api/mft/mdn-deliveries"),
      ])
      setState({ transfers, partners, certificates, connectionDetails, mdnDeliveries })
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "File exchange data could not be loaded.") }
    finally { setLoading(false) }
  }, [])

  useEffect(() => {
    void refresh()
    const handleSimulatorUpdate = () => { window.setTimeout(() => void refresh(), 1_000) }
    window.addEventListener(LOCAL_MFT_SIMULATOR_EVENT, handleSimulatorUpdate)
    return () => window.removeEventListener(LOCAL_MFT_SIMULATOR_EVENT, handleSimulatorUpdate)
  }, [refresh])

  const partnerNames = useMemo(() => new Map(state.partners.map((partner) => [partner.id, partner.name])), [state.partners])
  const attentionCount = state.transfers.filter(({ status }) => status === "NEEDS_REVIEW" || status === "FAILED").length
    + state.mdnDeliveries.filter(({ status }) => status === "FAILED").length
  const identityCertificates = state.certificates.filter((item) => item.kind === "LOCAL_IDENTITY")
  const partnerCertificates = state.certificates.filter((item) => item.kind === "PARTNER_PUBLIC")
  const activePartners = state.partners.filter((item) => item.status === "ACTIVE" || item.status === undefined)

  async function submitTransfer(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi("/api/mft/transfers", { method: "POST", body: new FormData(event.currentTarget), headers: { "Idempotency-Key": crypto.randomUUID() } })
      event.currentTarget.reset(); setShowSendForm(false)
      setNotice("File queued for delivery. Its receipt will appear in activity when the partner responds.")
      await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The file could not be sent.") }
    finally { setBusy(false) }
  }

  async function submitPartner(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget); setBusy(true); setError(undefined); setNotice(undefined)
    try {
      const partner = await readApi<PartnerResponse>("/api/mft/partners", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name: form.get("name"), localAs2Id: form.get("localAs2Id"), remoteAs2Id: form.get("remoteAs2Id"), endpoint: form.get("endpoint"), localCertificateId: form.get("localCertificateId"), partnerSigningCertificateId: form.get("partnerSigningCertificateId"), partnerEncryptionCertificateId: form.get("partnerEncryptionCertificateId"), asynchronousMdnEndpoint: form.get("asynchronousMdnEndpoint") || null, mdnMode: form.get("mdnMode"), signingRequired: true, encryptionRequired: true, compressionEnabled: false, legacyCryptoEnabled: false }) })
      await readApi(`/api/mft/partners/${partner.id}/activate`, { method: "POST" })
      event.currentTarget.reset(); setShowPartnerForm(false)
      setNotice("Trading partner checked and activated with signing and encryption enabled.")
      await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The trading partner could not be added.") }
    finally { setBusy(false) }
  }

  async function submitCertificate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi("/api/mft/certificates", { method: "POST", body: new FormData(event.currentTarget) })
      event.currentTarget.reset(); setShowCertificateForm(false)
      setNotice(LOCAL_CERTIFICATE_UPLOADS ? "Certificate checked and added to the local workspace." : "Partner public certificate checked and added.")
      await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The certificate could not be added.") }
    finally { setBusy(false) }
  }

  async function changePartnerStatus(partner: PartnerResponse) {
    const isActive = partner.status === "ACTIVE" || partner.status === undefined
    const action = isActive ? "suspend" : "activate"
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi(`/api/mft/partners/${partner.id}/${action}`, { method: "POST" })
      setNotice(isActive
        ? `${partner.name} is paused. New files will not be sent or accepted.`
        : `${partner.name} passed its security checks and is active.`)
      await refresh()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : `The trading partner could not be ${isActive ? "paused" : "activated"}.`)
    } finally { setBusy(false) }
  }

  async function revisePartner(partner: PartnerResponse) {
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi(`/api/mft/partners/${partner.id}/revisions`, { method: "POST" })
      setNotice(`Revision ${(partner.revision ?? 1) + 1} created for ${partner.name}.`)
      await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The AS2 revision could not be created.") }
    finally { setBusy(false) }
  }

  async function deleteDraftPartner(partner: PartnerResponse) {
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi(`/api/mft/partners/${partner.id}`, { method: "DELETE" })
      setNotice(`Unused draft revision for ${partner.name} deleted.`)
      await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The AS2 draft could not be deleted.") }
    finally { setBusy(false) }
  }

  async function deleteCertificate(certificate: CertificateResponse) {
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi(`/api/mft/certificates/${certificate.id}`, { method: "DELETE" })
      setNotice(`${certificate.name} and its unused material were deleted.`)
      await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The certificate is still assigned and cannot be deleted.") }
    finally { setBusy(false) }
  }

  async function updatePartnerDraft(partner: PartnerResponse, form: FormData) {
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi(`/api/mft/partners/${partner.id}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({
        name: form.get("name"), localAs2Id: partner.localAs2Id, remoteAs2Id: partner.remoteAs2Id,
        endpoint: form.get("endpoint"), localCertificateId: partner.localCertificateId,
        partnerSigningCertificateId: partner.partnerSigningCertificateId,
        partnerEncryptionCertificateId: partner.partnerEncryptionCertificateId,
        asynchronousMdnEndpoint: partner.asynchronousMdnEndpoint || undefined, mdnMode: partner.mdnMode,
        signingRequired: partner.signingRequired, encryptionRequired: partner.encryptionRequired,
        compressionEnabled: partner.compressionEnabled, legacyCryptoEnabled: partner.legacyCryptoEnabled,
      }) })
      setNotice(`Draft revision for ${partner.name} saved.`); await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The AS2 draft could not be saved.") }
    finally { setBusy(false) }
  }

  async function activatePartnerDraft(partner: PartnerResponse, effectiveAt?: string) {
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      const path = effectiveAt ? "schedule" : "activate"
      await readApi(`/api/mft/partners/${partner.id}/${path}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: effectiveAt ? JSON.stringify({ effectiveAt: new Date(effectiveAt).toISOString() }) : undefined })
      setNotice(effectiveAt ? `${partner.name} revision scheduled.` : `${partner.name} revision activated.`); await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The AS2 revision could not be activated.") }
    finally { setBusy(false) }
  }

  function openSendForm() { setSection("activity"); setShowSendForm(true) }

  async function copyConnectionDetails(details: As2ConnectionDetailsResponse) {
    const content = [
      "AS2 CONNECTION",
      "Organisation connection information",
      `AS2 identifier: ${details.as2Identifier}`,
      `Receiving URL: ${details.inboundUrl}`,
      `Asynchronous receipt URL: ${details.asynchronousMdnUrl}`,
      `Supported encryption algorithms: ${details.supportedEncryptionAlgorithms?.join(" ")}`,
      `Supported signature algorithms: ${details.supportedSignatureAlgorithms?.join(" ")}`,
      `Key encryption: ${details.keyEncryptionAlgorithm}`,
      `Signing certificate SHA-256: ${details.certificateFingerprintSha256}`,
      `Signing certificate expires: ${formatDate(details.certificateNotAfter)}`,
      `Encryption certificate SHA-256: ${details.encryptionCertificateFingerprintSha256}`,
      `Encryption certificate expires: ${formatDate(details.encryptionCertificateNotAfter)}`,
    ].join("\n")
    try {
      await navigator.clipboard.writeText(content)
      setNotice(`Connection details copied for ${details.partnerName}.`)
    } catch {
      setError("Connection details could not be copied. Copy the values individually instead.")
    }
  }

  function downloadPublicCertificate(details: As2ConnectionDetailsResponse, purpose: "signing" | "encryption") {
    const certificate = purpose === "signing" ? details.publicCertificatePem : details.encryptionPublicCertificatePem
    if (!certificate) return
    const objectUrl = URL.createObjectURL(new Blob([certificate], { type: "application/x-pem-file" }))
    const link = document.createElement("a")
    const safeAs2Id = (details.as2Identifier ?? "edispark").replace(/[^a-zA-Z0-9_-]/g, "-")
    link.href = objectUrl
    link.download = `${safeAs2Id}-as2-${purpose}-certificate.pem`
    link.click()
    URL.revokeObjectURL(objectUrl)
  }

  return <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
    <header className="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
      <h1 className="text-2xl font-semibold tracking-tight">Transport</h1>
      <div className="flex flex-wrap gap-2 self-start"><Button asChild variant="outline" className="gap-2"><Link to="/settings/connections/guide"><BookOpen className="h-4 w-4" aria-hidden="true" />Guide</Link></Button><Button variant="outline" size="icon" onClick={() => void refresh()} disabled={loading} aria-label="Refresh transport"><RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} /></Button><Button className="gap-2" onClick={openSendForm} disabled={activePartners.length === 0}><FileUp className="h-4 w-4" aria-hidden="true" />Test file</Button></div>
    </header>

    {error && <div role="alert" className="mb-4 flex gap-3 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive"><CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /><span>{error}</span></div>}
    {notice && <div role="status" className="mb-4 flex gap-3 rounded-md bg-success/25 px-4 py-3 text-sm text-success-foreground"><CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /><span>{notice}</span></div>}
    {attentionCount > 0 && <div className="mb-4 flex flex-col justify-between gap-3 rounded-md border border-destructive/25 bg-destructive/5 px-4 py-3 sm:flex-row sm:items-center" role="status"><div className="flex items-center gap-3"><AlertTriangle className="h-4 w-4 shrink-0 text-destructive" aria-hidden="true" /><p className="text-sm font-medium">{attentionCount} {attentionCount === 1 ? "delivery needs" : "deliveries need"} attention</p></div><Button variant="outline" size="sm" onClick={() => setSection("activity")}>Review activity</Button></div>}

    <Tabs value={section} onValueChange={(value) => setSection(value as MftSection)} className="space-y-4">
      <TabsList aria-label="File exchange sections"><TabsTrigger value="activity">Activity</TabsTrigger><TabsTrigger value="partners">Trading partners</TabsTrigger><TabsTrigger value="certificates">Certificates</TabsTrigger></TabsList>

      <MftConfigurationControls section={section} partners={state.partners} certificates={state.certificates} busy={busy} onRevise={(partner) => void revisePartner(partner)} onDeleteDraft={(partner) => void deleteDraftPartner(partner)} onUpdateDraft={(partner, form) => void updatePartnerDraft(partner, form)} onActivateDraft={(partner, effectiveAt) => void activatePartnerDraft(partner, effectiveAt)} onDeleteCertificate={(certificate) => void deleteCertificate(certificate)} />

      <TabsContent value="activity" className="space-y-4">
        {showSendForm && <section className="rounded-lg border bg-card" aria-labelledby="send-file-title"><div className="flex items-start justify-between gap-4 border-b px-4 py-3"><div><h2 id="send-file-title" className="text-sm font-semibold">Send a file</h2><p className="mt-0.5 text-xs text-muted-foreground">Choose an active partner that should receive this file.</p></div><Button variant="ghost" size="icon" onClick={() => setShowSendForm(false)} aria-label="Close send file form"><X className="h-4 w-4" /></Button></div><form onSubmit={submitTransfer} className="grid gap-4 p-4 lg:grid-cols-[minmax(14rem,1fr)_minmax(18rem,2fr)_auto] lg:items-end"><label className="grid gap-1.5 text-sm font-medium">Trading partner<select name="partnerId" required className="h-10 rounded-md border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><option value="">Choose a partner…</option>{activePartners.map((partner) => <option key={partner.id} value={partner.id}>{partner.name}</option>)}</select></label><label className="grid gap-1.5 text-sm font-medium">File<Input name="file" type="file" required accept=".edi,.x12,.dat,.txt,.xml,application/edi-x12,application/edifact" className="cursor-pointer file:mr-3 file:border-0 file:bg-transparent file:text-sm file:font-medium" /></label><Button type="submit" disabled={busy} className="gap-2"><FileUp className="h-4 w-4" />{busy ? "Queuing…" : "Send securely"}</Button></form></section>}

        {state.mdnDeliveries.some(({ status }) => status === "FAILED") && <section className="rounded-lg border border-destructive/25 bg-destructive/5" aria-labelledby="receipt-failures-title"><div className="border-b border-destructive/15 px-4 py-3"><h2 id="receipt-failures-title" className="text-sm font-semibold">Receipts needing attention</h2><p className="mt-0.5 text-xs text-muted-foreground">The file was accepted, but its separate signed receipt could not be delivered.</p></div><div className="divide-y divide-destructive/15">{state.mdnDeliveries.filter(({ status }) => status === "FAILED").map((receipt) => <div key={receipt.id} className="flex flex-col justify-between gap-2 px-4 py-3 sm:flex-row sm:items-center"><div><p className="font-mono text-xs">{receipt.originalMessageId}</p><p className="mt-1 text-xs text-muted-foreground">{partnerNames.get(receipt.partnerId) ?? "Unknown partner"} · {receipt.attempts} attempts · {receipt.lastFailure ?? "Delivery failed"}</p></div><Badge className="w-fit border-0 bg-destructive/10 text-destructive"><CircleAlert className="mr-1 h-3 w-3" aria-hidden="true" />Receipt failed</Badge></div>)}</div></section>}

        <section className="overflow-hidden rounded-lg border bg-card" aria-labelledby="activity-title"><div className="flex items-center justify-between border-b px-4 py-3"><div><h2 id="activity-title" className="text-sm font-semibold">Recent activity</h2><p className="text-xs text-muted-foreground">Incoming and outgoing files for your organisation.</p></div><span className="text-xs tabular-nums text-muted-foreground">{state.transfers.length} records</span></div><div className="overflow-x-auto"><table className="w-full min-w-[66rem] text-left text-sm"><thead className="border-b bg-muted/55 text-xs text-muted-foreground"><tr><th className="px-4 py-2.5 font-medium">File</th><th className="px-4 py-2.5 font-medium">Partner</th><th className="px-4 py-2.5 font-medium">Flow</th><th className="px-4 py-2.5 font-medium">Status</th><th className="px-4 py-2.5 font-medium">Receipt</th><th className="px-4 py-2.5 font-medium">Last updated</th></tr></thead><tbody className="divide-y">{state.transfers.map((transfer) => {
          const receipt = transfer.failureReason ?? (transfer.mdnDisposition
            ? transfer.direction === "INBOUND" ? "Receipt sent" : "Confirmed by partner"
            : transfer.status === "DELIVERED" ? "Processed" : "Not received yet")
          return <tr key={transfer.id} className="transition-colors hover:bg-muted/40"><td className="px-4 py-3"><span className="block font-medium">{transfer.filename}</span><span className="font-mono text-xs text-muted-foreground">{transfer.sha256?.slice(0, 12)}…</span></td><td className="px-4 py-3">{partnerNames.get(transfer.partnerId) ?? "Unknown partner"}</td><td className="px-4 py-3"><span className="inline-flex items-center gap-1.5">{transfer.direction === "INBOUND" ? <ArrowDownToLine className="h-3.5 w-3.5" aria-hidden="true" /> : <ArrowUpFromLine className="h-3.5 w-3.5" aria-hidden="true" />}{transfer.direction === "INBOUND" ? "Received" : "Sent"}</span></td><td className="px-4 py-3"><StatusBadge status={transfer.status} /></td><td className={cn("max-w-[18rem] px-4 py-3 text-xs", transfer.failureReason ? "text-destructive" : "text-muted-foreground")}>{receipt}</td><td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(transfer.updatedAt)}</td></tr>
        })}{!loading && state.transfers.length === 0 && <tr><td colSpan={6}><EmptyState title="No file activity yet">Send a file to a trading partner or wait for an incoming delivery. Each exchange will appear here with its receipt.</EmptyState></td></tr>}</tbody></table></div></section>
      </TabsContent>

      <TabsContent value="partners" className="space-y-4"><section className="overflow-hidden rounded-lg border bg-card" aria-labelledby="partners-title"><div className="flex flex-col justify-between gap-3 border-b px-4 py-4 sm:flex-row sm:items-center"><div><h2 id="partners-title" className="text-sm font-semibold">Trading partners</h2><p className="mt-0.5 text-xs text-muted-foreground">The organisations you exchange files with and the connection details they supplied.</p></div><Button size="sm" className="gap-2 self-start" onClick={() => setShowPartnerForm((value) => !value)}><Plus className="h-4 w-4" aria-hidden="true" />Add trading partner</Button></div>
        {showPartnerForm && <form onSubmit={submitPartner} className="grid gap-4 border-b bg-muted/20 p-4 lg:grid-cols-2"><div className="lg:col-span-2"><h3 className="text-sm font-semibold">Connection details</h3><p className="mt-1 max-w-3xl text-xs leading-5 text-muted-foreground">Ask your partner for their AS2 identifier, receiving URL, public signing and encryption certificates, and receipt preference. AS2 identifiers are case-sensitive.</p></div><label className="grid gap-1.5 text-sm font-medium">Partner name<Input name="name" required placeholder="Example Retailer" /></label><label className="grid gap-1.5 text-sm font-medium">Partner receiving URL<Input name="endpoint" type="url" placeholder="https://partner.example/as2" required /></label><label className="grid gap-1.5 text-sm font-medium">Your AS2 identifier<Input name="localAs2Id" required /></label><label className="grid gap-1.5 text-sm font-medium">Partner AS2 identifier<Input name="remoteAs2Id" required /></label><label className="grid gap-1.5 text-sm font-medium">Your signing certificate<select name="localCertificateId" required className="h-10 rounded-md border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><option value="">Choose a certificate…</option>{identityCertificates.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label className="grid gap-1.5 text-sm font-medium">Partner signing certificate<select name="partnerSigningCertificateId" required className="h-10 rounded-md border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><option value="">Choose a certificate…</option>{partnerCertificates.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label className="grid gap-1.5 text-sm font-medium">Partner encryption certificate<select name="partnerEncryptionCertificateId" required className="h-10 rounded-md border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><option value="">Choose a certificate…</option>{partnerCertificates.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label className="grid gap-1.5 text-sm font-medium">Receipt delivery<select name="mdnMode" className="h-10 rounded-md border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><option value="SYNCHRONOUS">Return receipt immediately</option><option value="ASYNCHRONOUS">Return receipt separately</option></select></label><label className="grid gap-1.5 text-sm font-medium lg:col-span-2">Separate receipt URL <span className="text-xs font-normal text-muted-foreground">Required only for separate receipts</span><Input name="asynchronousMdnEndpoint" type="url" placeholder="https://partner.example/as2/mdn" /></label><div className="flex items-end justify-end gap-2 lg:col-span-2"><Button type="button" variant="ghost" onClick={() => setShowPartnerForm(false)}>Cancel</Button><Button disabled={busy || identityCertificates.length === 0 || partnerCertificates.length === 0}>{busy ? "Adding…" : "Add trading partner"}</Button></div></form>}
        <div className="divide-y">{state.partners.map((partner) => {
          const secure = partner.signingRequired && partner.encryptionRequired
          const active = partner.status === "ACTIVE" || partner.status === undefined
          const details = state.connectionDetails.find((item) => item.partnerId === partner.id)
          return <article key={partner.id} className="grid gap-4 px-4 py-4 transition-colors hover:bg-muted/25 lg:grid-cols-[minmax(13rem,1.2fr)_minmax(14rem,1fr)_minmax(16rem,1.5fr)_auto] lg:items-center"><div><h3 className="font-semibold">{partner.name}</h3><p className="mt-0.5 font-mono text-xs text-muted-foreground">{partner.remoteAs2Id}</p></div><div><p className="text-xs text-muted-foreground">Your AS2 identifier</p><p className="mt-0.5 font-mono text-xs">{partner.localAs2Id}</p></div><div className="min-w-0"><p className="text-xs text-muted-foreground">Partner receiving URL</p><p className="mt-0.5 truncate text-xs" title={partner.endpoint}>{partner.endpoint}</p></div><div className="flex flex-wrap items-center gap-2 lg:justify-end"><Badge className={cn("border-0", active ? "bg-success/30 text-success-foreground" : "bg-muted text-muted-foreground")}>{active ? "Active" : partner.status === "DRAFT" ? "Setup needed" : "Paused"}</Badge><span className={cn("inline-flex items-center gap-1 text-xs font-medium", secure ? "text-success-foreground" : "text-destructive")}>{secure ? <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" /> : <CircleAlert className="h-3.5 w-3.5" aria-hidden="true" />}{secure ? "Signed and encrypted" : "Security check needed"}</span><Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => void changePartnerStatus(partner)}>{active ? "Pause" : "Activate"}</Button></div>{details && <details className="group border-t pt-3 lg:col-span-4"><summary className="inline-flex cursor-pointer list-none items-center gap-1 text-sm font-medium text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">Share your AS2 details with this partner <ChevronDown className="h-4 w-4 transition-transform group-open:rotate-180 motion-reduce:transition-none" aria-hidden="true" /></summary><div className="mt-4 rounded-md bg-muted/40 p-4"><div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start"><div><h4 className="text-sm font-semibold">Your connection information</h4><p className="mt-1 max-w-2xl text-xs leading-5 text-muted-foreground">Send these values and both public certificates to {partner.name}. They use the signing certificate to verify EdiSpark and the encryption certificate to protect files sent to you. No private key material is included.</p></div><div className="flex flex-wrap gap-2"><Button type="button" variant="outline" size="sm" className="gap-2" onClick={() => void copyConnectionDetails(details)}><Copy className="h-3.5 w-3.5" aria-hidden="true" />Copy details</Button><Button type="button" variant="outline" size="sm" className="gap-2" onClick={() => downloadPublicCertificate(details, "signing")} disabled={!details.publicCertificatePem}><Download className="h-3.5 w-3.5" aria-hidden="true" />Signing certificate</Button><Button type="button" size="sm" className="gap-2" onClick={() => downloadPublicCertificate(details, "encryption")} disabled={!details.encryptionPublicCertificatePem}><Download className="h-3.5 w-3.5" aria-hidden="true" />Encryption certificate</Button></div></div><dl className="mt-5 grid gap-x-8 gap-y-4 md:grid-cols-2"><div><dt className="text-xs text-muted-foreground">AS2 identifier</dt><dd className="mt-1 font-mono text-sm">{details.as2Identifier}</dd></div><div><dt className="text-xs text-muted-foreground">Receiving URL</dt><dd className="mt-1 break-all font-mono text-xs">{details.inboundUrl}</dd></div><div><dt className="text-xs text-muted-foreground">Asynchronous receipt URL</dt><dd className="mt-1 break-all font-mono text-xs">{details.asynchronousMdnUrl}</dd></div><div><dt className="text-xs text-muted-foreground">Key encryption</dt><dd className="mt-1 text-sm">{details.keyEncryptionAlgorithm}</dd></div><div><dt className="text-xs text-muted-foreground">Supported encryption</dt><dd className="mt-1 flex flex-wrap gap-1.5">{details.supportedEncryptionAlgorithms?.map((algorithm) => <Badge key={algorithm} variant="secondary">{algorithm}</Badge>)}</dd></div><div><dt className="text-xs text-muted-foreground">Supported signatures</dt><dd className="mt-1 flex flex-wrap gap-1.5">{details.supportedSignatureAlgorithms?.map((algorithm) => <Badge key={algorithm} variant="secondary">{algorithm}</Badge>)}</dd></div><div><dt className="text-xs text-muted-foreground">Signing certificate SHA-256</dt><dd className="mt-1 break-all font-mono text-xs">{details.certificateFingerprintSha256}</dd><p className="mt-1 text-xs text-muted-foreground">Expires {formatDate(details.certificateNotAfter)}</p></div><div><dt className="text-xs text-muted-foreground">Encryption certificate SHA-256</dt><dd className="mt-1 break-all font-mono text-xs">{details.encryptionCertificateFingerprintSha256}</dd><p className="mt-1 text-xs text-muted-foreground">Expires {formatDate(details.encryptionCertificateNotAfter)}</p></div></dl></div></details>}</article>
        })}{!loading && state.partners.length === 0 && <EmptyState title="No trading partners yet">Add both certificates first, then use the details supplied by your partner to create a secure connection.</EmptyState>}</div>
      </section></TabsContent>

      <TabsContent value="certificates" className="space-y-4"><section className="overflow-hidden rounded-lg border bg-card" aria-labelledby="certificates-title"><div className="flex flex-col justify-between gap-3 border-b px-4 py-4 sm:flex-row sm:items-center"><div><h2 id="certificates-title" className="text-sm font-semibold">Certificates</h2><p className="mt-0.5 text-xs text-muted-foreground">Certificates prove identity and protect files exchanged with each partner.</p></div><Button size="sm" className="gap-2 self-start" onClick={() => setShowCertificateForm((value) => !value)}><Plus className="h-4 w-4" aria-hidden="true" />Add certificate</Button></div>
        {showCertificateForm && <form onSubmit={submitCertificate} className="grid gap-4 border-b bg-muted/20 p-4 lg:grid-cols-2"><div className="lg:col-span-2"><h3 className="text-sm font-semibold">Add a certificate</h3><p className="mt-1 max-w-3xl text-xs leading-5 text-muted-foreground">{LOCAL_CERTIFICATE_UPLOADS ? "Local development also accepts a disposable simulator identity. In production, you only upload public certificates supplied by partners." : "Upload the public certificate supplied by your trading partner. Never upload their private key."}</p></div><label className="grid gap-1.5 text-sm font-medium">Certificate name<Input name="name" required placeholder="Partner name · encryption" /></label><label className="grid gap-1.5 text-sm font-medium">Certificate purpose<select name="kind" className="h-10 rounded-md border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">{LOCAL_CERTIFICATE_UPLOADS && <option value="LOCAL_IDENTITY">Local simulator identity</option>}<option value="PARTNER_PUBLIC">Partner public certificate</option></select></label><label className="grid gap-1.5 text-sm font-medium">Certificate file<Input name="file" type="file" required accept=".p12,.pfx,.pem,.cer,.crt" /></label>{LOCAL_CERTIFICATE_UPLOADS && <label className="grid gap-1.5 text-sm font-medium">Simulator identity password <span className="text-xs font-normal text-muted-foreground">Leave blank for a public certificate</span><Input name="password" type="password" autoComplete="new-password" /></label>}<div className="flex items-end justify-end gap-2 lg:col-span-2"><Button type="button" variant="ghost" onClick={() => setShowCertificateForm(false)}>Cancel</Button><Button disabled={busy} className="gap-2"><KeyRound className="h-4 w-4" />{busy ? "Checking…" : "Check and add"}</Button></div></form>}
        <div className="divide-y">{state.certificates.map((certificate) => {
          const expiring = (certificate.daysUntilExpiry ?? 0) < 30
          return <article key={certificate.id} className="grid gap-4 px-4 py-4 transition-colors hover:bg-muted/25 lg:grid-cols-[minmax(13rem,1.2fr)_minmax(16rem,1.4fr)_minmax(12rem,1fr)_auto] lg:items-center"><div><h3 className="font-semibold">{certificate.name}</h3><p className="mt-0.5 text-xs text-muted-foreground">{certificate.kind === "LOCAL_IDENTITY" ? "Your sending identity" : "Partner public certificate"}</p></div><div className="min-w-0"><p className="text-xs text-muted-foreground">Issued to</p><p className="mt-0.5 truncate text-xs" title={certificate.subject}>{certificate.subject}</p></div><div><p className="text-xs text-muted-foreground">Expires</p><p className="mt-0.5 text-xs">{formatDate(certificate.notAfter)}</p></div><Badge className={cn("w-fit gap-1.5 border-0", expiring ? "bg-warning/25 text-warning-foreground" : "bg-success/30 text-success-foreground")}><span className="sr-only">Certificate status:</span>{expiring ? <AlertTriangle className="h-3 w-3" aria-hidden="true" /> : <CheckCircle2 className="h-3 w-3" aria-hidden="true" />}{expiring ? "Renew soon" : "Valid"}</Badge><details className="group lg:col-span-4"><summary className="inline-flex cursor-pointer list-none items-center gap-1 text-xs font-medium text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">View certificate fingerprint <ChevronDown className="h-3.5 w-3.5 transition-transform group-open:rotate-180 motion-reduce:transition-none" aria-hidden="true" /></summary><p className="mt-2 break-all font-mono text-xs text-muted-foreground">{certificate.fingerprintSha256}</p></details></article>
        })}{!loading && state.certificates.length === 0 && <EmptyState title="No certificates yet">Add the public certificate supplied by your partner. You will use it when you create their connection.</EmptyState>}</div>
      </section></TabsContent>
    </Tabs>
  </LayoutBody>
}

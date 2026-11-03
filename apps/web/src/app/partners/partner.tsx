"use client"

import type { ExchangeResponse, MappingRevision, TradingAgreement, TradingPartner } from "@edispark/integration-client"
import type { As2ConnectionDetailsResponse, CertificateResponse, PartnerResponse, TransferResponse } from "@edispark/mft-client"
import { ArrowLeft, ArrowRight, Copy, Download, Edit3, FileText, Pause, Plug, Plus, RefreshCw, Send, Trash2, X } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { Link, useParams, useSearchParams } from "react-router-dom"

import { AdminEmptyState, AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { PartnerRevisionEditor } from "@/components/trading-network/partner-revision-editor"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { cn } from "@/lib/utils"

type PartnerData = {
  partners: TradingPartner[]
  agreements: TradingAgreement[]
  exchanges: ExchangeResponse[]
  connections: PartnerResponse[]
  certificates: CertificateResponse[]
  transfers: TransferResponse[]
  mappings: MappingRevision[]
  connectionDetails: As2ConnectionDetailsResponse[]
}

const EMPTY_DATA: PartnerData = { partners: [], agreements: [], exchanges: [], connections: [], certificates: [], transfers: [], mappings: [], connectionDetails: [] }
const PARTNER_TAB_CLASS = "min-h-10 shrink-0 justify-start rounded-md px-3 text-muted-foreground shadow-none data-[state=active]:bg-accent data-[state=active]:text-foreground data-[state=active]:shadow-none lg:w-full"

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

function stateLabel(value?: string) {
  if (value === "ACTIVE") return "Connected"
  if (value === "DELIVERED") return "Delivered"
  if (value === "AWAITING_MDN") return "Waiting for receipt"
  if (value === "NEEDS_REVIEW") return "Needs attention"
  return value?.toLowerCase().replaceAll("_", " ") ?? "Setup"
}

function Detail({ label, value, mono = false }: { label: string; value?: string | null; mono?: boolean }) {
  return <div className="py-3"><dt className="text-xs text-muted-foreground">{label}</dt><dd className={cn("mt-1 break-all text-sm font-medium", mono && "font-mono text-xs")}>{value || "Not set"}</dd></div>
}

export function PartnerPage() {
  const { partnerId } = useParams<{ partnerId: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const [data, setData] = useState<PartnerData>(EMPTY_DATA)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const [addingConnection, setAddingConnection] = useState(false)
  const [addingDocument, setAddingDocument] = useState(false)
  const [sendingFile, setSendingFile] = useState(false)
  const [documentDirection, setDocumentDirection] = useState<"ALL" | "OUTBOUND" | "INBOUND">("ALL")
  const requestedView = searchParams.get("view")
  const activeView = requestedView === "documents" || requestedView === "files" ? requestedView : "overview"

  function changeView(value: string) {
    const next = new URLSearchParams(searchParams)
    if (value === "overview") next.delete("view")
    else next.set("view", value)
    setSearchParams(next, { replace: true })
  }

  const refresh = useCallback(async () => {
    setError(undefined)
    try {
      const [partners, agreements, exchanges, connections, certificates, transfers, mappings, connectionDetails] = await Promise.all([
        readApi<TradingPartner[]>("/api/integration/partners"),
        readApi<TradingAgreement[]>("/api/integration/agreements"),
        readApi<ExchangeResponse[]>("/api/integration/exchanges"),
        readApi<PartnerResponse[]>("/api/mft/partners"),
        readApi<CertificateResponse[]>("/api/mft/certificates"),
        readApi<TransferResponse[]>("/api/mft/transfers"),
        readApi<MappingRevision[]>("/api/integration/mappings"),
        readApi<As2ConnectionDetailsResponse[]>("/api/mft/connection-details"),
      ])
      setData({ partners, agreements, exchanges, connections, certificates, transfers, mappings, connectionDetails })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "The partner could not be loaded.")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const partner = data.partners.find((item) => item.id === partnerId)
  const partnerRevisions = data.partners.filter((item) => item.familyId && item.familyId === partner?.familyId)
  const partnerDraft = partner?.status === "DRAFT" ? partner : partnerRevisions.find((item) => item.status === "DRAFT")
  const familyIds = useMemo(() => new Set(data.partners.filter((item) => item.familyId && item.familyId === partner?.familyId).map((item) => item.id)), [data.partners, partner?.familyId])
  if (partner?.id) familyIds.add(partner.id)

  const agreements = data.agreements.filter((item) => item.partnerId && familyIds.has(item.partnerId))
  const agreement = agreements.find((item) => item.status === "DRAFT" || item.status === "QUALIFYING")
    ?? agreements.find((item) => item.status === "ACTIVE")
    ?? agreements.toSorted((left, right) => (right.revision ?? 0) - (left.revision ?? 0))[0]
  const connectionIds = new Set(agreements.flatMap((item) => item.capabilities?.map((capability) => capability.as2ConnectionId).filter(Boolean) ?? []))
  const connection = data.connections.find((item) => item.id && connectionIds.has(item.id))
    ?? data.connections.find((item) => item.name?.toLocaleLowerCase() === partner?.displayName?.toLocaleLowerCase() && item.status === "ACTIVE")
    ?? data.connections.find((item) => item.name?.toLocaleLowerCase() === partner?.displayName?.toLocaleLowerCase())
  if (connection?.id) connectionIds.add(connection.id)
  const connectionDraft = data.connections.find((item) => item.status === "DRAFT" && (
    (connection?.familyId && item.familyId === connection.familyId)
    || item.name?.toLocaleLowerCase() === partner?.displayName?.toLocaleLowerCase()
  ))
  const publicConnectionDetails = data.connectionDetails.find((item) => item.partnerId && connectionIds.has(item.partnerId))

  const capabilities = agreement?.capabilities ?? []
  const visibleCapabilities = capabilities.filter((item) => documentDirection === "ALL" || item.direction === documentDirection)
  const exchanges = data.exchanges.filter((item) => item.partnerId && familyIds.has(item.partnerId))
  const transfers = data.transfers.filter((item) => item.partnerId && connectionIds.has(item.partnerId))
  const activity = [
    ...exchanges.map((item) => ({ id: item.id, name: item.generatedDocument?.documentId ?? item.sourceDocument?.documentId ?? "Document exchange", status: item.status, updatedAt: item.updatedAt })),
    ...transfers.map((item) => ({ id: item.id, name: item.filename ?? "File transfer", status: item.status, updatedAt: item.updatedAt })),
  ].sort((left, right) => new Date(right.updatedAt ?? 0).getTime() - new Date(left.updatedAt ?? 0).getTime()).slice(0, 6)

  async function perform(url: string, init: RequestInit, success: string) {
    setBusy(true)
    setError(undefined)
    setNotice(undefined)
    try {
      await readApi(url, init)
      setNotice(success)
      await refresh()
      return true
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "The change could not be saved.")
      return false
    } finally {
      setBusy(false)
    }
  }

  async function revisePartner() {
    if (!partner?.id) return
    await perform(`/api/integration/partners/${partner.id}/revisions`, { method: "POST" }, "Partner is ready to edit.")
  }

  async function savePartnerDraft(item: TradingPartner, form: FormData) {
    await perform(`/api/integration/partners/${item.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ displayName: form.get("displayName"), identities: [{ scheme: form.get("scheme"), value: form.get("value") }], inboundFileFailurePolicy: form.get("inboundFileFailurePolicy") }),
    }, "Partner saved.")
  }

  async function activatePartnerDraft(item: TradingPartner, effectiveAt?: string) {
    await perform(`/api/integration/partners/${item.id}/activate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ effectiveAt: effectiveAt ? new Date(effectiveAt).toISOString() : undefined }),
    }, effectiveAt ? "Partner update scheduled." : "Partner updated.")
  }

  async function discardPartnerDraft() {
    if (!partnerDraft?.id) return
    await perform(`/api/integration/partners/${partnerDraft.id}`, { method: "DELETE" }, "Partner edit discarded.")
  }

  async function createConnection(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      const created = await readApi<PartnerResponse>("/api/mft/partners", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: partner?.displayName,
          endpoint: form.get("endpoint"),
          localAs2Id: form.get("localAs2Id"),
          remoteAs2Id: form.get("remoteAs2Id"),
          localCertificateId: form.get("localCertificateId"),
          partnerSigningCertificateId: form.get("partnerCertificateId"),
          partnerEncryptionCertificateId: form.get("partnerCertificateId"),
          mdnMode: form.get("mdnMode"),
          signingRequired: true,
          encryptionRequired: true,
          compressionEnabled: false,
          legacyCryptoEnabled: false,
        }),
      })
      await readApi(`/api/mft/partners/${created.id}/activate`, { method: "POST" })
      formElement.reset(); setAddingConnection(false); setNotice("Connection added."); await refresh()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "The connection could not be added.")
    } finally { setBusy(false) }
  }

  async function reviseConnection() {
    if (!connection?.id) return
    await perform(`/api/mft/partners/${connection.id}/revisions`, { method: "POST" }, "Connection is ready to edit.")
  }

  async function saveConnectionDraft(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!connectionDraft?.id) return
    const form = new FormData(event.currentTarget)
    await perform(`/api/mft/partners/${connectionDraft.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...connectionDraft, endpoint: form.get("endpoint"), name: partner?.displayName }),
    }, "Connection saved.")
  }

  async function activateConnectionDraft() {
    if (!connectionDraft?.id) return
    await perform(`/api/mft/partners/${connectionDraft.id}/activate`, { method: "POST" }, "Connection updated.")
  }

  async function changeConnectionStatus() {
    if (!connection?.id) return
    const action = connection.status === "ACTIVE" ? "suspend" : "activate"
    await perform(`/api/mft/partners/${connection.id}/${action}`, { method: "POST" }, action === "suspend" ? "Connection paused." : "Connection turned on.")
  }

  async function editDocuments() {
    if (!partner?.id) return
    if (!agreement) {
      await perform(`/api/integration/partners/${partner.id}/agreements`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: `${partner.displayName} documents` }),
      }, "Document exchange created.")
      setAddingDocument(true)
      return
    }
    if (agreement.status === "ACTIVE") {
      await perform(`/api/integration/agreements/${agreement.id}/revisions`, { method: "POST" }, "Documents are ready to edit.")
      setAddingDocument(true)
    } else {
      setAddingDocument((value) => !value)
    }
  }

  async function addDocument(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!agreement?.id || !connection?.id) return setError("Add an active connection first.")
    const form = new FormData(event.currentTarget)
    const direction = String(form.get("direction")) as "INBOUND" | "OUTBOUND"
    const documentType = String(form.get("documentType")) as "ORDER" | "INVOICE"
    const standard = String(form.get("standard")) as "TRADACOMS" | "EDIFACT"
    const search = [direction === "INBOUND" ? "inbound" : "outbound", documentType.toLowerCase(), standard.toLowerCase()]
    const mapping = data.mappings.find((item) => item.status === "PUBLISHED" && search.every((term) => item.name?.toLowerCase().includes(term)))
      ?? data.mappings.find((item) => item.status === "PUBLISHED" && item.name?.toLowerCase().includes(standard.toLowerCase()) && item.name?.toLowerCase().includes(documentType.toLowerCase()))
    if (!mapping?.id || !mapping.sourceSchemaId || !mapping.targetSchemaId) return setError("That document exchange is not available yet.")
    await perform(`/api/integration/agreements/${agreement.id}/capabilities`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ direction, documentType, standard, release: standard === "EDIFACT" ? "D96A" : "9", messageType: documentType === "ORDER" ? "ORDERS" : "INVOIC", sourceSchemaId: mapping.sourceSchemaId, targetSchemaId: mapping.targetSchemaId, mappingRevisionId: mapping.id, as2ConnectionId: connection.id, acknowledgementPolicy: "TRANSPORT_ONLY", batchingPolicy: "INDIVIDUAL" }),
    }, "Document exchange added.")
  }

  async function changeDocumentStatus(action: "qualify" | "activate" | "suspend") {
    if (!agreement?.id) return
    await perform(`/api/integration/agreements/${agreement.id}/${action}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: action === "activate" ? "{}" : undefined }, action === "qualify" ? "Checks complete." : action === "activate" ? "Document exchange is on." : "Document exchange paused.")
  }

  async function removeDocument(capabilityId?: string) {
    if (!agreement?.id || !capabilityId) return
    await perform(`/api/integration/agreements/${agreement.id}/capabilities/${capabilityId}`, { method: "DELETE" }, "Document exchange removed.")
  }

  async function discardDocumentEdit() {
    if (!agreement?.id || agreement.status !== "DRAFT") return
    await perform(`/api/integration/agreements/${agreement.id}`, { method: "DELETE" }, "Document edit discarded.")
    setAddingDocument(false)
  }

  async function sendFile(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!connection?.id) return
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    form.set("partnerId", connection.id)
    const saved = await perform("/api/mft/transfers", { method: "POST", body: form, headers: { "Idempotency-Key": crypto.randomUUID() } }, "File queued.")
    if (saved) { formElement.reset(); setSendingFile(false) }
  }

  async function copyConnectionDetails() {
    if (!publicConnectionDetails) return
    await navigator.clipboard.writeText([
      `AS2 identifier: ${publicConnectionDetails.as2Identifier}`,
      `Receiving URL: ${publicConnectionDetails.inboundUrl}`,
      `Asynchronous receipt URL: ${publicConnectionDetails.asynchronousMdnUrl}`,
      `Signing certificate SHA-256: ${publicConnectionDetails.certificateFingerprintSha256}`,
      `Encryption certificate SHA-256: ${publicConnectionDetails.encryptionCertificateFingerprintSha256}`,
    ].join("\n"))
    setNotice("AS2 details copied.")
  }

  function downloadCertificate(pem?: string, filename = "edispark-public.pem") {
    if (!pem) return
    const url = URL.createObjectURL(new Blob([pem], { type: "application/x-pem-file" }))
    const link = window.document.createElement("a")
    link.href = url
    link.download = filename
    link.click()
    URL.revokeObjectURL(url)
  }

  if (!loading && !partner) {
    return <AdminPage><Button asChild variant="ghost"><Link to="/partners"><ArrowLeft className="mr-2 h-4 w-4" />Partners</Link></Button><AdminEmptyState title="Partner not found" /></AdminPage>
  }

  return (
    <AdminPage className="max-w-[96rem]">
      <Button asChild variant="ghost" className="-ml-3 mb-3"><Link to="/partners"><ArrowLeft className="mr-2 h-4 w-4" aria-hidden="true" />Partners</Link></Button>
      <AdminPageHeader
        title={partner?.displayName ?? "Partner"}
        actions={<><Button variant="ghost" size="icon" onClick={() => void refresh()} disabled={loading} aria-label="Refresh partner"><RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} /></Button>{partner?.status === "ACTIVE" && !partnerDraft ? <Button variant="outline" onClick={() => void revisePartner()} disabled={busy}><Edit3 className="mr-2 h-4 w-4" />Edit partner</Button> : null}</>}
      />
      {error ? <AdminNotice>{error}</AdminNotice> : null}
      {notice ? <AdminNotice tone="success">{notice}</AdminNotice> : null}

      <Tabs value={activeView} onValueChange={changeView} className="mt-6 grid items-start gap-8 lg:grid-cols-[13rem_minmax(0,1fr)] lg:gap-10">
        <aside className="border-b pb-3 lg:sticky lg:top-20 lg:border-b-0 lg:pb-0" aria-label="Partner navigation">
          <p className="mb-2 hidden px-3 text-xs font-medium text-muted-foreground lg:block">Partner</p>
          <TabsList aria-label="Partner views" className="h-auto w-full justify-start gap-1 overflow-x-auto rounded-none bg-transparent p-0 lg:flex-col lg:items-stretch lg:overflow-visible">
            <TabsTrigger value="overview" className={PARTNER_TAB_CLASS}>Overview</TabsTrigger>
            <TabsTrigger value="documents" className={PARTNER_TAB_CLASS}>Documents</TabsTrigger>
            <TabsTrigger value="files" className={PARTNER_TAB_CLASS}>Files</TabsTrigger>
          </TabsList>
          <dl className="mt-4 hidden border-t px-3 pt-4 text-xs lg:block">
            <dt className="text-muted-foreground">Status</dt>
            <dd className="mt-1 font-medium">{stateLabel(connection?.status ?? partner?.status)}</dd>
            {partner?.identities?.[0] ? <><dt className="mt-4 text-muted-foreground">{partner.identities[0].scheme}</dt><dd className="mt-1 break-all font-mono">{partner.identities[0].value}</dd></> : null}
          </dl>
        </aside>

        <div className="min-w-0">
        <TabsContent value="overview" className="mt-4 lg:mt-0">
      <section>
        {partnerDraft ? <details className="border-y"><summary className="flex cursor-pointer list-none items-center justify-between gap-3 py-3 text-sm font-medium"><span>Unpublished partner changes</span><Badge variant="secondary">Revision {partnerDraft.revision ?? 1}</Badge></summary><div className="border-t pt-4"><PartnerRevisionEditor partner={partnerDraft} busy={busy} onSave={(item, form) => void savePartnerDraft(item, form)} onActivate={(item, effectiveAt) => void activatePartnerDraft(item, effectiveAt)} /><div className="flex justify-end border-x border-b p-2"><Button variant="ghost" size="sm" onClick={() => void discardPartnerDraft()} disabled={busy}><Trash2 className="mr-2 h-4 w-4" />Discard edit</Button></div></div></details> : <div className="flex items-center justify-between border-y py-3"><div><h2 className="text-sm font-semibold">Partner details</h2><p className="mt-0.5 text-xs text-muted-foreground">{partner?.identities?.map((identity) => `${identity.scheme} ${identity.value}`).join(" · ") || "No identifiers"}</p></div>{partner?.status === "ACTIVE" ? <Button variant="ghost" size="sm" onClick={() => void revisePartner()} disabled={busy}><Edit3 className="mr-2 h-4 w-4" />Edit</Button> : null}</div>}
      </section>

      <section className="mt-8 border-t" aria-labelledby="connection-title">
        <header className="flex flex-wrap items-center gap-3 border-b py-3"><Plug className="h-4 w-4 text-primary" aria-hidden="true" /><div><h2 id="connection-title" className="text-base font-semibold">Connection</h2><p className="mt-0.5 text-sm text-muted-foreground">How files are securely sent to and received from this partner.</p></div><Badge variant="secondary">{stateLabel(connection?.status ?? partner?.status)}</Badge><div className="ml-auto flex flex-wrap gap-2">{publicConnectionDetails ? <Button size="sm" variant="outline" onClick={() => void copyConnectionDetails()}><Copy className="mr-2 h-4 w-4" />Share AS2 details</Button> : null}{connection ? <Button size="sm" variant="ghost" onClick={() => void changeConnectionStatus()} disabled={busy}><Pause className="mr-2 h-4 w-4" />{connection.status === "ACTIVE" ? "Pause" : "Turn on"}</Button> : null}{connection?.status === "ACTIVE" && !connectionDraft ? <Button asChild size="sm" variant="ghost"><Link to={`/connections/${connection.id}/edit`}>Edit</Link></Button> : null}<Button size="sm" variant="outline" onClick={() => connection ? setSendingFile((value) => !value) : setAddingConnection((value) => !value)}>{connection ? <Send className="mr-2 h-4 w-4" /> : <Plus className="mr-2 h-4 w-4" />}{connection ? "Send file" : "Add connection"}</Button></div></header>
        {connection ? <><dl className="grid gap-x-8 border-b sm:grid-cols-2"><Detail label="Endpoint" value={connection.endpoint} /><Detail label="Receipts" value={connection.mdnMode === "ASYNCHRONOUS" ? "Separate" : "Immediate"} /><Detail label="Our AS2 ID" value={connection.localAs2Id} mono /><Detail label="Partner AS2 ID" value={connection.remoteAs2Id} mono /><Detail label="Our certificate" value={data.certificates.find((item) => item.id === connection.localCertificateId)?.name} /><Detail label="Partner certificate" value={data.certificates.find((item) => item.id === connection.partnerEncryptionCertificateId)?.name} /></dl><div className="flex justify-end py-2"><Button asChild variant="ghost" size="sm"><Link to="/connections/certificates">Manage certificates</Link></Button></div></> : <AdminEmptyState title="No file connection" />}
        {publicConnectionDetails ? <details className="border-t"><summary className="cursor-pointer py-3 text-sm font-medium">Details to share with this partner</summary><div className="grid gap-4 border-t bg-muted/20 py-4 sm:grid-cols-2"><Detail label="Our AS2 ID" value={publicConnectionDetails.as2Identifier} mono /><Detail label="Our receiving URL" value={publicConnectionDetails.inboundUrl} mono /><Detail label="Our receipt URL" value={publicConnectionDetails.asynchronousMdnUrl} mono /><div className="flex flex-wrap items-center gap-2 sm:col-span-2"><Button size="sm" variant="outline" onClick={() => void copyConnectionDetails()}><Copy className="mr-2 h-4 w-4" />Copy details</Button><Button size="sm" variant="outline" onClick={() => downloadCertificate(publicConnectionDetails.publicCertificatePem, "edispark-signing.pem")} disabled={!publicConnectionDetails.publicCertificatePem}><Download className="mr-2 h-4 w-4" />Signing certificate</Button><Button size="sm" variant="outline" onClick={() => downloadCertificate(publicConnectionDetails.encryptionPublicCertificatePem, "edispark-encryption.pem")} disabled={!publicConnectionDetails.encryptionPublicCertificatePem}><Download className="mr-2 h-4 w-4" />Encryption certificate</Button></div></div></details> : null}
        {connectionDraft ? <details className="border-t"><summary className="cursor-pointer py-3 text-sm font-medium">Unpublished connection changes · revision {connectionDraft.revision ?? 1}</summary><form onSubmit={saveConnectionDraft} className="flex flex-col gap-3 border-t bg-muted/20 p-4 sm:flex-row sm:items-end"><label className="grid flex-1 gap-1.5 text-sm font-medium">Endpoint<Input name="endpoint" type="url" defaultValue={connectionDraft.endpoint} required /></label><Button variant="outline" disabled={busy}>Save</Button><Button type="button" onClick={() => void activateConnectionDraft()} disabled={busy}>Apply</Button></form></details> : null}
        {addingConnection ? <form onSubmit={createConnection} className="grid gap-4 border-t bg-muted/20 p-4 md:grid-cols-2"><label className="grid gap-1.5 text-sm font-medium">Partner endpoint<Input name="endpoint" type="url" required autoFocus /></label><label className="grid gap-1.5 text-sm font-medium">Receipt<select name="mdnMode" className="h-10 rounded-md border bg-background px-3 text-sm"><option value="SYNCHRONOUS">Immediate</option><option value="ASYNCHRONOUS">Separate</option></select></label><label className="grid gap-1.5 text-sm font-medium">Our AS2 ID<Input name="localAs2Id" required /></label><label className="grid gap-1.5 text-sm font-medium">Partner AS2 ID<Input name="remoteAs2Id" required /></label><label className="grid gap-1.5 text-sm font-medium">Our certificate<select name="localCertificateId" required className="h-10 rounded-md border bg-background px-3 text-sm"><option value="">Choose…</option>{data.certificates.filter((item) => item.kind === "LOCAL_IDENTITY").map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label className="grid gap-1.5 text-sm font-medium">Partner certificate<select name="partnerCertificateId" required className="h-10 rounded-md border bg-background px-3 text-sm"><option value="">Choose…</option>{data.certificates.filter((item) => item.kind === "PARTNER_PUBLIC").map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><div className="flex justify-end gap-2 md:col-span-2"><Button type="button" variant="ghost" onClick={() => setAddingConnection(false)}>Cancel</Button><Button disabled={busy}>Add</Button></div></form> : null}
        {sendingFile ? <form onSubmit={sendFile} className="flex flex-col gap-3 border-t bg-muted/20 p-4 sm:flex-row sm:items-end"><label className="grid flex-1 gap-1.5 text-sm font-medium">File<Input name="file" type="file" required accept=".edi,.dat,.txt,.xml,application/edifact" /></label><Button type="button" variant="ghost" onClick={() => setSendingFile(false)}>Cancel</Button><Button disabled={busy}>Send</Button></form> : null}
      </section>
        </TabsContent>

        <TabsContent value="documents" className="mt-4 lg:mt-0">
      <section className="border-t" aria-labelledby="documents-title">
        <header className="flex flex-wrap items-center gap-3 border-b py-3"><FileText className="h-4 w-4 text-primary" aria-hidden="true" /><div><h2 id="documents-title" className="text-base font-semibold">Documents</h2><p className="mt-0.5 text-sm text-muted-foreground">Each route says who sends the document, its EDI format, and the exact document rule used to translate its fields.</p></div>{agreement ? <Badge variant="secondary">{agreement.status === "ACTIVE" ? "On" : stateLabel(agreement.status)}</Badge> : null}<div className="ml-auto flex gap-2">{agreement?.status === "DRAFT" ? <Button size="sm" variant="ghost" onClick={() => void discardDocumentEdit()} disabled={busy}><Trash2 className="mr-2 h-4 w-4" />Discard</Button> : null}{agreement?.status === "DRAFT" || agreement?.status === "QUALIFYING" ? <><Button size="sm" variant="ghost" onClick={() => void changeDocumentStatus("qualify")} disabled={busy || !capabilities.length}>Check</Button>{agreement.status === "QUALIFYING" && agreement.qualification?.passed ? <Button size="sm" onClick={() => void changeDocumentStatus("activate")} disabled={busy}>Turn on</Button> : null}</> : null}{agreement?.status === "ACTIVE" ? <Button size="sm" variant="ghost" onClick={() => void changeDocumentStatus("suspend")} disabled={busy}>Pause</Button> : null}<Button size="sm" variant="outline" onClick={() => void editDocuments()} disabled={busy}>{addingDocument ? <X className="mr-2 h-4 w-4" /> : <Plus className="mr-2 h-4 w-4" />}{agreement?.status === "ACTIVE" ? "Edit" : "Add"}</Button></div></header>
        {capabilities.length ? <div className="flex items-center gap-1 border-b py-2" aria-label="Filter document routes by direction">{([['ALL', 'All'], ['OUTBOUND', 'Send'], ['INBOUND', 'Receive']] as const).map(([value, label]) => <button key={value} type="button" aria-pressed={documentDirection === value} onClick={() => setDocumentDirection(value)} className={cn("min-h-9 rounded-md px-3 text-sm font-medium text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring", documentDirection === value && "bg-accent text-primary")}>{label}</button>)}</div> : null}
        {visibleCapabilities.length ? <div>{(['OUTBOUND', 'INBOUND'] as const).map((direction) => {
          const routes = visibleCapabilities.filter((item) => item.direction === direction)
          if (!routes.length) return null
          const outbound = direction === 'OUTBOUND'
          return <div key={direction} className="border-b last:border-b-0">
            <div className="flex items-center gap-2 bg-muted/30 px-3 py-2 text-sm font-medium"><span>{outbound ? 'Your organisation' : partner?.displayName}</span><ArrowRight className="h-4 w-4 text-primary" aria-hidden="true" /><span>{outbound ? partner?.displayName : 'Your organisation'}</span><span className="ml-auto text-xs font-normal text-muted-foreground">{routes.length} {routes.length === 1 ? 'route' : 'routes'}</span></div>
            <div className="divide-y">{routes.map((capability, index) => {
              const rule = data.mappings.find((mapping) => mapping.id === capability.mappingRevisionId)
              return <div key={capability.id ?? index} className="grid min-h-16 items-center gap-3 px-3 py-3 text-sm sm:grid-cols-[8rem_10rem_minmax(15rem,1fr)_auto]">
                <span className="font-medium capitalize">{capability.documentType?.toLowerCase()}</span>
                <span className="text-muted-foreground">{capability.standard} {capability.release}</span>
                {rule?.id ? <Button asChild variant="ghost" className="h-auto justify-start px-0 py-1 text-left"><Link to={`/organisation/document-rules/${rule.id}`}><span className="min-w-0"><span className="block truncate font-medium">{rule.name}</span><span className="block text-xs font-normal text-muted-foreground">Document rule · revision {rule.revision ?? 1}</span></span></Link></Button> : <span className="text-xs text-muted-foreground">No document rule</span>}
                {agreement?.status === "DRAFT" || agreement?.status === "QUALIFYING" ? <Button variant="ghost" size="icon" onClick={() => void removeDocument(capability.id)} disabled={busy} aria-label="Remove document route"><Trash2 className="h-4 w-4" /></Button> : null}
              </div>
            })}</div>
          </div>
        })}</div> : capabilities.length ? <AdminEmptyState title="No routes in this direction" /> : <AdminEmptyState title="No document routes" />}
        {addingDocument && agreement && agreement.status !== "ACTIVE" ? <form onSubmit={addDocument} className="grid gap-3 border-t bg-muted/20 p-4 sm:grid-cols-[1fr_1fr_1fr_auto]"><label className="grid gap-1.5 text-sm font-medium">Direction<select name="direction" className="h-10 rounded-md border bg-background px-3 text-sm"><option value="INBOUND">Receive</option><option value="OUTBOUND">Send</option></select></label><label className="grid gap-1.5 text-sm font-medium">Document<select name="documentType" className="h-10 rounded-md border bg-background px-3 text-sm"><option value="ORDER">Order</option><option value="INVOICE">Invoice</option></select></label><label className="grid gap-1.5 text-sm font-medium">Format<select name="standard" className="h-10 rounded-md border bg-background px-3 text-sm"><option value="EDIFACT">EDIFACT D96A</option><option value="TRADACOMS">TRADACOMS 9</option></select></label><Button className="self-end" disabled={busy || !connection}>Add</Button></form> : null}
      </section>
        </TabsContent>

        <TabsContent value="files" className="mt-4 lg:mt-0">
      <section className="border-t" aria-labelledby="activity-title">
        <header className="flex items-center justify-between gap-3 border-b py-3"><h2 id="activity-title" className="text-base font-semibold">Files and documents</h2><Button asChild variant="ghost" size="sm"><Link to={connection?.id ? `/files?connection=${connection.id}` : "/files"}>View all files</Link></Button></header>
        {activity.length ? <div className="divide-y">{activity.map((item) => <div key={`${item.id}-${item.name}`} className="flex min-h-14 items-center gap-4 py-3 text-sm"><span className="min-w-0 flex-1 truncate font-medium">{item.name}</span><Badge variant="secondary">{stateLabel(item.status)}</Badge><time className="hidden text-xs text-muted-foreground sm:block">{formatDate(item.updatedAt)}</time></div>)}</div> : <AdminEmptyState title="No activity" />}
      </section>
        </TabsContent>
        </div>
      </Tabs>
    </AdminPage>
  )
}

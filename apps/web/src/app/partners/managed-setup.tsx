"use client"

import type { ManagedAgreement, Published, Request as SetupRequest, ManagedSetupResult } from "@edispark/integration-client"
import type { CertificateResponse } from "@edispark/mft-client"
import { useEffect, useState } from "react"
import { Link, useNavigate } from "react-router-dom"

import { AdminNotice } from "@/components/admin/admin-page"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { adminApi } from "@/lib/admin-api"
import { verificationLabel } from "@/app/platform/managed-partners"

const endpoint = "/api/integration/managed-partners"
const selectClass = "h-10 rounded-md border bg-background px-3 text-sm"
const headers = { "Content-Type": "application/json" }

export function ManagedPartnerSetup() {
  const navigate = useNavigate()
  const [templates, setTemplates] = useState<Published[]>()
  const [certificates, setCertificates] = useState<CertificateResponse[]>([])
  const [selected, setSelected] = useState("")
  const [submission, setSubmission] = useState<{ key: string; requestId: string }>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const template = templates?.find((item) => item.id === selected)

  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal }
    void Promise.all([
      adminApi<Published[]>(endpoint, options),
      adminApi<CertificateResponse[]>("/api/mft/certificates", options),
    ]).then(([entries, available]) => {
      setTemplates(entries)
      setCertificates(available)
    }).catch((cause: Error) => {
      if (!controller.signal.aborted) setError(cause.message)
    })
    return () => controller.abort()
  }, [])

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!template?.id || !template.revision) return
    const form = new FormData(event.currentTarget)
    const value = (key: string) => String(form.get(key) ?? "").trim()
    const body: Omit<SetupRequest, "requestId"> = {
      businessIdentity: { scheme: value("scheme"), value: value("businessIdentity") },
      localAs2Id: value("localAs2Id"),
      remoteAs2Id: value("remoteAs2Id"),
      endpoint: value("endpoint"),
      asynchronousMdnEndpoint: value("asynchronousMdnEndpoint") || undefined,
      localCertificateId: value("localCertificateId"),
      partnerSigningCertificateId: value("partnerSigningCertificateId"),
      partnerEncryptionCertificateId: value("partnerEncryptionCertificateId"),
    }
    const key = JSON.stringify([template.id, template.revision, body])
    const requestId = submission?.key === key ? submission.requestId : crypto.randomUUID()
    setSubmission({ key, requestId })
    setBusy(true)
    setError(undefined)
    try {
      const result = await adminApi<ManagedSetupResult>(endpoint + "/" + template.id + "/revisions/" + template.revision + "/apply", {
        method: "POST", headers, body: JSON.stringify({ ...body, requestId }),
      })
      navigate(result.partnerId ? "/partners/" + result.partnerId : "/partners")
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The managed partner could not be set up.")
    } finally {
      setBusy(false)
    }
  }

  const definition = template?.definition
  const local = certificates.filter((item) => item.kind === "LOCAL_IDENTITY" && item.hasPrivateKey)
  const publicCertificates = certificates.filter((item) => item.kind === "PARTNER_PUBLIC")
  return <section aria-labelledby="managed-partner-title" className="mb-8 space-y-4 rounded-lg border p-4 sm:p-5">
    <h2 id="managed-partner-title" className="font-semibold">Use a managed template</h2>
    {error && <AdminNotice>{error}</AdminNotice>}
    {templates?.length === 0 && <p className="text-sm text-muted-foreground">No published templates are available.</p>}
    <label className="grid gap-1.5 text-sm font-medium">Partner template
      <select className={selectClass} value={selected} disabled={busy || !templates?.length} onChange={(event) => setSelected(event.target.value)}>
        <option value="">{templates ? "Select a partner template" : "Loading templates…"}</option>
        {templates?.map((item) => <option key={item.id} value={item.id}>{item.definition?.name}</option>)}
      </select>
    </label>
    {definition && <form key={template.id} onSubmit={submit} className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="outline">{verificationLabel(definition.verification)}</Badge>
        <Badge variant="secondary">Revision {template.revision}</Badge>
        <span className="text-sm text-muted-foreground">{definition.capabilities.map((item) => (item.direction === "INBOUND" ? "Receive " : "Send ") + item.messageType).join(" · ")}</span>
      </div>
      <p className="text-sm text-muted-foreground">Setup creates partner, AS2 connection and agreement drafts for this workspace.</p>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="grid gap-1.5 text-sm font-medium">Business identifier type
          <select className={selectClass} name="scheme"><option>GLN</option><option>DUNS</option><option>EDI</option></select>
        </label>
        <label className="grid gap-1.5 text-sm font-medium">Partner business identifier<Input name="businessIdentity" required maxLength={128} /></label>
        <label className="grid gap-1.5 text-sm font-medium">Organisation AS2 ID<Input name="localAs2Id" required maxLength={128} /></label>
        <label className="grid gap-1.5 text-sm font-medium">Partner AS2 ID<Input name="remoteAs2Id" required maxLength={128} /></label>
        <label className="grid gap-1.5 text-sm font-medium sm:col-span-2">Partner AS2 endpoint<Input name="endpoint" required type="url" /></label>
        {definition.transport.mdnMode === "ASYNCHRONOUS" &&
          <label className="grid gap-1.5 text-sm font-medium sm:col-span-2">Partner asynchronous MDN endpoint<Input name="asynchronousMdnEndpoint" required type="url" /></label>}
        <CertificateSelect name="localCertificateId" label="Organisation AS2 certificate" certificates={local} />
        <CertificateSelect name="partnerSigningCertificateId" label="Partner signing certificate" certificates={publicCertificates} />
        <CertificateSelect name="partnerEncryptionCertificateId" label="Partner encryption certificate" certificates={publicCertificates} />
      </div>
      {(!local.length || !publicCertificates.length) && <p className="text-sm"><Link className="underline" to="/connections/certificates">Add or review AS2 certificates</Link></p>}
      <div className="flex justify-end"><Button disabled={busy || !local.length || !publicCertificates.length}>{busy ? "Creating drafts…" : "Create drafts"}</Button></div>
    </form>}
  </section>
}

function CertificateSelect({ name, label, certificates }: { name: string; label: string; certificates: CertificateResponse[] }) {
  return <label className="grid gap-1.5 text-sm font-medium">{label}
    <select name={name} className={selectClass} required defaultValue="">
      <option value="">Select a certificate</option>
      {certificates.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
    </select>
  </label>
}

export function ManagedRetailerAcceptance({ agreementId, editable }: { agreementId: string; editable: boolean }) {
  const url = endpoint + "/agreements/" + agreementId
  const [details, setDetails] = useState<ManagedAgreement>()
  const [error, setError] = useState<string>()
  const [pending, setPending] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    setDetails(undefined)
    setError(undefined)
    void adminApi<ManagedAgreement>(url, { signal: controller.signal }).then(setDetails).catch((cause: Error) => {
      if (!controller.signal.aborted) setError(cause.message)
    })
    return () => controller.abort()
  }, [url])

  async function record(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const reference = String(new FormData(event.currentTarget).get("reference") ?? "").trim()
    setPending(true)
    setError(undefined)
    try {
      await adminApi(url + "/acceptance", { method: "POST", headers, body: JSON.stringify({ reference }) })
      setDetails(await adminApi<ManagedAgreement>(url))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Retailer acceptance could not be recorded.")
    } finally {
      setPending(false)
    }
  }

  if (!details?.setup) return error ? <AdminNotice>{error}</AdminNotice> : null
  return <section aria-label="Managed agreement" className="space-y-3 border-b py-4">
    <p className="text-sm font-medium">Managed template revision {details.setup.templateRevision}</p>
    {error && <AdminNotice>{error}</AdminNotice>}
    {details.requiresRetailerAcceptance && (
      <p className="text-sm text-muted-foreground">{details.retailerAcceptanceCurrent
        ? "Retailer acceptance is recorded for this setup."
        : "Record retailer acceptance for the current agreement and AS2 connection before turning on document exchange."}</p>
    )}
    {editable && details.requiresRetailerAcceptance && <form onSubmit={record} className="flex flex-wrap items-end gap-3">
      <label className="grid flex-1 gap-1.5 text-sm font-medium">Retailer acceptance reference<Input name="reference" maxLength={300} required /></label>
      <Button variant="outline" disabled={pending}>Record acceptance</Button>
    </form>}
  </section>
}

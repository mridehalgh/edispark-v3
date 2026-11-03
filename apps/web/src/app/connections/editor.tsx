"use client"

import type { CertificateResponse, CreatePartnerRequest, PartnerResponse } from "@edispark/mft-client"
import { ArrowLeft, Link2 } from "lucide-react"
import { useEffect, useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"

import { AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { adminApi } from "@/lib/admin-api"

export function ConnectionEditorPage() {
  const { connectionId } = useParams<{ connectionId: string }>()
  const navigate = useNavigate()
  const [certificates, setCertificates] = useState<CertificateResponse[]>([])
  const [connection, setConnection] = useState<PartnerResponse>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    void Promise.all([adminApi<CertificateResponse[]>("/api/mft/certificates"), adminApi<PartnerResponse[]>("/api/mft/partners")])
      .then(([nextCertificates, connections]) => { setCertificates(nextCertificates); setConnection(connections.find((item) => item.id === connectionId)) })
      .catch((requestError) => setError(requestError instanceof Error ? requestError.message : "Connection setup could not be loaded."))
  }, [connectionId])

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(undefined)
    const form = new FormData(event.currentTarget)
    const body: CreatePartnerRequest = {
      name: String(form.get("name")).trim(), endpoint: String(form.get("endpoint")).trim(), localAs2Id: String(form.get("localAs2Id")).trim(), remoteAs2Id: String(form.get("remoteAs2Id")).trim(), localCertificateId: String(form.get("localCertificateId")), partnerSigningCertificateId: String(form.get("partnerSigningCertificateId")), partnerEncryptionCertificateId: String(form.get("partnerEncryptionCertificateId")), asynchronousMdnEndpoint: String(form.get("asynchronousMdnEndpoint") || "").trim() || undefined, mdnMode: String(form.get("mdnMode")) as "SYNCHRONOUS" | "ASYNCHRONOUS", signingRequired: true, encryptionRequired: true, compressionEnabled: form.get("compressionEnabled") === "on", legacyCryptoEnabled: false,
    }
    try {
      let targetId = connectionId
      if (!connectionId) {
        const created = await adminApi<PartnerResponse>("/api/mft/partners", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) })
        targetId = created.id
      } else {
        let draft = connection
        if (connection?.status !== "DRAFT") draft = await adminApi<PartnerResponse>(`/api/mft/partners/${connectionId}/revisions`, { method: "POST" })
        if (!draft?.id) throw new Error("The connection draft could not be created.")
        targetId = draft.id
        await adminApi(`/api/mft/partners/${draft.id}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) })
      }
      navigate(targetId ? `/connections/${targetId}` : "/connections")
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "The connection could not be saved.")
    } finally { setBusy(false) }
  }

  const identityCertificates = certificates.filter((item) => item.kind === "LOCAL_IDENTITY")
  const partnerCertificates = certificates.filter((item) => item.kind === "PARTNER_PUBLIC")
  const editing = Boolean(connectionId)

  return (
    <AdminPage className="max-w-4xl">
      <Button asChild variant="ghost" className="-ml-3 mb-3"><Link to={connectionId ? `/connections/${connectionId}` : "/connections"}><ArrowLeft className="mr-2 h-4 w-4" />Connections</Link></Button>
      <AdminPageHeader title={editing ? "Edit connection" : "Add connection"} />
      {error ? <AdminNotice>{error}</AdminNotice> : null}
      <form onSubmit={submit} className="border-y">
        <section className="grid gap-5 px-1 py-6 sm:grid-cols-2 sm:px-4" aria-labelledby="identity-title"><h2 id="identity-title" className="sm:col-span-2 text-base font-semibold">Identity</h2><label className="grid gap-1.5 text-sm font-medium">Connection name<Input name="name" required defaultValue={connection?.name} autoFocus /></label><label className="grid gap-1.5 text-sm font-medium">Protocol<select disabled className="h-10 rounded-md border bg-muted px-3 text-sm"><option>AS2</option></select></label><label className="grid gap-1.5 text-sm font-medium">Our AS2 identifier<Input name="localAs2Id" required defaultValue={connection?.localAs2Id} /></label><label className="grid gap-1.5 text-sm font-medium">Remote AS2 identifier<Input name="remoteAs2Id" required defaultValue={connection?.remoteAs2Id} /></label></section>
        <section className="grid gap-5 border-t px-1 py-6 sm:grid-cols-2 sm:px-4" aria-labelledby="delivery-title"><h2 id="delivery-title" className="sm:col-span-2 text-base font-semibold">Delivery</h2><label className="grid gap-1.5 text-sm font-medium sm:col-span-2">Remote receiving URL<Input name="endpoint" type="url" required defaultValue={connection?.endpoint} /></label><label className="grid gap-1.5 text-sm font-medium">Receipt<select name="mdnMode" defaultValue={connection?.mdnMode ?? "SYNCHRONOUS"} className="h-10 rounded-md border bg-background px-3 text-sm"><option value="SYNCHRONOUS">Immediate</option><option value="ASYNCHRONOUS">Separate</option></select></label><label className="grid gap-1.5 text-sm font-medium">Separate receipt URL<Input name="asynchronousMdnEndpoint" type="url" defaultValue={connection?.asynchronousMdnEndpoint ?? ""} /></label></section>
        <section className="grid gap-5 border-t px-1 py-6 sm:grid-cols-2 sm:px-4" aria-labelledby="security-title"><div className="sm:col-span-2 flex items-center justify-between"><h2 id="security-title" className="text-base font-semibold">Security</h2><Button asChild variant="ghost" size="sm"><Link to="/connections/certificates">Manage certificates</Link></Button></div><label className="grid gap-1.5 text-sm font-medium">Our identity certificate<select name="localCertificateId" required defaultValue={connection?.localCertificateId ?? ""} className="h-10 rounded-md border bg-background px-3 text-sm"><option value="">Choose a certificate</option>{identityCertificates.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label className="grid gap-1.5 text-sm font-medium">Remote signing certificate<select name="partnerSigningCertificateId" required defaultValue={connection?.partnerSigningCertificateId ?? ""} className="h-10 rounded-md border bg-background px-3 text-sm"><option value="">Choose a certificate</option>{partnerCertificates.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label className="grid gap-1.5 text-sm font-medium">Remote encryption certificate<select name="partnerEncryptionCertificateId" required defaultValue={connection?.partnerEncryptionCertificateId ?? ""} className="h-10 rounded-md border bg-background px-3 text-sm"><option value="">Choose a certificate</option>{partnerCertificates.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label className="flex min-h-10 items-center gap-2 text-sm font-medium"><input name="compressionEnabled" type="checkbox" defaultChecked={connection?.compressionEnabled} />Compress outbound files</label></section>
        <div className="flex justify-end gap-2 border-t py-4"><Button asChild type="button" variant="ghost"><Link to={connectionId ? `/connections/${connectionId}` : "/connections"}>Cancel</Link></Button><Button disabled={busy || identityCertificates.length === 0 || partnerCertificates.length === 0}><Link2 className="mr-2 h-4 w-4" />{busy ? "Saving…" : editing ? "Save draft" : "Create connection"}</Button></div>
      </form>
    </AdminPage>
  )
}

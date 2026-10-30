"use client"

import type { CertificateResponse, PartnerResponse, TransferResponse } from "@edispark/mft-client"
import { useCallback, useEffect, useMemo, useState } from "react"
import { AlertTriangle, ArrowDownToLine, ArrowUpFromLine, CheckCircle2, Database, ExternalLink, FileUp, KeyRound, LockKeyhole, Network, RefreshCw, Route, ShieldCheck } from "lucide-react"

import { LayoutBody } from "@/components/layout/layout"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { cn } from "@/lib/utils"

type ViewState = {
  transfers: TransferResponse[]
  partners: PartnerResponse[]
  certificates: CertificateResponse[]
}

const EMPTY_STATE: ViewState = { transfers: [], partners: [], certificates: [] }
const LOCAL_CERTIFICATE_UPLOADS = process.env.NODE_ENV === "development"

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
  const tone = status === "DELIVERED"
    ? "bg-success/30 text-success-foreground"
    : status === "NEEDS_REVIEW" || status === "FAILED"
      ? "bg-destructive/10 text-destructive"
      : status === "RETRY_SCHEDULED"
        ? "bg-warning/25 text-warning-foreground"
        : "bg-primary/10 text-primary"
  return <Badge className={cn("border-0", tone)}>{status?.replaceAll("_", " ") ?? "UNKNOWN"}</Badge>
}

function Metric({ label, value, note, icon: Icon, warning = false }: { label: string; value: number; note: string; icon: typeof CheckCircle2; warning?: boolean }) {
  return (
    <article className="rounded-lg border bg-card p-4">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-muted-foreground">{label}</span>
        <Icon className={cn("h-4 w-4", warning ? "text-warning-foreground" : "text-primary")} aria-hidden="true" />
      </div>
      <p className="mt-3 text-3xl font-semibold tabular-nums tracking-tight">{value}</p>
      <p className="mt-1 text-xs text-muted-foreground">{note}</p>
    </article>
  )
}

function KeyStrategyPanel() {
  return (
    <section className="overflow-hidden rounded-lg border bg-card" aria-labelledby="key-strategy-title">
      <div className="border-b px-5 py-5 sm:px-6">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="secondary">Production target</Badge>
          <span className="text-xs text-muted-foreground">AWS KMS only · private keys remain non-exportable</span>
        </div>
        <h2 id="key-strategy-title" className="mt-3 text-xl font-semibold tracking-tight">Cost-conscious key hierarchy</h2>
        <p className="mt-1 max-w-3xl text-sm leading-6 text-muted-foreground">Tenant data keeps a tenant-specific deletion boundary. AS2 identities share a bounded cell key set so key count does not grow with every workspace.</p>
      </div>

      <div className="grid lg:grid-cols-3">
        <article className="border-b p-5 lg:border-b-0 lg:border-r sm:p-6">
          <Database className="h-5 w-5 text-primary" aria-hidden="true" />
          <p className="mt-4 text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Stored tenant data</p>
          <h3 className="mt-1 font-semibold">One symmetric key per tenant</h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">The same tenant key protects approved EdiSpark services, using an exact tenant/service/resource encryption context. It is not shared with other tenants.</p>
          <p className="mt-3 font-mono text-xs text-foreground">alias/edispark/tenant/&#123;tenant&#125;/data</p>
        </article>
        <article className="border-b p-5 lg:border-b-0 lg:border-r sm:p-6">
          <KeyRound className="h-5 w-5 text-primary" aria-hidden="true" />
          <p className="mt-4 text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">AS2 signing</p>
          <h3 className="mt-1 font-semibold">One signing key per cell</h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">Standard tenants in a deployment cell share a non-exportable RSA <code className="text-foreground">SIGN_VERIFY</code> key.</p>
          <p className="mt-3 font-mono text-xs text-foreground">alias/edispark/&#123;cell&#125;/as2-signing</p>
        </article>
        <article className="p-5 sm:p-6">
          <LockKeyhole className="h-5 w-5 text-primary" aria-hidden="true" />
          <p className="mt-4 text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">AS2 decryption</p>
          <h3 className="mt-1 font-semibold">One decryption key per cell</h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">A separate RSA <code className="text-foreground">ENCRYPT_DECRYPT</code> key unwraps inbound content keys.</p>
          <p className="mt-3 font-mono text-xs text-foreground">alias/edispark/&#123;cell&#125;/as2-decryption</p>
        </article>
      </div>

      <div className="border-y border-warning/40 bg-warning/10 px-5 py-4 sm:px-6" role="note" aria-label="KMS AS2 compatibility limitation">
        <div className="flex gap-3">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning-foreground" aria-hidden="true" />
          <div>
            <h3 className="font-semibold">RSA-OAEP is a partner activation requirement</h3>
            <p className="mt-1 text-sm leading-6 text-muted-foreground">KMS RSA decryption supports RSA-OAEP, not legacy RSA PKCS#1 v1.5. A KMS key also cannot both sign and decrypt, so partners must accept separate signing and encryption certificates. Incompatible profiles are not activated and there is no local-key fallback.</p>
          </div>
        </div>
      </div>

      <div className="grid gap-8 px-5 py-6 lg:grid-cols-[1fr_1.15fr] sm:px-6">
        <div>
          <div className="flex items-center gap-2"><Network className="h-4 w-4 text-primary" aria-hidden="true" /><h3 className="font-semibold">Tenant isolation</h3></div>
          <ul className="mt-3 space-y-3 text-sm leading-6 text-muted-foreground">
            <li><span className="font-medium text-foreground">Control plane:</span> tenant comes from the validated JWT claim.</li>
            <li><span className="font-medium text-foreground">AS2 data plane:</span> tenant comes from one atomically unique, exact AS2 identity pair.</li>
            <li><span className="font-medium text-foreground">Stored data:</span> tenant-specific symmetric key, alias-scoped STS session, and encryption context.</li>
            <li><span className="font-medium text-foreground">Shared AS2 keys:</span> bounded by cell, separate worker roles, per-tenant quotas, immutable certificate selection, and CloudTrail evidence.</li>
          </ul>
        </div>
        <div>
          <h3 className="font-semibold">What sharing changes</h3>
          <p className="mt-3 text-sm leading-6 text-muted-foreground">Asymmetric KMS calls do not support encryption context. The cell key set therefore provides cell-level key custody, not per-tenant cryptographic isolation. A disabled, throttled, or misconfigured key affects every tenant in that cell, so cells are deliberately bounded and never fall back to filesystem keys.</p>
          <a className="mt-4 inline-flex items-center gap-1.5 text-sm font-medium text-primary underline-offset-4 hover:underline" href="https://aws.amazon.com/blogs/architecture/simplify-multi-tenant-encryption-with-a-cost-conscious-aws-kms-key-strategy/" target="_blank" rel="noreferrer">AWS multi-tenant KMS strategy <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" /><span className="sr-only">opens in a new tab</span></a>
        </div>
      </div>
    </section>
  )
}

export function MftPage() {
  const [state, setState] = useState<ViewState>(EMPTY_STATE)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  const refresh = useCallback(async () => {
    setError(undefined)
    try {
      const [transfers, partners, certificates] = await Promise.all([
        readApi<TransferResponse[]>("/api/mft/transfers"),
        readApi<PartnerResponse[]>("/api/mft/partners"),
        readApi<CertificateResponse[]>("/api/mft/certificates"),
      ])
      setState({ transfers, partners, certificates })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "MFT data could not be loaded.")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const metrics = useMemo(() => ({
    total: state.transfers.length,
    delivered: state.transfers.filter(({ status }) => status === "DELIVERED").length,
    review: state.transfers.filter(({ status }) => status === "NEEDS_REVIEW" || status === "FAILED").length,
    pending: state.transfers.filter(({ status }) => status === "AWAITING_MDN" || status === "PROCESSING" || status === "QUEUED").length,
  }), [state.transfers])

  async function submitTransfer(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi("/api/mft/transfers", { method: "POST", body: new FormData(event.currentTarget), headers: { "Idempotency-Key": crypto.randomUUID() } })
      event.currentTarget.reset()
      setNotice("Transfer queued. Its AS2 evidence will appear below.")
      await refresh()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Transfer could not be queued.")
    } finally { setBusy(false) }
  }

  async function submitPartner(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi("/api/mft/partners", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: form.get("name"), localAs2Id: form.get("localAs2Id"), remoteAs2Id: form.get("remoteAs2Id"), endpoint: form.get("endpoint"),
          localCertificateId: form.get("localCertificateId"), partnerCertificateId: form.get("partnerCertificateId"), mdnMode: form.get("mdnMode"),
          signingRequired: true, encryptionRequired: true, compressionEnabled: true, legacyCryptoEnabled: false,
        }),
      })
      event.currentTarget.reset()
      setNotice("Trading partner profile created with secure defaults.")
      await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "Partner could not be created.") }
    finally { setBusy(false) }
  }

  async function submitCertificate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi("/api/mft/certificates", { method: "POST", body: new FormData(event.currentTarget) })
      event.currentTarget.reset()
      setNotice(LOCAL_CERTIFICATE_UPLOADS
        ? "Certificate validated and stored in the encrypted local simulator store."
        : "Partner public certificate validated and registered.")
      await refresh()
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "Certificate could not be stored.") }
    finally { setBusy(false) }
  }

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
      <header className="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-primary"><ShieldCheck className="h-4 w-4" />MFT control plane</div>
          <h1 className="text-2xl font-semibold tracking-tight">AS2 operations</h1>
          <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">Send EDI, inspect signed MDN evidence, and manage partner certificates in one tenant-isolated workspace.</p>
        </div>
        <Button variant="outline" className="gap-2 self-start" onClick={() => void refresh()} disabled={loading}><RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} />Refresh</Button>
      </header>

      {error && <div role="alert" className="mb-4 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</div>}
      {notice && <div role="status" className="mb-4 rounded-md bg-success/25 px-4 py-3 text-sm text-success-foreground">{notice}</div>}

      <section aria-label="Transfer health" className="mb-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Transfers" value={metrics.total} note="Retained in this workspace" icon={Route} />
        <Metric label="Delivered" value={metrics.delivered} note="Receipt validated" icon={CheckCircle2} />
        <Metric label="Needs review" value={metrics.review} note="Operator action required" icon={AlertTriangle} warning />
        <Metric label="In progress" value={metrics.pending} note="Queued, sending, or MDN pending" icon={RefreshCw} />
      </section>

      <Tabs defaultValue="transfers" className="space-y-4">
        <TabsList aria-label="MFT sections">
          <TabsTrigger value="transfers">Transfers</TabsTrigger>
          <TabsTrigger value="partners">Partners</TabsTrigger>
          <TabsTrigger value="certificates">Certificates</TabsTrigger>
          <TabsTrigger value="security">Security &amp; KMS</TabsTrigger>
        </TabsList>

        <TabsContent value="transfers" className="space-y-4">
          <form onSubmit={submitTransfer} className="grid gap-4 rounded-lg border bg-card p-4 lg:grid-cols-[minmax(14rem,1fr)_minmax(18rem,2fr)_auto] lg:items-end">
            <label className="grid gap-1.5 text-sm font-medium">Trading partner
              <select name="partnerId" required className="h-10 rounded-md border bg-background px-3 text-sm">
                <option value="">Choose a partner…</option>
                {state.partners.map((partner) => <option key={partner.id} value={partner.id}>{partner.name} · {partner.remoteAs2Id}</option>)}
              </select>
            </label>
            <label className="grid gap-1.5 text-sm font-medium">EDI payload
              <Input name="file" type="file" required accept=".edi,.x12,.dat,.txt,.xml,application/edi-x12,application/edifact" className="cursor-pointer file:mr-3 file:border-0 file:bg-transparent file:text-sm file:font-medium" />
            </label>
            <Button type="submit" disabled={busy || state.partners.length === 0} className="gap-2"><FileUp className="h-4 w-4" />Send via AS2</Button>
          </form>

          <section className="overflow-hidden rounded-lg border bg-card" aria-labelledby="transfer-log-title">
            <div className="flex items-center justify-between border-b px-4 py-3"><div><h2 id="transfer-log-title" className="text-sm font-semibold">Transfer log</h2><p className="text-xs text-muted-foreground">Every payload follows one evidence trail.</p></div><span className="text-xs tabular-nums text-muted-foreground">{state.transfers.length} records</span></div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[58rem] text-left text-sm">
                <thead className="border-b bg-muted/55 text-xs text-muted-foreground"><tr><th className="px-4 py-2.5 font-medium">File</th><th className="px-4 py-2.5 font-medium">Direction</th><th className="px-4 py-2.5 font-medium">Status</th><th className="px-4 py-2.5 font-medium">MDN evidence</th><th className="px-4 py-2.5 font-medium">Updated</th></tr></thead>
                <tbody className="divide-y">
                  {state.transfers.map((transfer) => <tr key={transfer.id} className="hover:bg-muted/40"><td className="px-4 py-3"><span className="block font-medium">{transfer.filename}</span><span className="font-mono text-xs text-muted-foreground">{transfer.sha256?.slice(0, 16)}…</span></td><td className="px-4 py-3"><span className="inline-flex items-center gap-1.5">{transfer.direction === "INBOUND" ? <ArrowDownToLine className="h-3.5 w-3.5" /> : <ArrowUpFromLine className="h-3.5 w-3.5" />}{transfer.direction}</span></td><td className="px-4 py-3"><StatusBadge status={transfer.status} /></td><td className="px-4 py-3 text-xs text-muted-foreground">{transfer.mdnDisposition ?? transfer.failureReason ?? "Pending"}</td><td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(transfer.updatedAt)}</td></tr>)}
                  {!loading && state.transfers.length === 0 && <tr><td colSpan={5} className="px-4 py-12 text-center text-sm text-muted-foreground">No transfers yet. Upload a payload to begin an evidence trail.</td></tr>}
                </tbody>
              </table>
            </div>
          </section>
        </TabsContent>

        <TabsContent value="partners" className="grid gap-4 xl:grid-cols-[24rem_1fr]">
          <form onSubmit={submitPartner} className="grid content-start gap-3 rounded-lg border bg-card p-4">
            <div><h2 className="font-semibold">Add trading partner</h2><p className="mt-1 text-xs text-muted-foreground">Signing, encryption, and compression are enabled by default.</p></div>
            <label className="grid gap-1 text-sm">Display name<Input name="name" required /></label>
            <div className="grid grid-cols-2 gap-3"><label className="grid gap-1 text-sm">Our AS2 ID<Input name="localAs2Id" required /></label><label className="grid gap-1 text-sm">Partner AS2 ID<Input name="remoteAs2Id" required /></label></div>
            <label className="grid gap-1 text-sm">AS2 endpoint<Input name="endpoint" type="url" placeholder="https://partner.example/as2" required /></label>
            <label className="grid gap-1 text-sm">Identity certificate<select name="localCertificateId" required className="h-10 rounded-md border bg-background px-3">{state.certificates.filter((item) => item.kind === "LOCAL_IDENTITY").map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
            <label className="grid gap-1 text-sm">Partner certificate<select name="partnerCertificateId" required className="h-10 rounded-md border bg-background px-3">{state.certificates.filter((item) => item.kind === "PARTNER_PUBLIC").map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
            <label className="grid gap-1 text-sm">MDN mode<select name="mdnMode" className="h-10 rounded-md border bg-background px-3"><option value="SYNCHRONOUS">Synchronous</option><option value="ASYNCHRONOUS">Asynchronous</option></select></label>
            <Button disabled={busy || state.certificates.length < 2}>Create partner</Button>
          </form>
          <section className="grid content-start gap-3 sm:grid-cols-2">{state.partners.map((partner) => <article key={partner.id} className="rounded-lg border bg-card p-4"><div className="flex items-start justify-between"><div><h3 className="font-semibold">{partner.name}</h3><p className="mt-0.5 font-mono text-xs text-muted-foreground">{partner.remoteAs2Id}</p></div><Badge variant="secondary">{partner.mdnMode === "ASYNCHRONOUS" ? "Async MDN" : "Sync MDN"}</Badge></div><dl className="mt-4 grid gap-2 text-xs"><div><dt className="text-muted-foreground">Endpoint</dt><dd className="mt-0.5 break-all font-mono">{partner.endpoint}</dd></div><div className="flex gap-2"><ShieldCheck className="h-4 w-4 text-success-foreground" /><dd>Signed · encrypted · {partner.compressionEnabled ? "compressed" : "uncompressed"}</dd></div></dl></article>)}{state.partners.length === 0 && <div className="rounded-lg border border-dashed p-10 text-center text-sm text-muted-foreground sm:col-span-2">Add certificates first, then configure your first trading partner.</div>}</section>
        </TabsContent>

        <TabsContent value="certificates" className="grid gap-4 xl:grid-cols-[24rem_1fr]">
          <form onSubmit={submitCertificate} className="grid content-start gap-3 rounded-lg border bg-card p-4">
            <div><h2 className="font-semibold">Register certificate</h2><p className="mt-1 text-xs text-muted-foreground">{LOCAL_CERTIFICATE_UPLOADS ? "Local simulation accepts disposable PKCS#12 identities. Production accepts public certificate material only." : "Production accepts public partner certificates only. AS2 private keys are created and used inside AWS KMS."}</p></div>
            <label className="grid gap-1 text-sm">Name<Input name="name" required /></label>
            <label className="grid gap-1 text-sm">Material type<select name="kind" className="h-10 rounded-md border bg-background px-3">{LOCAL_CERTIFICATE_UPLOADS && <option value="LOCAL_IDENTITY">Local simulator identity (PKCS#12)</option>}<option value="PARTNER_PUBLIC">Partner public certificate</option></select></label>
            <label className="grid gap-1 text-sm">Certificate file<Input name="file" type="file" required accept=".p12,.pfx,.pem,.cer,.crt" /></label>
            {LOCAL_CERTIFICATE_UPLOADS && <label className="grid gap-1 text-sm">PKCS#12 password <span className="text-xs text-muted-foreground">Local simulator only; leave blank for public certificates</span><Input name="password" type="password" autoComplete="new-password" /></label>}
            <Button disabled={busy} className="gap-2"><KeyRound className="h-4 w-4" />Validate and store</Button>
          </form>
          <section className="grid content-start gap-3 sm:grid-cols-2">{state.certificates.map((certificate) => <article key={certificate.id} className="rounded-lg border bg-card p-4"><div className="flex items-start justify-between gap-3"><div><h3 className="font-semibold">{certificate.name}</h3><p className="mt-0.5 text-xs text-muted-foreground">{certificate.kind === "LOCAL_IDENTITY" ? "Signing identity" : "Partner encryption certificate"}</p></div><Badge className={cn("border-0", (certificate.daysUntilExpiry ?? 0) < 30 ? "bg-warning/25 text-warning-foreground" : "bg-success/30 text-success-foreground")}>{certificate.validity}</Badge></div><dl className="mt-4 space-y-2 text-xs"><div><dt className="text-muted-foreground">Subject</dt><dd className="mt-0.5 truncate" title={certificate.subject}>{certificate.subject}</dd></div><div><dt className="text-muted-foreground">SHA-256 fingerprint</dt><dd className="mt-0.5 truncate font-mono" title={certificate.fingerprintSha256}>{certificate.fingerprintSha256}</dd></div><div className="flex justify-between"><dt className="text-muted-foreground">Expires</dt><dd>{formatDate(certificate.notAfter)}</dd></div></dl></article>)}{state.certificates.length === 0 && <div className="rounded-lg border border-dashed p-10 text-center text-sm text-muted-foreground sm:col-span-2">Upload a local identity and your partner’s public certificate to begin.</div>}</section>
        </TabsContent>

        <TabsContent value="security">
          <KeyStrategyPanel />
        </TabsContent>
      </Tabs>
    </LayoutBody>
  )
}

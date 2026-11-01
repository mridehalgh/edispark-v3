'use client'

import type { ExchangeResponse, MappingRevision, TradingAgreement, TradingPartner } from '@edispark/integration-client'
import type { PartnerResponse as MftPartner } from '@edispark/mft-client'
import { Activity, ArrowRight, CheckCircle2, CircleAlert, GitBranch, History, Link2, Network, Plus, RefreshCw, ShieldCheck, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { LayoutBody } from '@/components/layout/layout'
import { PartnerRevisionEditor } from '@/components/trading-network/partner-revision-editor'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'

type NetworkState = {
  partners: TradingPartner[]
  agreements: TradingAgreement[]
  mappings: MappingRevision[]
  exchanges: ExchangeResponse[]
  transportPartners: MftPartner[]
}

const EMPTY_STATE: NetworkState = { partners: [], agreements: [], mappings: [], exchanges: [], transportPartners: [] }

async function readApi<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: 'no-store' })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.error?.message ?? 'The request could not be completed.')
  return body as T
}

function formatDate(value?: string) {
  if (!value) return 'Not recorded'
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function StatusBadge({ status }: { status?: string }) {
  const active = status === 'ACTIVE' || status === 'PUBLISHED' || status === 'DELIVERED'
  const warning = status === 'QUALIFYING' || status === 'AWAITING_RECEIPT' || status === 'NEEDS_REVIEW'
  const failed = status === 'FAILED' || status === 'SUSPENDED'
  return <Badge className={cn('border-0 font-medium', active && 'bg-success/30 text-success-foreground', warning && 'bg-warning/25 text-warning-foreground', failed && 'bg-destructive/10 text-destructive', !active && !warning && !failed && 'bg-muted text-muted-foreground')}>{status?.replaceAll('_', ' ').toLowerCase() ?? 'unknown'}</Badge>
}

function EmptyState({ title, children }: { title: string; children: React.ReactNode }) {
  return <div className="px-6 py-12 text-center"><h3 className="text-sm font-semibold">{title}</h3><p className="mx-auto mt-1 max-w-lg text-sm leading-6 text-muted-foreground">{children}</p></div>
}

export function TradingNetworkPage() {
  const [state, setState] = useState<NetworkState>(EMPTY_STATE)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  const refresh = useCallback(async () => {
    setError(undefined)
    try {
      const [partners, agreements, mappings, exchanges, transportPartners] = await Promise.all([
        readApi<TradingPartner[]>('/api/integration/partners'),
        readApi<TradingAgreement[]>('/api/integration/agreements'),
        readApi<MappingRevision[]>('/api/integration/mappings'),
        readApi<ExchangeResponse[]>('/api/integration/exchanges'),
        readApi<MftPartner[]>('/api/mft/partners'),
      ])
      setState({ partners, agreements, mappings, exchanges, transportPartners })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Trading network data could not be loaded.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const partnerNames = useMemo(() => new Map(state.partners.map((item) => [item.id, item.displayName])), [state.partners])
  const metrics = useMemo(() => ({
    partners: state.partners.filter((item) => item.status === 'ACTIVE').length,
    agreements: state.agreements.filter((item) => item.status === 'ACTIVE').length,
    mappings: state.mappings.filter((item) => item.status === 'PUBLISHED').length,
    attention: state.exchanges.filter((item) => item.status === 'FAILED' || item.status === 'NEEDS_REVIEW').length,
  }), [state])

  async function perform(url: string, init?: RequestInit, success?: string) {
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi(url, init)
      if (success) setNotice(success)
      await refresh()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'The change could not be saved.')
    } finally {
      setBusy(false)
    }
  }

  async function createPartner(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    await perform('/api/integration/partners', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ displayName: form.get('displayName'), identities: [{ scheme: form.get('scheme'), value: form.get('value') }] }),
    }, 'Trading partner profile created.')
    event.currentTarget.reset()
  }

  async function createAgreement(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const partnerId = String(form.get('partnerId'))
    await perform(`/api/integration/partners/${partnerId}/agreements`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: form.get('name') }),
    }, 'Draft agreement created. Add a route, qualify it, then activate it.')
    event.currentTarget.reset()
  }

  async function savePartnerDraft(partner: TradingPartner, form: FormData) {
    await perform(`/api/integration/partners/${partner.id}`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ displayName: form.get('displayName'), identities: [{ scheme: form.get('scheme'), value: form.get('value') }] }),
    }, 'Partner draft saved.')
  }

  async function activatePartnerRevision(partner: TradingPartner, effectiveAt?: string) {
    await perform(`/api/integration/partners/${partner.id}/activate`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ effectiveAt: effectiveAt ? new Date(effectiveAt).toISOString() : undefined }),
    }, effectiveAt ? 'Partner revision scheduled.' : 'Partner revision activated.')
  }

  async function addCapability(event: React.FormEvent<HTMLFormElement>, agreementId: string) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const mapping = state.mappings.find((item) => item.id === form.get('mappingRevisionId'))
    const documentType = String(form.get('documentType')) as 'ORDER' | 'INVOICE'
    const standard = String(form.get('standard')) as 'TRADACOMS' | 'EDIFACT'
    if (!mapping?.sourceSchemaId || !mapping.targetSchemaId) return setError('Choose a published mapping.')
    await perform(`/api/integration/agreements/${agreementId}/capabilities`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        direction: 'OUTBOUND', documentType, standard,
        release: standard === 'EDIFACT' ? 'D96A' : '9',
        messageType: documentType === 'ORDER' ? 'ORDERS' : 'INVOIC',
        sourceSchemaId: mapping.sourceSchemaId, targetSchemaId: mapping.targetSchemaId,
        mappingRevisionId: mapping.id, as2ConnectionId: form.get('as2ConnectionId'),
        acknowledgementPolicy: 'TRANSPORT_ONLY', batchingPolicy: 'INDIVIDUAL',
      }),
    }, 'Outbound EDI route added to the agreement.')
  }

  return <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
    <header className="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
      <div><div className="flex items-center gap-2 text-primary"><Network className="h-4 w-4" aria-hidden="true" /><span className="text-xs font-semibold uppercase tracking-[0.16em]">Control plane</span></div><h1 className="mt-2 text-2xl font-semibold tracking-tight">Trading network</h1><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">Define who you trade with, qualify document routes, publish transformations, and trace every exchange.</p></div>
      <Button variant="outline" size="icon" onClick={() => void refresh()} disabled={loading} aria-label="Refresh trading network"><RefreshCw className={cn('h-4 w-4', loading && 'animate-spin')} /></Button>
    </header>

    {error && <div role="alert" className="mb-4 flex gap-3 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive"><CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />{error}</div>}
    {notice && <div role="status" className="mb-4 flex gap-3 rounded-md bg-success/25 px-4 py-3 text-sm text-success-foreground"><CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />{notice}</div>}

    <section aria-label="Trading network summary" className="mb-6 grid overflow-hidden rounded-lg border bg-card sm:grid-cols-4">
      {[['Active partners', metrics.partners], ['Active agreements', metrics.agreements], ['Published mappings', metrics.mappings], ['Needs attention', metrics.attention]].map(([label, value], index) => <div key={label} className="border-b px-4 py-4 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0"><p className="text-sm font-medium text-muted-foreground">{label}</p><p className={cn('mt-2 text-2xl font-semibold tabular-nums', index === 3 && Number(value) > 0 && 'text-destructive')}>{value}</p></div>)}
    </section>

    <Tabs defaultValue="partners" className="space-y-4">
      <TabsList className="h-auto w-full justify-start overflow-x-auto bg-muted/65 p-1"><TabsTrigger value="partners">Partners</TabsTrigger><TabsTrigger value="agreements">Agreements</TabsTrigger><TabsTrigger value="mappings">Mappings</TabsTrigger><TabsTrigger value="exchanges">Exchange evidence</TabsTrigger></TabsList>

      <TabsContent value="partners" className="space-y-4">
        {state.partners.filter((partner) => partner.status === 'DRAFT').map((partner) => <PartnerRevisionEditor key={`editor-${partner.id}`} partner={partner} busy={busy} onSave={(item, form) => void savePartnerDraft(item, form)} onActivate={(item, effectiveAt) => void activatePartnerRevision(item, effectiveAt)} />)}
        <section className="rounded-lg border bg-card"><div className="border-b px-4 py-3"><h2 className="text-sm font-semibold">Add business partner</h2><p className="mt-0.5 text-xs text-muted-foreground">Business identity stays separate from AS2 transport configuration.</p></div><form onSubmit={createPartner} className="grid gap-4 p-4 md:grid-cols-[1.4fr_0.8fr_1fr_auto]"><label className="grid gap-1.5 text-sm font-medium">Display name<Input name="displayName" required placeholder="Example Retailer" /></label><label className="grid gap-1.5 text-sm font-medium">Identity scheme<select name="scheme" className="h-10 rounded-md border bg-background px-3 text-sm"><option value="GLN">GLN</option><option value="DUNS">DUNS</option><option value="EDI">EDI identifier</option></select></label><label className="grid gap-1.5 text-sm font-medium">Identity value<Input name="value" required placeholder="[partner_identifier]" /></label><Button className="self-end gap-2" disabled={busy}><Plus className="h-4 w-4" />Add partner</Button></form></section>
        <section className="overflow-hidden rounded-lg border bg-card"><div className="border-b px-4 py-3"><h2 className="text-sm font-semibold">Business partners</h2></div><div className="divide-y">{state.partners.map((partner) => <article key={partner.id} className="grid gap-3 px-4 py-4 sm:grid-cols-[1fr_1fr_auto] sm:items-center"><div><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{partner.displayName}</h3><span className="text-xs text-muted-foreground">r{partner.revision ?? 1}</span></div><p className="mt-0.5 text-xs text-muted-foreground">{partner.identities?.map((item) => `${item.scheme}: ${item.value}`).join(' · ')}</p></div><p className="text-xs text-muted-foreground">Updated {formatDate(partner.updatedAt)}</p><div className="flex items-center justify-end gap-2"><StatusBadge status={partner.status} />{partner.status === 'ACTIVE' && <Button size="sm" variant="outline" disabled={busy} onClick={() => void perform(`/api/integration/partners/${partner.id}/revisions`, { method: 'POST' }, 'Editable partner revision created.')}><History className="mr-1.5 h-4 w-4" />Revise</Button>}{partner.status === 'DRAFT' && <Button size="sm" variant="ghost" disabled={busy} onClick={() => void perform(`/api/integration/partners/${partner.id}`, { method: 'DELETE' }, 'Unused partner draft deleted.')}><Trash2 className="mr-1.5 h-4 w-4" />Delete</Button>}</div></article>)}{!loading && state.partners.length === 0 && <EmptyState title="No business partners yet">Create a retailer, supplier, or logistics partner here. Their AS2 connection remains managed by the transport service.</EmptyState>}</div></section>
      </TabsContent>

      <TabsContent value="agreements" className="space-y-4">
        <section className="rounded-lg border bg-card"><div className="border-b px-4 py-3"><h2 className="text-sm font-semibold">Create agreement</h2><p className="mt-0.5 text-xs text-muted-foreground">Agreements bind a business partner to qualified document, mapping, and AS2 capabilities.</p></div><form onSubmit={createAgreement} className="grid gap-4 p-4 sm:grid-cols-[1fr_1.5fr_auto]"><label className="grid gap-1.5 text-sm font-medium">Partner<select name="partnerId" required className="h-10 rounded-md border bg-background px-3 text-sm"><option value="">Choose a partner…</option>{state.partners.map((item) => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label className="grid gap-1.5 text-sm font-medium">Agreement name<Input name="name" required placeholder="Retail orders and invoices" /></label><Button className="self-end gap-2" disabled={busy || state.partners.length === 0}><Plus className="h-4 w-4" />Create draft</Button></form></section>
        <div className="space-y-4">{state.agreements.map((agreement) => <article key={agreement.id} className="overflow-hidden rounded-lg border bg-card"><header className="flex flex-col justify-between gap-3 border-b px-4 py-4 sm:flex-row sm:items-center"><div><div className="flex flex-wrap items-center gap-2"><h2 className="font-semibold">{agreement.name}</h2><StatusBadge status={agreement.status} /><span className="text-xs text-muted-foreground">Revision {agreement.revision}</span></div><p className="mt-1 text-xs text-muted-foreground">{partnerNames.get(agreement.partnerId) ?? 'Unknown partner'} · {agreement.capabilities?.length ?? 0} routes</p></div><div className="flex flex-wrap gap-2">{(agreement.status === 'DRAFT' || agreement.status === 'QUALIFYING') && <Button size="sm" variant="outline" disabled={busy || !agreement.capabilities?.length} onClick={() => void perform(`/api/integration/agreements/${agreement.id}/qualify`, { method: 'POST' }, 'Qualification checks completed.')}>Qualify</Button>}{agreement.status === 'QUALIFYING' && agreement.qualification?.passed && <Button size="sm" disabled={busy} onClick={() => void perform(`/api/integration/agreements/${agreement.id}/activate`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }, 'Agreement activated. Matching documents can now be sent.')}>Activate</Button>}{agreement.status === 'ACTIVE' && <><Button size="sm" variant="outline" disabled={busy} onClick={() => void perform(`/api/integration/agreements/${agreement.id}/revisions`, { method: 'POST' }, 'Editable agreement revision created.')}><History className="mr-1.5 h-4 w-4" />Revise</Button><Button size="sm" variant="outline" disabled={busy} onClick={() => void perform(`/api/integration/agreements/${agreement.id}/suspend`, { method: 'POST' }, 'Agreement suspended.')}>Suspend</Button></>}{agreement.status === 'DRAFT' && <Button size="sm" variant="ghost" disabled={busy} onClick={() => void perform(`/api/integration/agreements/${agreement.id}`, { method: 'DELETE' }, 'Unused agreement draft deleted.')}><Trash2 className="mr-1.5 h-4 w-4" />Delete</Button>}</div></header>
          <div className="divide-y">{agreement.capabilities?.map((capability) => <div key={capability.id} className="flex flex-col justify-between gap-2 px-4 py-3 text-sm sm:flex-row sm:items-center"><div className="flex items-center gap-2"><ArrowRight className="h-4 w-4 text-primary" /><span className="font-medium">{capability.documentType}</span><span className="text-muted-foreground">to {capability.standard} {capability.release} {capability.messageType}</span></div><div className="flex items-center gap-2"><span className="font-mono text-xs text-muted-foreground">AS2 {capability.as2ConnectionId?.slice(0, 8)}…</span>{(agreement.status === 'DRAFT' || agreement.status === 'QUALIFYING') && capability.id && <Button size="sm" variant="ghost" disabled={busy} aria-label={`Remove ${capability.documentType?.toLowerCase() ?? 'agreement'} route`} onClick={() => void perform(`/api/integration/agreements/${agreement.id}/capabilities/${capability.id}`, { method: 'DELETE' }, 'Agreement route removed.')}><Trash2 className="h-4 w-4" /></Button>}</div></div>)}</div>
          {(agreement.status === 'DRAFT' || agreement.status === 'QUALIFYING') && <form onSubmit={(event) => void addCapability(event, agreement.id!)} className="grid gap-3 bg-muted/25 p-4 lg:grid-cols-[0.8fr_0.8fr_1.3fr_1.3fr_auto]"><label className="grid gap-1 text-xs font-medium">Document<select name="documentType" className="h-9 rounded-md border bg-background px-2 text-sm"><option value="ORDER">Order</option><option value="INVOICE">Invoice</option></select></label><label className="grid gap-1 text-xs font-medium">Standard<select name="standard" className="h-9 rounded-md border bg-background px-2 text-sm"><option value="EDIFACT">EDIFACT D96A</option><option value="TRADACOMS">TRADACOMS</option></select></label><label className="grid gap-1 text-xs font-medium">Published mapping<select name="mappingRevisionId" required className="h-9 rounded-md border bg-background px-2 text-sm"><option value="">Choose mapping…</option>{state.mappings.filter((item) => item.status === 'PUBLISHED').map((item) => <option key={item.id} value={item.id}>{item.name} · r{item.revision}</option>)}</select></label><label className="grid gap-1 text-xs font-medium">Active AS2 connection<select name="as2ConnectionId" required className="h-9 rounded-md border bg-background px-2 text-sm"><option value="">Choose connection…</option>{state.transportPartners.filter((item) => item.status === 'ACTIVE' || item.status === undefined).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><Button size="sm" className="self-end" disabled={busy}>Add route</Button></form>}
          {agreement.qualification && <div className={cn('flex gap-2 border-t px-4 py-3 text-xs', agreement.qualification.passed ? 'bg-success/15 text-success-foreground' : 'bg-destructive/5 text-destructive')}><ShieldCheck className="h-4 w-4 shrink-0" /><div><p className="font-medium">{agreement.qualification.passed ? 'Qualification passed' : 'Qualification failed'}</p>{agreement.qualification.failures?.map((failure) => <p key={failure} className="mt-1">{failure}</p>)}</div></div>}
        </article>)}{!loading && state.agreements.length === 0 && <section className="rounded-lg border bg-card"><EmptyState title="No agreements yet">Create a partner first, then define the exact document routes that partner accepts.</EmptyState></section>}</div>
      </TabsContent>

      <TabsContent value="mappings" className="space-y-4">
        <section className="overflow-hidden rounded-lg border bg-card"><div className="flex flex-col justify-between gap-3 border-b px-4 py-4 sm:flex-row sm:items-center"><div><h2 className="text-base font-semibold">Mappings have their own workspace</h2><p className="mt-1 max-w-2xl text-sm text-muted-foreground">Build transformations with source fields, mapping rules, live output, server testing, and controlled publication in one focused screen.</p></div><Button asChild><Link to="/mappings">Open mappings<ArrowRight className="ml-2 h-4 w-4" /></Link></Button></div><div className="divide-y">{state.mappings.slice(0, 4).map((mapping) => <Link key={mapping.id} to={`/mappings/${mapping.id}`} className="flex min-h-14 items-center justify-between gap-3 px-4 py-3 hover:bg-muted/35"><div className="min-w-0"><div className="flex items-center gap-2"><Link2 className="h-4 w-4 shrink-0 text-primary" /><span className="truncate text-sm font-medium">{mapping.name}</span></div><p className="mt-1 text-xs text-muted-foreground">Revision {mapping.revision} · {mapping.nodes?.length ?? 0} operations</p></div><StatusBadge status={mapping.status} /></Link>)}</div>
        </section>
      </TabsContent>

      <TabsContent value="exchanges"><section className="overflow-hidden rounded-lg border bg-card"><div className="flex items-center justify-between border-b px-4 py-3"><div><h2 className="text-sm font-semibold">Exchange evidence</h2><p className="mt-0.5 text-xs text-muted-foreground">Agreement revision, generated representation, transport transfer, and lifecycle stages remain correlated.</p></div><Activity className="h-4 w-4 text-primary" /></div><div className="overflow-x-auto"><table className="w-full min-w-[64rem] text-left text-sm"><thead className="border-b bg-muted/55 text-xs text-muted-foreground"><tr><th className="px-4 py-2.5 font-medium">Exchange</th><th className="px-4 py-2.5 font-medium">Partner</th><th className="px-4 py-2.5 font-medium">Agreement</th><th className="px-4 py-2.5 font-medium">Status</th><th className="px-4 py-2.5 font-medium">Transport</th><th className="px-4 py-2.5 font-medium">Updated</th></tr></thead><tbody className="divide-y">{state.exchanges.map((exchange) => <tr key={exchange.id} className="hover:bg-muted/30"><td className="px-4 py-3 font-mono text-xs">{exchange.id}</td><td className="px-4 py-3">{partnerNames.get(exchange.partnerId) ?? exchange.partnerId}</td><td className="px-4 py-3 text-xs">{exchange.agreementId?.slice(0, 8)}… · r{exchange.agreementRevision}</td><td className="px-4 py-3"><StatusBadge status={exchange.status} /></td><td className="px-4 py-3 font-mono text-xs text-muted-foreground">{exchange.mftTransferId ?? 'Not queued'}</td><td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(exchange.updatedAt)}</td></tr>)}{!loading && state.exchanges.length === 0 && <tr><td colSpan={6}><EmptyState title="No exchanges yet">Open an order or invoice and choose “Send to partner” once an agreement is active.</EmptyState></td></tr>}</tbody></table></div></section></TabsContent>
    </Tabs>
  </LayoutBody>
}

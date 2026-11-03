'use client'

import type { MappingNode, MappingRevision, TestResult } from '@edispark/integration-client'
import { ArrowLeft, Beaker, BookOpen, Braces, Check, ChevronRight, CircleAlert, Copy, FileJson2, Globe2, History, Plus, Save, Search, Send, Trash2, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { previewMapping } from '@/lib/mapping-preview'
import { cn } from '@/lib/utils'

const OPERATIONS: NonNullable<MappingNode['operation']>[] = ['COPY', 'CONSTANT', 'CONCAT', 'COALESCE', 'UPPERCASE', 'LOWERCASE']
const DEFAULT_INPUT = JSON.stringify({
  _D: 'urn:oasis:names:specification:ubl:schema:xsd:Order-2',
  _A: 'urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2',
  _B: 'urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2',
  Order: [{
    UBLVersionID: [{ _: '2.1' }], ID: [{ _: 'PO-2026-0042' }], SalesOrderID: [{ _: 'SO-2026-0042' }], IssueDate: [{ _: '2026-11-01' }], IssueTime: [{ _: '09:30:00Z' }], OrderTypeCode: [{ _: '220' }], DocumentCurrencyCode: [{ _: 'GBP' }], CustomerReference: [{ _: 'BUYER-REF-42' }],
    BuyerCustomerParty: [{ Party: [{ EndpointID: [{ _: '[partner_identifier]', schemeID: 'GLN' }], PartyIdentification: [{ ID: [{ _: 'BUYER-001' }] }], PartyName: [{ Name: [{ _: '[name]' }] }], PostalAddress: [{ StreetName: [{ _: '[address]' }], CityName: [{ _: '[address]' }], PostalZone: [{ _: '[address]' }], Country: [{ IdentificationCode: [{ _: 'GB' }] }] }] }] }],
    SellerSupplierParty: [{ Party: [{ EndpointID: [{ _: '[tenant_identifier]', schemeID: 'GLN' }], PartyIdentification: [{ ID: [{ _: 'SUPPLIER-001' }] }], PartyName: [{ Name: [{ _: '[name]' }] }] }] }],
    Delivery: [{ ID: [{ _: 'DELIVERY-42' }], LatestDeliveryDate: [{ _: '2026-11-07' }], DeliveryLocation: [{ ID: [{ _: 'STORE-042' }] }] }], PaymentTerms: [{ ID: [{ _: 'NET30' }], PaymentDueDate: [{ _: '2026-12-01' }] }],
    AnticipatedMonetaryTotal: [{ LineExtensionAmount: [{ _: 100, currencyID: 'GBP' }], TaxExclusiveAmount: [{ _: 95, currencyID: 'GBP' }], TaxInclusiveAmount: [{ _: 114, currencyID: 'GBP' }], PayableAmount: [{ _: 114, currencyID: 'GBP' }] }],
    OrderLine: [{ LineItem: [{ ID: [{ _: '1' }], Quantity: [{ _: 10, unitCode: 'EA' }], LineExtensionAmount: [{ _: 100, currencyID: 'GBP' }], Item: [{ Description: [{ _: 'Comprehensive sample retail item' }], Name: [{ _: 'Sample item' }], BuyersItemIdentification: [{ ID: [{ _: 'BUY-SKU-001' }] }], SellersItemIdentification: [{ ID: [{ _: 'SELL-SKU-001' }] }], StandardItemIdentification: [{ ID: [{ _: '05012345000017', schemeID: 'GTIN' }] }] }], Price: [{ PriceAmount: [{ _: 10, currencyID: 'GBP' }] }] }] }],
  }],
}, null, 2)

async function readApi<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: 'no-store' })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.error?.message ?? 'The request could not be completed.')
  return body as T
}

function decodeOutput(value?: string) {
  if (!value) return ''
  if (value.trimStart().startsWith('{') || value.trimStart().startsWith('[')) return value
  try { return decodeURIComponent(Array.from(atob(value)).map((character) => `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`).join('')) } catch { return value }
}

function leafPointers(value: unknown, base = ''): string[] {
  if (value === null || typeof value !== 'object') return base ? [base] : []
  return Object.entries(value).flatMap(([key, child]) => leafPointers(child, `${base}/${key.replaceAll('~', '~0').replaceAll('/', '~1')}`))
}

function JsonPane({ value, label }: { value: string; label: string }) {
  return <div className="h-full overflow-auto bg-[oklch(0.18_0.035_270)] p-4 text-[0.75rem] leading-6 text-[oklch(0.91_0.02_270)]" aria-label={label}><pre className="font-mono whitespace-pre-wrap break-words">{value}</pre></div>
}

export function MappingWorkspacePage() {
  const { mappingId } = useParams()
  const navigate = useNavigate()
  const [mapping, setMapping] = useState<MappingRevision>()
  const [nodes, setNodes] = useState<MappingNode[]>([])
  const [input, setInput] = useState(DEFAULT_INPUT)
  const [activeNodeId, setActiveNodeId] = useState<string>()
  const [activePane, setActivePane] = useState<'source' | 'mapping' | 'output'>('mapping')
  const [search, setSearch] = useState('')
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [testOpen, setTestOpen] = useState(false)
  const [sampleOpen, setSampleOpen] = useState(false)
  const [testResult, setTestResult] = useState<TestResult>()
  const [effectiveAt, setEffectiveAt] = useState('')
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  const load = useCallback(async () => {
    if (!mappingId) return
    try {
      const result = await readApi<MappingRevision>(`/api/integration/mappings/${mappingId}`)
      setMapping(result); setNodes(result.nodes ?? []); setActiveNodeId(result.nodes?.[0]?.id)
      if (result.sample?.sourceDocument) setInput(result.sample.sourceDocument)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'The document rule could not be loaded.')
    }
  }, [mappingId])
  useEffect(() => { void load() }, [load])

  const parsedInput = useMemo(() => { try { return JSON.parse(input) as unknown } catch { return undefined } }, [input])
  const preview = useMemo(() => parsedInput === undefined ? undefined : previewMapping(parsedInput, nodes), [nodes, parsedInput])
  const previewText = useMemo(() => preview === undefined ? 'Input is not valid JSON.' : JSON.stringify(preview, null, 2), [preview])
  const sourcePointers = useMemo(() => (parsedInput === undefined ? [] : leafPointers(parsedInput)).filter((pointer) => pointer.toLowerCase().includes(search.toLowerCase())), [parsedInput, search])
  const editable = mapping?.status === 'DRAFT'

  function updateNode(id: string | undefined, patch: Partial<MappingNode>) {
    if (!id || !editable) return
    setNodes((current) => current.map((node) => node.id === id ? { ...node, ...patch } : node)); setDirty(true); setTestResult(undefined)
  }
  function addNode() {
    const node: MappingNode = { id: crypto.randomUUID(), operation: 'COPY', sourcePointers: ['/document/id'], targetPointer: '/document/id' }
    setNodes((current) => [...current, node]); setActiveNodeId(node.id); setDirty(true)
  }
  function removeNode(id?: string) {
    setNodes((current) => current.filter((node) => node.id !== id)); setDirty(true)
  }

  async function save() {
    if (!mappingId) return
    try {
      setBusy(true); setError(undefined)
      const saved = await readApi<MappingRevision>(`/api/integration/mappings/${mappingId}/draft`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ nodes }) })
      setMapping(saved); setDirty(false); setNotice('Draft saved.')
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : 'The draft could not be saved.') } finally { setBusy(false) }
  }

  async function test() {
    if (!mappingId || preview === undefined) return
    try {
      setBusy(true); setError(undefined)
      const result = await readApi<TestResult>(`/api/integration/mappings/${mappingId}/test`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: 'Workspace preview', input, expected: previewText }) })
      setTestResult({ ...result, output: decodeOutput(result.output) }); setNotice(result.passed ? 'Test passed. The output matches the preview.' : undefined)
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : 'The document rule test could not run.') } finally { setBusy(false) }
  }

  async function publish() {
    if (!mappingId || !testResult?.passed) return
    try {
      setBusy(true); setError(undefined)
      const published = await readApi<MappingRevision>(`/api/integration/mappings/${mappingId}/publish`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ fixtures: [{ name: 'Workspace qualification', input, expected: previewText }], effectiveAt: effectiveAt ? new Date(effectiveAt).toISOString() : undefined }) })
      setMapping(published); setNotice(effectiveAt ? 'Rule publication scheduled.' : 'Rule published. This revision is now immutable.'); setTestOpen(false)
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : 'The document rule could not be published.') } finally { setBusy(false) }
  }

  async function revise() {
    if (!mappingId) return
    try {
      setBusy(true)
      const revision = await readApi<MappingRevision>(`/api/integration/mappings/${mappingId}/revisions`, { method: 'POST' })
      navigate(`/organisation/document-rules/${revision.id}`)
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : 'A new revision could not be created.') } finally { setBusy(false) }
  }

  if (!mapping && !error) return <div className="grid min-h-[calc(100vh-4rem)] place-items-center"><p className="text-sm text-muted-foreground">Loading document rule…</p></div>

  return <div className="flex min-h-[calc(100vh-4rem)] flex-col bg-muted/20">
    <header className="sticky top-16 z-10 border-b bg-background">
      <div className="flex min-h-16 flex-wrap items-center gap-3 px-4 py-3 sm:px-6">
        <Button asChild variant="ghost" size="icon"><Link to="/organisation/document-rules" aria-label="Back to document rules"><ArrowLeft className="h-4 w-4" /></Link></Button>
        <div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h1 className="truncate text-lg font-semibold">{mapping?.name ?? 'Document rule unavailable'}</h1><Badge variant="secondary">r{mapping?.revision ?? 1}</Badge><Badge className={cn('border-0', mapping?.status === 'PUBLISHED' ? 'bg-success/30 text-success-foreground' : 'bg-muted text-foreground')}>{mapping?.status?.toLowerCase()}</Badge>{mapping?.scope === 'GLOBAL' && <Badge variant="outline" className="gap-1 border-primary/30 text-primary"><Globe2 className="h-3 w-3" />{mapping.vendor}</Badge>}</div></div>
        <div className="flex flex-wrap items-center justify-end gap-2">{mapping?.sample && <Button variant="outline" size="sm" onClick={() => { setSampleOpen((value) => !value); setTestOpen(false) }} className="gap-2"><BookOpen className="h-4 w-4" />Samples</Button>}<Button variant="outline" size="sm" onClick={() => { setTestOpen((value) => !value); setSampleOpen(false) }} className="gap-2"><Beaker className="h-4 w-4" />Test</Button>{editable ? <><span className="hidden text-xs text-muted-foreground sm:inline" role="status">{dirty ? 'Unsaved changes' : 'Draft saved'}</span><Button size="sm" onClick={() => void save()} disabled={busy || !dirty || nodes.length === 0} className="gap-2"><Save className="h-4 w-4" />Save draft</Button></> : <Button size="sm" onClick={() => void revise()} disabled={busy} className="gap-2"><History className="h-4 w-4" />{mapping?.scope === 'GLOBAL' ? 'Use as starting point' : 'Create new revision'}</Button>}</div>
      </div>
      <nav className="flex border-t px-4 xl:hidden" aria-label="Document rule panels">{(['source', 'mapping', 'output'] as const).map((pane) => <button key={pane} onClick={() => setActivePane(pane)} className={cn('min-h-11 border-b-2 px-4 text-sm font-medium capitalize', activePane === pane ? 'border-primary text-primary' : 'border-transparent text-muted-foreground')}>{pane === 'mapping' ? 'Field rules' : pane}</button>)}</nav>
    </header>

    {(error || notice) && <div className={cn('flex items-center gap-2 border-b px-5 py-2 text-sm', error ? 'bg-destructive/10 text-destructive' : 'bg-success/20 text-success-foreground')} role={error ? 'alert' : 'status'}>{error ? <CircleAlert className="h-4 w-4" /> : <Check className="h-4 w-4" />}{error ?? notice}<button className="ml-auto" onClick={() => { setError(undefined); setNotice(undefined) }} aria-label="Dismiss message"><X className="h-4 w-4" /></button></div>}

    <div className="grid min-h-0 flex-1 xl:grid-cols-[minmax(15rem,0.8fr)_minmax(32rem,1.65fr)_minmax(18rem,1fr)]">
      <aside className={cn('min-h-0 border-r bg-background', activePane !== 'source' && 'hidden xl:block')} aria-labelledby="source-fields-title">
        <div className="border-b p-4"><div className="flex items-center gap-2"><FileJson2 className="h-4 w-4 text-primary" /><h2 id="source-fields-title" className="text-sm font-semibold">Source fields</h2></div><label className="relative mt-3 block"><span className="sr-only">Search source fields</span><Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" /><Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search paths" className="h-9 pl-8 font-mono text-xs" /></label></div>
        <div className="max-h-[calc(100vh-15rem)] overflow-y-auto p-2">{sourcePointers.map((pointer) => <button key={pointer} disabled={!editable || !activeNodeId} onClick={() => updateNode(activeNodeId, { sourcePointers: [pointer] })} className="group flex min-h-10 w-full items-center gap-2 rounded-md px-2 text-left font-mono text-xs hover:bg-accent disabled:opacity-50"><ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" /><span className="truncate">{pointer}</span><Copy className="ml-auto h-3.5 w-3.5 opacity-0 group-hover:opacity-100" /></button>)}</div>
      </aside>

      <main className={cn('min-h-0 bg-background', activePane !== 'mapping' && 'hidden xl:block')} aria-labelledby="rules-title">
        <div className="flex items-center justify-between border-b px-4 py-3"><h2 id="rules-title" className="text-sm font-semibold">Rules</h2>{editable && <Button size="sm" variant="outline" onClick={addNode} className="gap-2"><Plus className="h-4 w-4" />Add field</Button>}</div>
        <div className="max-h-[calc(100vh-13rem)] overflow-y-auto divide-y">{nodes.map((node, index) => <article key={node.id ?? index} onClick={() => setActiveNodeId(node.id)} className={cn('px-4 py-3 transition-colors', activeNodeId === node.id ? 'bg-accent/75' : 'hover:bg-muted/30')}><div className="mb-2 flex items-center justify-between gap-3"><div className="flex min-w-0 items-center gap-2"><span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold tabular-nums">{index + 1}</span><span className="truncate font-mono text-xs font-medium text-primary">{node.targetPointer}</span></div>{editable && <Button variant="ghost" size="icon" className="h-8 w-8" onClick={(event) => { event.stopPropagation(); removeNode(node.id) }} aria-label={`Remove rule ${index + 1}`}><Trash2 className="h-4 w-4" /></Button>}</div><div className="grid gap-2 sm:grid-cols-[8.5rem_1fr]"><label className="grid gap-1 text-xs font-medium text-muted-foreground">Operation<select disabled={!editable} value={node.operation} onChange={(event) => updateNode(node.id, { operation: event.target.value as MappingNode['operation'] })} className="h-9 rounded-md border bg-background px-2 text-sm text-foreground"><option value="COPY">Copy value</option><option value="CONSTANT">Fixed value</option><option value="CONCAT">Join values</option><option value="COALESCE">First available</option><option value="UPPERCASE">Uppercase</option><option value="LOWERCASE">Lowercase</option></select></label><label className="grid gap-1 text-xs font-medium text-muted-foreground">{node.operation === 'CONSTANT' ? 'Fixed value' : 'Source JSON pointer'}<Input disabled={!editable} value={node.operation === 'CONSTANT' ? node.constant ?? '' : node.sourcePointers?.[0] ?? ''} onChange={(event) => updateNode(node.id, node.operation === 'CONSTANT' ? { constant: event.target.value } : { sourcePointers: [event.target.value] })} className="h-9 font-mono text-xs text-foreground" /></label><label className="grid gap-1 text-xs font-medium text-muted-foreground sm:col-span-2">Target JSON pointer<Input disabled={!editable} value={node.targetPointer ?? ''} onChange={(event) => updateNode(node.id, { targetPointer: event.target.value })} className="h-9 font-mono text-xs text-foreground" /></label></div></article>)}{nodes.length === 0 && <div className="px-6 py-16 text-center"><Braces className="mx-auto h-6 w-6 text-muted-foreground" /><p className="mt-3 text-sm font-medium">No rules</p>{editable && <Button size="sm" className="mt-4" onClick={addNode}>Add field</Button>}</div>}</div>
      </main>

      <aside className={cn('min-h-0 border-l bg-[oklch(0.18_0.035_270)]', activePane !== 'output' && 'hidden xl:block')} aria-labelledby="output-title"><div className="flex items-center justify-between border-b border-white/10 px-4 py-3 text-white"><h2 id="output-title" className="text-sm font-semibold">Output</h2><Braces className="h-4 w-4 text-[oklch(0.88_0.13_155)]" /></div><div className="h-[calc(100vh-12.25rem)]"><JsonPane value={previewText} label="Generated document rule output" /></div></aside>
    </div>

    {sampleOpen && mapping?.sample && <section className="fixed inset-x-0 bottom-0 z-40 max-h-[82vh] overflow-y-auto border-t bg-background lg:left-60" aria-labelledby="samples-title"><div className="sticky top-0 z-10 flex items-center justify-between border-b bg-background px-4 py-3"><h2 id="samples-title" className="text-sm font-semibold">Samples</h2><Button variant="ghost" size="icon" onClick={() => setSampleOpen(false)} aria-label="Close samples"><X className="h-4 w-4" /></Button></div><div className="grid xl:grid-cols-3"><div className="grid min-h-80 border-r"><span className="border-b px-4 py-2 text-xs font-medium">{mapping.sample.sourceFormat}</span><JsonPane value={mapping.sample.sourceDocument ?? ''} label="Source sample document" /></div>{(mapping.sample.sourceRaw || mapping.sample.targetRaw) && <div className="grid min-h-80 border-r"><span className="border-b px-4 py-2 text-xs font-medium">Raw EDI</span><JsonPane value={mapping.sample.sourceRaw ?? mapping.sample.targetRaw ?? ''} label="Raw EDI sample" /></div>}<div className="grid min-h-80"><span className="border-b px-4 py-2 text-xs font-medium">{mapping.sample.targetFormat}</span><JsonPane value={mapping.sample.targetDocument ?? ''} label="Target sample document" /></div></div><footer className="flex justify-end border-t px-4 py-3"><Button onClick={() => { setInput(mapping.sample?.sourceDocument ?? DEFAULT_INPUT); setSampleOpen(false); setTestOpen(true) }} className="gap-2"><Beaker className="h-4 w-4" />Test sample</Button></footer></section>}

    {testOpen && <section className="fixed inset-x-0 bottom-0 z-40 max-h-[82vh] overflow-y-auto border-t bg-background lg:left-60" aria-labelledby="test-title"><div className="sticky top-0 z-10 flex items-center justify-between border-b bg-background px-4 py-3"><h2 id="test-title" className="text-sm font-semibold">Test</h2><Button variant="ghost" size="icon" onClick={() => setTestOpen(false)} aria-label="Close test"><X className="h-4 w-4" /></Button></div><div className="grid lg:grid-cols-2"><label className="grid min-h-72 border-r"><span className="border-b px-4 py-2 text-xs font-medium">Input</span><textarea value={input} onChange={(event) => { setInput(event.target.value); setTestResult(undefined) }} spellCheck={false} className="min-h-72 resize-y bg-[oklch(0.18_0.035_270)] p-4 font-mono text-xs leading-6 text-white focus-visible:outline-none" /></label><div className="grid min-h-72"><span className="border-b px-4 py-2 text-xs font-medium">Output</span><JsonPane value={testResult?.output || previewText} label="Document rule test output" /></div></div><footer className="flex flex-col justify-between gap-3 border-t px-4 py-3 sm:flex-row sm:items-end"><div className="flex flex-wrap items-end gap-3"><Button variant="outline" onClick={() => void test()} disabled={busy || dirty || preview === undefined} className="gap-2"><Beaker className="h-4 w-4" />Run test</Button>{testResult?.passed && <span className="flex h-10 items-center gap-2 text-sm font-medium text-success-foreground"><Check className="h-4 w-4" />Passed</span>} {dirty && <span className="h-10 content-center text-xs text-muted-foreground">Save before testing.</span>}</div>{editable && <div className="flex flex-wrap items-end gap-3"><label className="grid gap-1 text-xs font-medium">Publish at (optional)<Input type="datetime-local" value={effectiveAt} onChange={(event) => setEffectiveAt(event.target.value)} className="h-9" /></label><Button onClick={() => void publish()} disabled={busy || !testResult?.passed} className="gap-2"><Send className="h-4 w-4" />{effectiveAt ? 'Schedule publication' : 'Publish revision'}</Button></div>}</footer></section>}
  </div>
}

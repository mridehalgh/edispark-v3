'use client'

import type { MappingRevision } from '@edispark/integration-client'
import { ArrowRight, CheckCircle2, CircleAlert, FileCode2, Globe2, Plus, Search } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { LayoutBody } from '@/components/layout/layout'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

async function readApi<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: 'no-store' })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.error?.message ?? 'The request could not be completed.')
  return body as T
}

function MappingStatus({ status }: { status?: string }) {
  return <Badge className={cn('border-0 font-medium', status === 'PUBLISHED' && 'bg-success/30 text-success-foreground', status === 'SCHEDULED' && 'bg-primary/10 text-primary', status === 'DRAFT' && 'bg-muted text-foreground', status === 'ARCHIVED' && 'bg-muted text-muted-foreground')}>{status?.toLowerCase() ?? 'unknown'}</Badge>
}

export function MappingsPage() {
  const navigate = useNavigate()
  const [mappings, setMappings] = useState<MappingRevision[]>([])
  const [query, setQuery] = useState('')
  const [creating, setCreating] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()

  const refresh = useCallback(async () => {
    try {
      setMappings(await readApi<MappingRevision[]>('/api/integration/mappings'))
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Mappings could not be loaded.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const schemaCatalog = useMemo(() => Array.from(new Set(mappings.flatMap((item) => [item.sourceSchemaId, item.targetSchemaId]).filter(Boolean) as string[])), [mappings])
  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    return mappings.filter((item) => !normalized || item.name?.toLowerCase().includes(normalized) || item.status?.toLowerCase().includes(normalized) || item.vendor?.toLowerCase().includes(normalized))
  }, [mappings, query])

  async function createMapping(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    try {
      setCreating(true); setError(undefined)
      const mapping = await readApi<MappingRevision>('/api/integration/mappings', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: form.get('name'), sourceSchemaId: form.get('sourceSchemaId'), targetSchemaId: form.get('targetSchemaId'),
          nodes: [{ id: crypto.randomUUID(), operation: 'COPY', sourcePointers: ['/document/id'], targetPointer: '/document/id' }],
        }),
      })
      navigate(`/mappings/${mapping.id}`)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'The mapping could not be created.')
    } finally {
      setCreating(false)
    }
  }

  return <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
    <header className="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
      <div><h1 className="text-2xl font-semibold tracking-tight">Mappings</h1><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">Transform canonical business documents into each partner’s required shape, test with representative data, and publish immutable revisions.</p></div>
      <Button onClick={() => setCreating((value) => !value)} className="gap-2"><Plus className="h-4 w-4" />New mapping</Button>
    </header>

    {error && <div role="alert" className="mb-4 flex gap-3 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive"><CircleAlert className="mt-0.5 h-4 w-4 shrink-0" />{error}</div>}

    {creating && <section className="mb-5 border-y bg-muted/25 py-5" aria-labelledby="new-mapping-title">
      <div className="mb-4"><h2 id="new-mapping-title" className="text-base font-semibold">Create a mapping draft</h2><p className="mt-0.5 text-sm text-muted-foreground">Start with the source and target schemas. You will define fields in the mapping workspace.</p></div>
      <form onSubmit={createMapping} className="grid gap-4 lg:grid-cols-[1.5fr_1fr_1fr_auto]">
        <label className="grid gap-1.5 text-sm font-medium">Mapping name<Input name="name" required placeholder="UBL order to retailer order" autoFocus /></label>
        <label className="grid gap-1.5 text-sm font-medium">Source schema<select name="sourceSchemaId" required className="h-10 rounded-md border bg-background px-3 font-mono text-xs"><option value="">Choose schema…</option>{schemaCatalog.map((id) => <option key={id} value={id}>{id}</option>)}</select></label>
        <label className="grid gap-1.5 text-sm font-medium">Target schema<select name="targetSchemaId" required className="h-10 rounded-md border bg-background px-3 font-mono text-xs"><option value="">Choose schema…</option>{schemaCatalog.map((id) => <option key={id} value={id}>{id}</option>)}</select></label>
        <Button className="self-end" disabled={schemaCatalog.length === 0}>Open workspace</Button>
      </form>
    </section>}

    <section className="overflow-hidden rounded-lg border bg-card" aria-labelledby="mapping-list-title">
      <div className="flex flex-col gap-3 border-b px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div><h2 id="mapping-list-title" className="text-base font-semibold">Mapping library</h2><p className="mt-0.5 text-xs text-muted-foreground">Start with an EdiSpark global mapping or manage a tenant-specific revision. Published revisions remain available for historical exchanges.</p></div>
        <label className="relative sm:w-64"><span className="sr-only">Search mappings</span><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" /><Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search mappings…" className="h-9 pl-9" /></label>
      </div>
      <div className="hidden overflow-x-auto md:block"><table className="w-full min-w-[52rem] text-left text-sm"><thead className="border-b bg-muted/55 text-xs text-muted-foreground"><tr><th className="px-4 py-2.5 font-medium">Mapping</th><th className="px-4 py-2.5 font-medium">Revision</th><th className="px-4 py-2.5 font-medium">Status</th><th className="px-4 py-2.5 font-medium">Operations</th><th className="px-4 py-2.5 font-medium">Published</th><th className="px-4 py-2.5"><span className="sr-only">Open</span></th></tr></thead><tbody className="divide-y">{visible.map((mapping) => <tr key={mapping.id} className="hover:bg-muted/35"><td className="px-4 py-3"><div className="flex flex-wrap items-center gap-2"><FileCode2 className="h-4 w-4 text-primary" /><span className="font-medium">{mapping.name}</span>{mapping.scope === 'GLOBAL' && <Badge variant="outline" className="gap-1 border-primary/30 text-primary"><Globe2 className="h-3 w-3" />{mapping.vendor} default</Badge>}</div><p className="mt-1 font-mono text-xs text-muted-foreground">{mapping.sourceSchemaId?.slice(0, 8)}… → {mapping.targetSchemaId?.slice(0, 8)}…</p></td><td className="px-4 py-3 tabular-nums">r{mapping.revision ?? 1}</td><td className="px-4 py-3"><MappingStatus status={mapping.status} /></td><td className="px-4 py-3 tabular-nums">{mapping.nodes?.length ?? 0}</td><td className="px-4 py-3 text-xs text-muted-foreground">{mapping.publishedAt ? new Date(mapping.publishedAt).toLocaleDateString('en-GB') : 'Not published'}</td><td className="px-4 py-3 text-right"><Button asChild size="sm" variant="ghost"><Link to={`/mappings/${mapping.id}`}>{mapping.status === 'DRAFT' ? 'Continue editing' : 'View mapping'}<ArrowRight className="ml-2 h-4 w-4" /></Link></Button></td></tr>)}</tbody></table></div>
      <div className="divide-y md:hidden">{visible.map((mapping) => <Link key={mapping.id} to={`/mappings/${mapping.id}`} className="block px-4 py-4 hover:bg-muted/35"><div className="flex items-start justify-between gap-3"><div><p className="font-medium">{mapping.name}</p><p className="mt-1 text-xs text-muted-foreground">{mapping.scope === 'GLOBAL' ? `${mapping.vendor} default · ` : ''}Revision {mapping.revision ?? 1} · {mapping.nodes?.length ?? 0} operations</p></div><MappingStatus status={mapping.status} /></div></Link>)}</div>
      {!loading && visible.length === 0 && <div className="px-6 py-12 text-center"><CheckCircle2 className="mx-auto h-5 w-5 text-muted-foreground" /><p className="mt-3 text-sm font-medium">No mappings match this search</p><p className="mt-1 text-sm text-muted-foreground">Clear the search or create a new mapping draft.</p></div>}
    </section>
  </LayoutBody>
}

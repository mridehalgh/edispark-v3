'use client'

import type { MappingRevision } from '@edispark/integration-client'
import { ArrowRight, ChevronDown, CircleAlert, FileCode2, Globe2, Plus, Search } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { AdminEmptyState, AdminPage, AdminPageHeader } from '@/components/admin/admin-page'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

async function readApi<T>(url: string): Promise<T> {
  const response = await fetch(url, { cache: 'no-store' })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.error?.message ?? 'The request could not be completed.')
  return body as T
}

function RuleStatus({ status }: { status?: string }) {
  return <Badge className={cn('border-0 font-medium', status === 'PUBLISHED' && 'bg-success/30 text-success-foreground', status === 'SCHEDULED' && 'bg-primary/10 text-primary', status === 'DRAFT' && 'bg-muted text-foreground', (status === 'ARCHIVED' || status === 'RETIRED') && 'bg-muted text-muted-foreground')}>{status?.toLowerCase() ?? 'unknown'}</Badge>
}

function ruleFormat(mapping: MappingRevision) {
  const source = mapping.sample?.sourceFormat || mapping.sourceSchemaId
  const target = mapping.sample?.targetFormat || mapping.targetSchemaId
  return source && target ? `${source} → ${target}` : 'Document fields'
}

export function MappingsPage() {
  const [mappings, setMappings] = useState<MappingRevision[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()

  const refresh = useCallback(async () => {
    try {
      setMappings(await readApi<MappingRevision[]>('/api/integration/mappings'))
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Document rules could not be loaded.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const families = useMemo(() => {
    const grouped = new Map<string, MappingRevision[]>()
    for (const mapping of mappings) {
      const key = mapping.familyId || mapping.id || mapping.name || 'rule'
      grouped.set(key, [...(grouped.get(key) ?? []), mapping])
    }
    const normalized = query.trim().toLowerCase()
    return Array.from(grouped.entries())
      .map(([id, revisions]) => ({ id, revisions: revisions.toSorted((left, right) => (right.revision ?? 0) - (left.revision ?? 0)) }))
      .filter(({ revisions }) => !normalized || revisions.some((item) => [item.name, item.status, item.vendor, item.sample?.sourceFormat, item.sample?.targetFormat].some((value) => value?.toLowerCase().includes(normalized))))
      .toSorted((left, right) => (left.revisions[0]?.name ?? '').localeCompare(right.revisions[0]?.name ?? ''))
  }, [mappings, query])

  return <AdminPage>
    <AdminPageHeader title="Document rules" actions={<Button asChild className="gap-2"><Link to="/organisation/document-rules/new"><Plus className="h-4 w-4" />New rule</Link></Button>} />
    <div className="mb-6 border-l-2 border-primary bg-muted/30 px-4 py-3 text-sm">
      <p className="font-medium">Document rules translate fields from one document format into another.</p>
      <p className="mt-1 text-muted-foreground">Partner document routes use a specific revision, so a published setup never changes unexpectedly.</p>
    </div>
    {error ? <div role="alert" className="mb-4 flex gap-3 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive"><CircleAlert className="mt-0.5 h-4 w-4 shrink-0" />{error}</div> : null}
    <section className="overflow-hidden rounded-lg border bg-card" aria-labelledby="rule-list-title">
      <div className="flex flex-col gap-3 border-b px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div><h2 id="rule-list-title" className="text-base font-semibold">Rule families</h2><p className="mt-0.5 text-xs text-muted-foreground">Open a family to see its revision history.</p></div>
        <label className="relative sm:w-64"><span className="sr-only">Search document rules</span><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" /><Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search rules" className="h-9 pl-9" /></label>
      </div>
      <div className="divide-y">
        {families.map(({ id, revisions }) => {
          const current = revisions.find((item) => item.status === 'PUBLISHED') ?? revisions.find((item) => item.status === 'SCHEDULED') ?? revisions[0]
          return <details key={id} className="group">
            <summary className="grid cursor-pointer list-none items-center gap-3 px-4 py-4 hover:bg-muted/30 sm:grid-cols-[minmax(14rem,1.5fr)_minmax(8rem,1fr)_8rem_2rem]">
              <div className="min-w-0"><div className="flex items-center gap-2"><FileCode2 className="h-4 w-4 shrink-0 text-primary" /><span className="truncate text-sm font-medium">{current?.name}</span>{current?.scope === 'GLOBAL' ? <Badge variant="outline" className="gap-1 border-primary/30 text-primary"><Globe2 className="h-3 w-3" />EdiSpark</Badge> : null}</div><p className="mt-1 truncate text-xs text-muted-foreground">{current ? ruleFormat(current) : 'Document fields'}</p></div>
              <span className="text-xs text-muted-foreground">{revisions.length} {revisions.length === 1 ? 'revision' : 'revisions'}</span>
              <div className="flex items-center gap-2"><RuleStatus status={current?.status} /><span className="text-xs tabular-nums">r{current?.revision ?? 1}</span></div>
              <ChevronDown className="h-4 w-4 text-muted-foreground transition-transform group-open:rotate-180" />
            </summary>
            <div className="border-t bg-muted/15 px-4 py-2 sm:pl-10">
              {revisions.map((revision) => <div key={revision.id} className="grid items-center gap-3 border-b py-3 last:border-0 sm:grid-cols-[6rem_8rem_1fr_auto]">
                <span className="text-sm font-medium tabular-nums">Revision {revision.revision ?? 1}</span>
                <RuleStatus status={revision.status} />
                <span className="text-xs text-muted-foreground">{revision.publishedAt ? `Published ${new Date(revision.publishedAt).toLocaleDateString('en-GB')}` : revision.scheduledAt ? `Scheduled ${new Date(revision.scheduledAt).toLocaleDateString('en-GB')}` : 'Not published'}</span>
                {revision.id ? <Button asChild variant="ghost" size="sm"><Link to={`/organisation/document-rules/${revision.id}`}>{revision.status === 'DRAFT' ? 'Edit' : 'View'}<ArrowRight className="ml-2 h-4 w-4" /></Link></Button> : null}
              </div>)}
            </div>
          </details>
        })}
      </div>
      {!loading && families.length === 0 ? <AdminEmptyState title={query ? 'No matching rules' : 'No document rules'} /> : null}
    </section>
  </AdminPage>
}

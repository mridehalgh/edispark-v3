'use client'

import type { MappingRevision } from '@edispark/integration-client'
import { ArrowLeft, CircleAlert } from 'lucide-react'
import { FormEvent, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { AdminPage, AdminPageHeader } from '@/components/admin/admin-page'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

async function readApi<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: 'no-store' })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.error?.message ?? 'The request could not be completed.')
  return body as T
}

export function NewDocumentRulePage() {
  const navigate = useNavigate()
  const [mappings, setMappings] = useState<MappingRevision[]>([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    void readApi<MappingRevision[]>('/api/integration/mappings')
      .then(setMappings)
      .catch((requestError) => setError(requestError instanceof Error ? requestError.message : 'Schemas could not be loaded.'))
  }, [])

  const schemas = useMemo(
    () => Array.from(new Set(mappings.flatMap((item) => [item.sourceSchemaId, item.targetSchemaId]).filter(Boolean) as string[])),
    [mappings],
  )

  async function createRule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    try {
      setSaving(true)
      setError(undefined)
      const mapping = await readApi<MappingRevision>('/api/integration/mappings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: form.get('name'),
          sourceSchemaId: form.get('sourceSchemaId'),
          targetSchemaId: form.get('targetSchemaId'),
          nodes: [{ id: crypto.randomUUID(), operation: 'COPY', sourcePointers: ['/document/id'], targetPointer: '/document/id' }],
        }),
      })
      navigate(`/organisation/document-rules/${mapping.id}`)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'The document rule could not be created.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <AdminPage className="max-w-3xl">
      <Button asChild variant="ghost" className="mb-4 -ml-3 gap-2">
        <Link to="/organisation/document-rules"><ArrowLeft className="h-4 w-4" />Document rules</Link>
      </Button>
      <AdminPageHeader title="New document rule" />
      {error ? <div role="alert" className="mb-5 flex gap-3 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive"><CircleAlert className="mt-0.5 h-4 w-4 shrink-0" />{error}</div> : null}
      <form onSubmit={createRule} className="grid gap-5 rounded-lg border bg-card p-5 sm:p-6">
        <label className="grid gap-2 text-sm font-medium">Rule name<Input name="name" required placeholder="Order to retailer order" autoFocus /></label>
        <label className="grid gap-2 text-sm font-medium">From<select name="sourceSchemaId" required className="h-10 rounded-md border bg-background px-3 font-mono text-xs"><option value="">Choose a document format</option>{schemas.map((id) => <option key={id} value={id}>{id}</option>)}</select></label>
        <label className="grid gap-2 text-sm font-medium">To<select name="targetSchemaId" required className="h-10 rounded-md border bg-background px-3 font-mono text-xs"><option value="">Choose a document format</option>{schemas.map((id) => <option key={id} value={id}>{id}</option>)}</select></label>
        <div className="flex justify-end gap-3 border-t pt-5"><Button asChild variant="outline"><Link to="/organisation/document-rules">Cancel</Link></Button><Button type="submit" disabled={saving || schemas.length === 0}>{saving ? 'Creating…' : 'Create rule'}</Button></div>
      </form>
    </AdminPage>
  )
}

'use client'

import type { CreateMappingRequest, GetMappingExampleFilesResponse, MappingRevision } from '@edispark/integration-client'
import { ArrowLeft, ArrowRight, BookOpen } from 'lucide-react'
import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { AdminNotice, AdminPage, AdminPageHeader } from '@/components/admin/admin-page'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { templateRule } from '@/lib/mapping-fields'
import { DownloadMappingFile, exampleTestFiles, MappingFileUpload, stageDraftFiles, type TestFiles } from './mapping-test-files'

const rulesPath = '/organisation/document-rules'
const formatSelectClass = 'h-10 w-full min-w-0 rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-60'

type FormatLabel = { label: string; document?: string; format?: string }

function ruleFormats(mappings: MappingRevision[]) {
  const labels = new Map<string, FormatLabel>()
  const fallbacks = new Map<string, string>()
  const compatibleTargets = new Map<string, Set<string>>()

  for (const mapping of mappings) {
    const { sourceSchemaId: source, targetSchemaId: target, compatibility, sample } = mapping
    if (!source || !target) continue
    fallbacks.set(source, `${mapping.name ?? 'Document rule'} (input)`)
    fallbacks.set(target, `${mapping.name ?? 'Document rule'} (output)`)

    const { direction, documentType, standard, release, messageType } = compatibility ?? {}
    if ((direction === 'INBOUND' || direction === 'OUTBOUND') && documentType && standard && release && messageType) {
      const document = documentType.toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase())
      const inbound = direction === 'INBOUND'
      labels.set(inbound ? source : target, { label: `${document} · ${standard} ${messageType} ${release}`, document, format: `${standard} ${release}` })
      labels.set(inbound ? target : source, { label: `${document} · UBL 2.1`, document, format: 'UBL 2.1' })
      const targets = compatibleTargets.get(source) ?? new Set<string>()
      targets.add(target)
      compatibleTargets.set(source, targets)
    } else {
      if (!labels.has(source) && sample?.sourceFormat) labels.set(source, { label: sample.sourceFormat })
      if (!labels.has(target) && sample?.targetFormat) labels.set(target, { label: sample.targetFormat })
    }
  }

  const options = [...fallbacks].map(([id, fallback]) => ({ id, ...(labels.get(id) ?? { label: fallback }) }))
    .sort((first, second) => first.label.localeCompare(second.label))
  return { options, compatibleTargets }
}

function suggestRuleName(source: FormatLabel, target: FormatLabel) {
  if (source.document && source.document === target.document && source.format && target.format) {
    return `${source.document} · ${source.format} → ${target.format}`
  }
  return `${source.label} → ${target.label}`.slice(0, 160)
}

async function readApi<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: 'no-store' })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.error?.message ?? 'The request could not be completed.')
  return body as T
}

export function NewDocumentRulePage() {
  const navigate = useNavigate()
  const [mappings, setMappings] = useState<MappingRevision[]>()
  const [catalogueError, setCatalogueError] = useState<string>()
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [sourceId, setSourceId] = useState('')
  const [targetId, setTargetId] = useState('')
  const [customName, setCustomName] = useState<string>()
  const [copyMapping, setCopyMapping] = useState(false)
  const [starterId, setStarterId] = useState('')
  const [selectedFiles, setSelectedFiles] = useState<{ pair: string; files: TestFiles }>()
  const [loadingFiles, setLoadingFiles] = useState(false)
  const [fileError, setFileError] = useState<string>()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    const controller = new AbortController()
    void readApi<MappingRevision[]>('/api/integration/mappings', { signal: controller.signal })
      .then((result) => { if (!controller.signal.aborted) setMappings(result) })
      .catch(() => { if (!controller.signal.aborted) setCatalogueError('Document formats could not be loaded.') })
    return () => controller.abort()
  }, [loadAttempt])

  const { options, compatibleTargets } = useMemo(() => ruleFormats(mappings ?? []), [mappings])
  const recommended = options.filter(({ id }) => compatibleTargets.get(sourceId)?.has(id))
  const otherTargets = options.filter(({ id }) => !compatibleTargets.get(sourceId)?.has(id))
  const source = options.find(({ id }) => id === sourceId)
  const target = options.find(({ id }) => id === targetId)
  const starters = mappings?.filter((mapping) => mapping.id && mapping.scope === 'GLOBAL' && mapping.status === 'PUBLISHED'
    && mapping.sourceSchemaId === sourceId && mapping.targetSchemaId === targetId && mapping.nodes?.length) ?? []
  const starter = starters.find((mapping) => mapping.id === starterId) ?? starters.find((mapping) => mapping.sample) ?? starters[0]
  const inbound = starter?.compatibility?.direction === 'INBOUND'
  const outbound = starter?.compatibility?.direction === 'OUTBOUND'
  const exampleAvailable = starter?.id && (starter.compatibility || starter.sample)
  const pair = `${sourceId}:${targetId}`
  const files = selectedFiles?.pair === pair ? selectedFiles.files : undefined
  const suggestedName = source && target ? suggestRuleName(source, target) : ''
  const name = customName?.trim() || suggestedName
  const loading = !mappings && !catalogueError

  function updateFiles(patch: TestFiles) {
    setSelectedFiles((current) => ({ pair, files: { ...(current?.pair === pair ? current.files : {}), ...patch } }))
  }

  async function loadExample() {
    if (!starter?.id) return
    setLoadingFiles(true)
    setFileError(undefined)
    try {
      const example = await readApi<GetMappingExampleFilesResponse>(`/api/integration/mappings/${starter.id}/file-preview`)
      const exampleFiles = exampleTestFiles(example, inbound)
      if (!exampleFiles.source) throw new Error('No example input is available for these formats.')
      updateFiles(exampleFiles)
    } catch (error) {
      setFileError(error instanceof Error ? error.message : 'Could not load the example files.')
    } finally {
      setLoadingFiles(false)
    }
  }

  async function createRule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!source || !target || saving) return
    try {
      setSaving(true)
      setError(undefined)
      const exampleNode = templateRule('/document/id', starter) ?? starter?.nodes?.find((node) => node.operation === 'COPY')
      const nodes = copyMapping && starter?.nodes ? starter.nodes.map((node) => ({ ...node, id: crypto.randomUUID() }))
        : [{ ...(exampleNode ?? { operation: 'COPY' as const, sourcePointers: ['/document/id'], targetPointer: '/document/id' }), id: crypto.randomUUID() }]
      const request: CreateMappingRequest = { name, sourceSchemaId: source.id, targetSchemaId: target.id, nodes }
      const mapping = await readApi<MappingRevision>('/api/integration/mappings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      })
      if (!mapping.id) throw new Error('The new rule could not be opened.')
      if (files?.source || files?.expected) stageDraftFiles(mapping.id, files)
      navigate(`${rulesPath}/${mapping.id}`)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'The document rule could not be created.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <AdminPage className="max-w-4xl">
      <Button asChild variant="ghost" className="mb-3 -ml-3 gap-2 text-muted-foreground">
        <Link to={rulesPath}><ArrowLeft aria-hidden="true" className="h-4 w-4" />Document rules</Link>
      </Button>
      <AdminPageHeader title="New document rule" description="Choose the document formats for this mapping. You’ll match fields and test the rule in the draft." />
      {catalogueError && <AdminNotice>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span>{catalogueError}</span>
          <Button type="button" variant="outline" size="sm" onClick={() => {
            setCatalogueError(undefined)
            setLoadAttempt((attempt) => attempt + 1)
          }}>Try again</Button>
        </div>
      </AdminNotice>}
      {!loading && !catalogueError && options.length === 0 && <AdminNotice>No document formats are available.</AdminNotice>}
      <form onSubmit={createRule} className="overflow-hidden rounded-lg border bg-card">
        <fieldset disabled={saving || loadingFiles} className="grid min-w-0 gap-6 p-5 sm:p-6">
          <div className="grid gap-x-4 gap-y-3 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:items-end">
            <div className="grid min-w-0 gap-2">
              <div>
                <label htmlFor="rule-source" className="text-sm font-semibold">Input format</label>
                <p id="rule-source-help" className="text-sm text-muted-foreground">The document you start with.</p>
              </div>
              <select id="rule-source" name="sourceSchemaId" aria-describedby="rule-source-help" required disabled={!options.length} value={sourceId} onChange={(event) => {
                setSourceId(event.target.value)
                setTargetId('')
                setCopyMapping(false)
                setStarterId('')
                setSelectedFiles(undefined)
                setFileError(undefined)
              }} className={formatSelectClass}>
                <option value="" disabled>{loading ? 'Loading formats…' : 'Select input format'}</option>
                {options.map(({ id, label }) => <option key={id} value={id}>{label}</option>)}
              </select>
            </div>
            <ArrowRight aria-hidden="true" className="ml-3 h-4 w-4 rotate-90 text-muted-foreground sm:mb-3 sm:ml-0 sm:rotate-0" />
            <div className="grid min-w-0 gap-2">
              <div>
                <label htmlFor="rule-target" className="text-sm font-semibold">Output format</label>
                <p id="rule-target-help" className="text-sm text-muted-foreground">The document the rule produces.</p>
              </div>
              <select id="rule-target" name="targetSchemaId" aria-describedby="rule-target-help" required disabled={!source} value={targetId} onChange={(event) => {
                setTargetId(event.target.value)
                setCopyMapping(false)
                setStarterId('')
                setSelectedFiles(undefined)
                setFileError(undefined)
              }} className={formatSelectClass}>
                <option value="" disabled>{source ? 'Select output format' : 'Select input format first'}</option>
                {recommended.length > 0 && <optgroup label="Compatible with this input">
                  {recommended.map(({ id, label }) => <option key={id} value={id}>{label}</option>)}
                </optgroup>}
                <optgroup label={recommended.length ? 'Other formats' : 'Available formats'}>
                  {otherTargets.map(({ id, label }) => <option key={id} value={id}>{label}</option>)}
                </optgroup>
              </select>
            </div>
          </div>
          <div className="grid gap-2 border-t pt-5">
            <label htmlFor="rule-name" className="text-sm font-semibold">Rule name</label>
            <Input id="rule-name" name="name" maxLength={160} value={customName ?? suggestedName} onChange={(event) => setCustomName(event.target.value)} placeholder={suggestedName || 'Suggested when you select both formats'} />
          </div>
          {source && target && <div className="grid min-w-0 gap-4 border-t pt-5" key={pair}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold">Test files <span className="font-normal text-muted-foreground">(optional)</span></h2>
                <p className="mt-1 text-sm text-muted-foreground">Add an input and an expected output to compare in the draft.</p>
              </div>
              {exampleAvailable && <Button type="button" variant="outline" size="sm" className="gap-2" onClick={() => void loadExample()}>
                <BookOpen className="h-4 w-4" aria-hidden="true" />{loadingFiles ? 'Loading example…' : 'Load example files'}
              </Button>}
            </div>
            {starters.length > 1 && <label className="grid gap-2 text-sm font-medium">
              Built-in example
              <select className={formatSelectClass} value={starter?.id} onChange={(event) => {
                setStarterId(event.target.value)
                setSelectedFiles(undefined)
                setFileError(undefined)
              }}>
                {starters.map((mapping) => <option key={mapping.id} value={mapping.id}>{mapping.name}</option>)}
              </select>
            </label>}
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="grid min-w-0 content-start gap-2">
                <MappingFileUpload label={inbound ? 'Upload EDI or JSON input' : 'Upload JSON input'} allowEdi={inbound} allowJson onLoad={(file) => updateFiles({ source: file })} />
                {files?.source && <div className="flex flex-wrap items-center gap-x-2" role="status">
                  <span className="break-all text-xs text-muted-foreground">{files.source.name}</span><DownloadMappingFile file={files.source} label="Download input" />
                  <Button type="button" variant="ghost" size="sm" onClick={() => updateFiles({ source: undefined })}>Remove input</Button>
                </div>}
              </div>
              <div className="grid min-w-0 content-start gap-2">
                <MappingFileUpload label={outbound ? 'Upload expected EDI' : 'Upload expected JSON'} allowEdi={outbound} allowJson={!outbound} onLoad={(file) => updateFiles({ expected: file })} />
                {files?.expected && <div className="flex flex-wrap items-center gap-x-2" role="status">
                  <span className="break-all text-xs text-muted-foreground">{files.expected.name}</span><DownloadMappingFile file={files.expected} label="Download expected" />
                  <Button type="button" variant="ghost" size="sm" onClick={() => updateFiles({ expected: undefined })}>Remove expected</Button>
                </div>}
              </div>
            </div>
            {fileError && <p role="alert" className="text-sm text-destructive">{fileError}</p>}
            {starter && <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" checked={copyMapping} onChange={(event) => setCopyMapping(event.target.checked)} className="mt-1 size-4 accent-primary" />
              <span><span className="font-medium">Copy the built-in mapping</span><span className="block text-muted-foreground">Start with its field mappings already in your draft.</span></span>
            </label>}
          </div>}
        </fieldset>
        {error && <div className="px-5 sm:px-6"><AdminNotice>{error}</AdminNotice></div>}
        <div className="flex justify-end gap-2 border-t bg-muted/30 px-5 py-4 sm:px-6">
          <Button asChild variant="outline"><Link to={rulesPath}>Cancel</Link></Button>
          <Button type="submit" disabled={saving || loadingFiles || !source || !target} className="motion-safe:transition-[transform,background-color] motion-safe:duration-150 motion-safe:ease-out motion-safe:active:scale-[0.98] motion-reduce:transition-none">
            {saving ? 'Creating draft…' : 'Create draft'}
          </Button>
        </div>
      </form>
    </AdminPage>
  )
}

'use client'

import type {
  Availability, Coordinates, Difference, DraftRequest, EstimateRequest, FixtureResult, Job, MigFinding,
  MappingNode, MappingRevision, ReviewRequest, Rule, Section, Selection, StructureComparison, Supersession,
  TradingPartner, Upload, UploadRequest,
} from '@edispark/integration-client'
import { ArrowLeft, FileText, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { AdminNotice, AdminPage, AdminPageHeader } from '@/components/admin/admin-page'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

export const migPath = '/organisation/document-rules/generate'
const rulesPath = '/organisation/document-rules'
export const migApi = '/api/integration/mappings/mig'
const guideTypes = '.pdf,.doc,.docx,.xls,.xlsx,.txt'
const documentTypes = [
  ['ORDER', 'ORDERS', 'Order'], ['INVOICE', 'INVOIC', 'Invoice'], ['ORDER_RESPONSE', 'ORDRSP', 'Order response'],
  ['DESPATCH_ADVICE', 'DESADV', 'Despatch advice'], ['RECEIPT_ADVICE', 'RECADV', 'Receipt advice'],
  ['REMITTANCE_ADVICE', 'REMADV', 'Remittance advice'],
] as const
const activeStates = new Set<Job['state']>(['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'])
const labels: Record<NonNullable<Job['state']>, string> = {
  ESTIMATED: 'Estimate ready', QUEUED: 'Queued', RUNNING: 'Generating candidates', CANCEL_REQUESTED: 'Cancelling',
  INCOMPLETE: 'Candidate incomplete', RECONCILIATION: 'Usage reconciliation required', REVIEW: 'Review required',
  READY: 'Reviewed mapping passed', IMPORTING: 'Draft creation started', IMPORTED: 'Draft created', CANCELLED: 'Cancelled',
}
function labelsForMode(mode: Job['modelMode'], modelIds?: Job['modelIds']) {
  const jev = modelIds?.JEV ? `TypeSafe Jev · ${modelIds.JEV}` : 'TypeSafe Jev'
  if (mode === 'LONDON_BEDROCK' || mode === 'BEDROCK_AND_JEV') {
    const claudeLabels: Record<string, string> = {
      'eu.anthropic.claude-sonnet-5-5': 'Claude Sonnet 5.5 (Europe)',
      'anthropic.claude-opus-5-5': 'Claude Opus 5.5',
    }
    return { SONNET: claudeLabels[modelIds?.SONNET ?? ''] ?? 'Claude Sonnet 4.6', NOVA: 'Amazon Nova Lite', JEV: jev }
  }
  if (mode === 'LOCAL_FIXTURE') return { SONNET: 'Local fixture candidate A', NOVA: 'Local fixture candidate B', JEV: jev }
  return { SONNET: 'Candidate A', NOVA: 'Candidate B', JEV: jev }
}

export async function readMig<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { cache: 'no-store', ...init })
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new Error(data?.error?.message ?? 'The MIG request could not be completed.')
  return data as T
}

function postMig<T>(path: string, body?: unknown) {
  return readMig<T>(path, {
    method: 'POST',
    ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
  })
}

async function uploadFile(file: File, role: UploadRequest['role'], edition?: string) {
  const maximum = (role === 'PRIMARY' || role === 'SUPPLEMENT' ? 3 : 1) * 1024 * 1024
  if (file.size < 1 || file.size > maximum) throw new Error(`${file.name} exceeds its file size limit or is empty.`)
  const bytes = new Uint8Array(await file.arrayBuffer())
  const chunks: string[] = []
  for (let offset = 0; offset < bytes.length; offset += 16_384) {
    chunks.push(String.fromCharCode(...bytes.subarray(offset, offset + 16_384)))
  }
  const body: UploadRequest = { role, name: file.name, edition, base64: btoa(chunks.join('')) }
  const uploaded = await postMig<Upload>(`${migApi}/uploads`, body)
  if (!uploaded.id) throw new Error('The upload returned no file identifier.')
  return uploaded.id
}

function field(form: FormData, name: string): string {
  const value = form.get(name)
  return typeof value === 'string' ? value.trim() : ''
}

function file(form: FormData, name: string): File | undefined {
  const value = form.get(name)
  return value instanceof File && value.name ? value : undefined
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="grid gap-1.5 text-sm font-medium">{label}{children}</label>
}

function Select(props: React.ComponentProps<'select'>) {
  return <select {...props} className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" />
}

function GuideIntake({ onEstimate, busy }: { onEstimate: (event: FormEvent<HTMLFormElement>) => void; busy: boolean }) {
  const [direction, setDirection] = useState('OUTBOUND')
  const [documentType, setDocumentType] = useState('ORDER')
  const [standard, setStandard] = useState('EDIFACT')
  const [release, setRelease] = useState('D96A')
  const [supplements, setSupplements] = useState(0)
  const [hasInput, setHasInput] = useState(false)
  const [partners, setPartners] = useState<TradingPartner[]>([])
  const [catalogue, setCatalogue] = useState<MappingRevision[]>([])
  const compatible = catalogue.filter((mapping) => mapping.scope === 'GLOBAL' && mapping.status === 'PUBLISHED'
    && mapping.compatibility?.direction === direction && mapping.compatibility.documentType === documentType
    && mapping.compatibility.standard === standard && mapping.compatibility.release === release)
  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal }
    void readMig<TradingPartner[]>('/api/integration/partners', options).then(setPartners).catch(() => undefined)
    void readMig<MappingRevision[]>('/api/integration/mappings', options).then(setCatalogue).catch(() => undefined)
    return () => controller.abort()
  }, [])
  return <form onSubmit={onEstimate} className="space-y-6">
    <fieldset disabled={busy} className="space-y-6">
      <section className="space-y-4 rounded-lg border bg-card p-5">
        <h2 className="font-semibold">Exchange format</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Direction"><Select name="direction" value={direction} onChange={(event) => {
            setDirection(event.target.value)
            setHasInput(false)
          }}><option value="OUTBOUND">Outbound to EDI</option><option value="INBOUND">Inbound to UBL</option></Select></Field>
          <Field label="Document"><Select name="documentType" value={documentType} onChange={(event) => setDocumentType(event.target.value)}>{documentTypes.map(([key, , label]) => <option key={key} value={key}>{label}</option>)}</Select></Field>
          <Field label="Standard"><Select name="standard" value={standard} onChange={(event) => {
            setStandard(event.target.value)
            setRelease(event.target.value === 'EDIFACT' ? 'D96A' : '9')
          }}><option>EDIFACT</option><option>TRADACOMS</option></Select></Field>
          <Field label="Release"><Input name="release" value={release} required maxLength={12} onChange={(event) => setRelease(event.target.value.toUpperCase())} /></Field>
        </div>
        {compatible.length > 0 && <Field label="Catalogue profile (optional)">
          <Select key={`${direction}/${documentType}/${standard}/${release}`} name="catalogueRevisionId" defaultValue="">
            <option value="">Default profile for this format</option>
            {compatible.filter((mapping) => mapping.id).map((mapping) => <option key={mapping.id} value={mapping.id}>{mapping.name}</option>)}
          </Select>
        </Field>}
        <Field label="Trading partner (optional)"><Select name="partnerId" defaultValue=""><option value="">No partner selected</option>{partners.filter((partner) => partner.id).map((partner) => <option key={partner.id} value={partner.id}>{partner.displayName}</option>)}</Select></Field>
      </section>
      <section className="space-y-4 rounded-lg border bg-card p-5">
        <div><h2 className="font-semibold">Implementation guides</h2><p className="text-sm text-muted-foreground">PDF, Word, Excel or UTF-8 text up to 3 MiB per guide.</p></div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Primary guide"><Input name="primary" type="file" accept={guideTypes} required /></Field>
          <Field label="Primary edition"><Input name="edition" maxLength={100} required placeholder="Edition or revision date" /></Field>
          {Array.from({ length: supplements }, (_, index) => <div key={index} className="contents">
            <Field label={`Supplement ${index + 1}`}><Input name={`supplement${index}`} type="file" accept={guideTypes} required /></Field>
            <Field label={`Supplement ${index + 1} edition`}><Input name={`supplementEdition${index}`} maxLength={100} required /></Field>
          </div>)}
        </div>
        <div className="flex flex-wrap gap-2">
          {supplements < 3 && <Button type="button" size="sm" variant="outline" onClick={() => setSupplements((value) => value + 1)}>Add supplement</Button>}
          {supplements > 0 && <Button type="button" size="sm" variant="ghost" onClick={() => setSupplements((value) => value - 1)}>Remove last supplement</Button>}
        </div>
      </section>
      <section className="space-y-4 rounded-lg border bg-card p-5">
        <div><h2 className="font-semibold">Test files (optional)</h2>
          <p className="text-sm text-muted-foreground">Use UTF-8 files up to 1 MiB. When you skip the input, EdiSpark looks for a compatible catalogue example. Expected output is treated as a reference by default and can be partial.</p>
        </div>
        <div key={direction} className="grid gap-4 sm:grid-cols-2">
          <Field label={direction === 'INBOUND' ? 'Raw EDI input' : 'UBL JSON input'}>
            <Input name="input" type="file" accept={direction === 'INBOUND' ? '.edi,.txt' : '.json,.txt'}
              onChange={(event) => setHasInput(Boolean(event.currentTarget.files?.length))} />
          </Field>
          <Field label={direction === 'INBOUND' ? 'Expected UBL JSON' : 'Expected raw EDI'}><Input name="expected" type="file" accept={direction === 'INBOUND' ? '.json,.txt' : '.edi,.txt'} /></Field>
          {hasInput && <label className="flex items-start gap-2 text-sm sm:col-span-2">
            <input className="mt-0.5" name="exactComparison" type="checkbox" />
            <span>Compare exactly with the expected output <span className="block text-xs text-muted-foreground">Use this for a complete expected output for this input.</span></span>
          </label>}
        </div>
      </section>
      <Button type="submit">{busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}Estimate credits</Button>
    </fieldset>
  </form>
}

async function uploadIntake(form: FormData): Promise<EstimateRequest> {
  const primary = file(form, 'primary')
  const input = file(form, 'input')
  const expected = file(form, 'expected')
  const exact = Boolean(input && form.has('exactComparison'))
  if (!primary) throw new Error('Choose a primary guide.')
  const document = documentTypes.find(([type]) => type === field(form, 'documentType'))
  if (!document) throw new Error('Choose a supported document type.')
  const target: Coordinates = { direction: field(form, 'direction'), standard: field(form, 'standard'),
    release: field(form, 'release'), documentType: document[0], messageType: document[1] }
  const supplementFiles = [0, 1, 2].flatMap((index) => {
    const attachment = file(form, `supplement${index}`)
    return attachment ? [{ attachment, edition: field(form, `supplementEdition${index}`) }] : []
  })
  const [guideId, supplementIds, inputId, outputId] = await Promise.all([
    uploadFile(primary, 'PRIMARY', field(form, 'edition')),
    Promise.all(supplementFiles.map(({ attachment, edition }) => uploadFile(attachment, 'SUPPLEMENT', edition))),
    input && uploadFile(input, 'INPUT'),
    expected && uploadFile(expected, exact ? 'EXPECTED' : 'REFERENCE'),
  ])
  return { primary: guideId, supplements: supplementIds, input: inputId, expected: exact ? outputId : undefined,
    reference: exact ? undefined : outputId, partnerId: field(form, 'partnerId') || undefined, catalogueRevisionId: field(form, 'catalogueRevisionId') || undefined, target }
}

function Findings({ findings }: { findings?: MigFinding[] }) {
  if (!findings?.length) return null
  return <ul className="list-disc space-y-1 pl-5 text-sm">{findings.map((finding, index) =>
    <li key={index}>{finding.detail}{finding.citations?.length ? <span className="block break-all text-xs text-muted-foreground">{finding.citations.join(' · ')}</span> : null}</li>,
  )}</ul>
}

const structureLabels: Record<NonNullable<StructureComparison['result']>, string> = {
  MATCH: 'Agrees with directory',
  PARTNER_RESTRICTION: 'Partner restriction',
  CONFLICT: 'Directory conflict',
  NEEDS_REVIEW: 'Needs review',
  ENVELOPE: 'Interchange envelope',
}

function structureUsage(usage?: string) {
  return usage === 'M' ? 'Mandatory' : usage === 'C' ? 'Conditional' : usage === 'N' ? 'Not used' : usage
}

function StructureRows({ rows, job }: { rows: StructureComparison[]; job: Job }) {
  const excerpts = new Map(job.sections?.map((section) => [section.anchor, section.excerpt]))
  const files = new Map(job.uploads?.map((upload) => [upload.id, upload]))
  return <div className="overflow-x-auto rounded-md border">
    <table className="w-full min-w-[34rem] text-left text-sm">
      <thead className="bg-muted/40">
        <tr><th scope="col" className="p-3">Guide declaration</th><th scope="col" className="p-3">UNECE occurrence</th>
          <th scope="col" className="p-3">Finding</th></tr>
      </thead>
      <tbody className="divide-y">{rows.map(({ guide, standard, result, detail }) => {
        const [, fileId, ...location] = guide?.anchor?.split('/') ?? []
        const citation = fileId ? files.get(fileId) : undefined
        const excerpt = excerpts.get(guide?.anchor)
        return <tr key={guide?.anchor} className="align-top">
          <td className="space-y-1 p-3">
            <div className="font-medium">{guide?.position} {guide?.tag}</div>
            <div>{structureUsage(guide?.usage)} · max {guide?.maxRepeats}{guide?.usageNote ? ` · ${guide.usageNote}` : ''}</div>
            <details className="text-xs text-muted-foreground">
              <summary className="cursor-pointer break-words">{[citation?.name, citation?.edition, location.join('/')].filter(Boolean).join(' · ') || guide?.anchor}</summary>
              {excerpt && <blockquote className="mt-2 whitespace-pre-wrap break-words border-l-2 pl-2">{excerpt}</blockquote>}
            </details>
          </td>
          <td className="p-3">{standard ? <>
            <div className="font-medium">{standard.position} {standard.tag}</div>
            <div>{structureUsage(standard.usage)} · max {standard.maxRepeats}</div>
            <div className="text-xs text-muted-foreground">{[standard.area, standard.groupPath].filter(Boolean).join(' · ')}</div>
          </> : '—'}</td>
          <td className="p-3">
            <div className={result === 'CONFLICT' ? 'font-semibold text-destructive' : 'font-medium'}>{result && structureLabels[result]}</div>
            <p className="text-xs text-muted-foreground">{detail}</p>
          </td>
        </tr>
      })}</tbody>
    </table>
  </div>
}

function StandardsComparison({ job }: { job: Job }) {
  const report = job.standardsReview
  if (!report) return null
  const rows = report.rows ?? []
  const differences = rows.filter((row) => row.result !== 'MATCH' && row.result !== 'ENVELOPE')
  const otherRows = rows.filter((row) => row.result === 'MATCH' || row.result === 'ENVELOPE')
  return <section aria-labelledby="mig-standards-title" className="space-y-3 border-t pt-4">
    <div>
      <h3 id="mig-standards-title" className="font-semibold">Official standard comparison</h3>
      {report.source && <a href={report.source} target="_blank" rel="noreferrer" className="text-sm text-muted-foreground underline underline-offset-4">
        UNECE {job.target?.release} {job.target?.messageType} segment table
      </a>}
    </div>
    {report.state === 'DIRECTORY_UNAVAILABLE' && <p className="text-sm text-muted-foreground">
      The comparison currently covers EDIFACT D96A ORDERS.
    </p>}
    {report.state === 'GUIDE_RELEASE_MISMATCH' && <p className="text-sm text-muted-foreground">
      The primary guide names another release. Confirm the release to compare its rows.
    </p>}
    {report.state === 'COMPARED' && <p className="text-sm text-muted-foreground">
      {rows.length ? 'Checks usage and repeat limits for explicit segment and group declarations.' : 'No segment or group table declarations were recognised in the extracted text.'}
    </p>}
    {differences.length > 0 && <StructureRows rows={differences} job={job} />}
    {otherRows.length > 0 && <details>
      <summary className="cursor-pointer text-sm">Other extracted rows ({otherRows.length})</summary>
      <div className="mt-2"><StructureRows rows={otherRows} job={job} /></div>
    </details>}
    {Boolean(report.omittedRows) && <p className="text-sm text-muted-foreground">
      {report.omittedRows} additional rows exceed the comparison limit.
    </p>}
  </section>
}

function Fixture({ fixture, target }: { fixture?: FixtureResult; target?: Coordinates }) {
  if (!fixture) return <p className="text-sm text-muted-foreground">Preview pending.</p>
  const paired = fixture.check === 'UPLOADED_EXPECTED'
  const withReference = fixture.check === 'CATALOGUE_STRUCTURE_WITH_REFERENCE' || fixture.check === 'UPLOADED_STRUCTURE_WITH_REFERENCE'
  const uploadedInput = fixture.check === 'UPLOADED_STRUCTURE' || fixture.check === 'UPLOADED_STRUCTURE_WITH_REFERENCE'
  const targetRules = target?.direction === 'INBOUND' ? 'UBL 2.1 schema' : [target?.standard, target?.release, 'validator'].filter(Boolean).join(' ')
  const unavailable = fixture.findings?.some((finding) => finding.code === 'PREVIEW_UNAVAILABLE' || finding.code === 'RUNTIME_CHANGED')
  const label = paired
    ? fixture.expectedMatch === true ? 'Matched the uploaded expected output' : fixture.expectedMatch === false ? 'Expected output differs' : 'Expected output check pending'
    : unavailable ? 'Mapping preview unavailable' : uploadedInput ? 'Uploaded input · structure checks' : 'Catalogue input · structure checks'
  return <div className="space-y-2">
    <p className="text-sm font-medium">{label}</p>
    {!paired && !unavailable && <p className="text-xs text-muted-foreground">Output validation uses the {targetRules}.</p>}
    <Findings findings={fixture.findings} />
    {withReference && <div className="space-y-1 border-l-2 pl-3">
      <p className="text-sm font-medium">Reference output · advisory</p>
      <p className="text-xs text-muted-foreground">
        {fixture.referenceFindings?.length ? 'The reference has format findings. It may be a partial example.'
          : `The reference passed the ${targetRules} checks.`}
      </p>
      <Findings findings={fixture.referenceFindings} />
    </div>}
    {fixture.generatedChecks?.map((generated, index) => <p className="text-xs text-muted-foreground" key={index}>{generated.name} · {generated.passed ? 'passed' : 'failed'}</p>)}
  </div>
}

function RuleEvidence({ rule, sections }: { rule?: Rule; sections: Map<string, Section> }) {
  if (!rule) return <p className="text-sm text-muted-foreground">No proposed rule</p>
  return <div className="space-y-2 text-sm">
    <p>{rule.requirement}</p>
    {rule.operation?.targetPointer && <>
      <p className="break-all font-mono text-xs">{rule.operation.operation} → {rule.operation.targetPointer}</p>
      <p className="break-all font-mono text-xs text-muted-foreground">{rule.operation.sourcePointers?.join(' · ')}</p>
    </>}
    {rule.facts && <p className="text-xs text-muted-foreground">
      {[rule.facts.presence, rule.facts.segmentOrGroup, rule.facts.qualifier, rule.facts.cardinality,
        rule.facts.valueType, rule.facts.length, rule.facts.condition, ...(rule.facts.codes ?? [])].filter(Boolean).join(' · ')}
    </p>}
    {rule.profileRule?.fieldKey && <p className="break-all font-mono text-xs">Partner profile · {rule.profileRule.fieldKey}</p>}
    {rule.gap && <p className="text-destructive">{rule.gap}</p>}
    {rule.suggestedLookupRows?.length ? <details><summary>Proposed lookup entries</summary><pre className="overflow-auto whitespace-pre-wrap text-xs">{JSON.stringify(rule.suggestedLookupRows, null, 2)}</pre></details> : null}
    {rule.citations?.map((anchor) => <details key={anchor}>
      <summary className="cursor-pointer break-all text-xs text-muted-foreground">{anchor}</summary>
      <blockquote className="mt-1 whitespace-pre-wrap border-l-2 pl-3 text-sm">{sections.get(anchor)?.excerpt ?? 'Source text unavailable.'}</blockquote>
    </details>)}
  </div>
}

function Review({ job, busy, onReview }: { job: Job; busy: boolean; onReview: (body: ReviewRequest) => void }) {
  const [localError, setLocalError] = useState('')
  const sections = new Map(job.sections?.flatMap((section) => section.anchor ? [[section.anchor, section]] : []))
  const supplied = new Map(job.selections?.map((selection) => [selection.id, selection]))
  const superseding = new Map(job.supersessions?.map((decision) => [decision.differenceId, decision]))
  const aliases = [...new Set(job.differences?.flatMap((difference) =>
    [difference.sonnet?.operation?.tableLookup?.alias, difference.nova?.operation?.tableLookup?.alias, difference.jev?.operation?.tableLookup?.alias].filter((alias): alias is string => Boolean(alias)),
  ))]
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    try {
      const selections: Selection[] = []
      const supersessions: Supersession[] = []
      for (const difference of job.differences ?? []) {
        if (!difference.id) continue
        const candidate = field(form, `choose-${difference.id}`)
        if (candidate !== 'SONNET' && candidate !== 'NOVA' && candidate !== 'JEV') continue
        const reason = field(form, `reason-${difference.id}`)
        const correction = field(form, `override-${difference.id}`)
        let override: MappingNode | undefined
        if (correction) override = { ...JSON.parse(correction), id: crypto.randomUUID() }
        selections.push({ id: difference.id, candidate, reason, override })
        const prevailingAnchor = field(form, `prevail-${difference.id}`)
        if (prevailingAnchor) supersessions.push({ differenceId: difference.id, prevailingAnchor, reason })
      }
      const referenceTableRevisions = Object.fromEntries(aliases.flatMap((alias) => {
        const value = field(form, `binding-${alias}`)
        return value ? [[alias, value]] : []
      }))
      setLocalError('')
      onReview({ selections, supersessions, referenceTableRevisions })
    } catch {
      setLocalError('Enter the corrected operation as valid JSON.')
    }
  }
  return <form onSubmit={submit} className="space-y-4 rounded-lg border bg-card p-5">
    <div><h2 className="font-semibold">Review mapping rules</h2><p className="text-sm text-muted-foreground">Choose an operation and record why for any difference. Confirm the prevailing source when editions conflict.</p></div>
    {job.differences?.map((difference) => difference.id && <RuleDecision key={difference.id} difference={difference} sections={sections}
      initial={supplied.get(difference.id)} priorSupersession={superseding.get(difference.id)} modelMode={job.modelMode} modelIds={job.modelIds} />)}
    {aliases.map((alias) => <Field key={alias} label={`Approved reference table revision for ${alias}`}><Input name={`binding-${alias}`} defaultValue={job.referenceTableRevisions?.[alias]} placeholder="Revision UUID" /></Field>)}
    {localError && <AdminNotice>{localError}</AdminNotice>}
    {job.reviewedFixture && <div className="rounded-md bg-muted/40 p-4"><Fixture fixture={job.reviewedFixture} target={job.target} /></div>}
    <Button type="submit" disabled={busy || !job.differences?.length}>Revalidate reviewed rules</Button>
  </form>
}

function RuleDecision({ difference, sections, initial, priorSupersession, modelMode, modelIds }: {
  difference: Difference; sections: Map<string, Section>; initial?: Selection; priorSupersession?: Supersession; modelMode?: Job['modelMode']; modelIds?: Job['modelIds']
}) {
  const modelLabels = labelsForMode(modelMode, modelIds)
  const secondaryCandidate = modelMode === 'BEDROCK_AND_JEV' ? 'JEV' : 'NOVA'
  const second = secondaryCandidate === 'JEV' ? difference.jev : difference.nova
  const id = difference.id
  const anchors = [...new Set([...(difference.sonnet?.citations ?? []), ...(second?.citations ?? [])])]
  const sources = new Set(anchors.map((anchor) => anchor.split('/')[1]))
  const showDecision = !difference.preselected || Boolean(initial?.override)
  return <fieldset className="space-y-3 rounded-md border p-4">
    <legend className="max-w-full break-all px-1 text-sm font-medium">{difference.sonnet?.operation?.targetPointer ?? second?.operation?.targetPointer ?? 'Guide requirement'}</legend>
    {difference.preselected && <p className="text-xs font-medium text-muted-foreground">Candidates agree and checks passed</p>}
    <div className="grid gap-4 sm:grid-cols-2">
      <div className="space-y-1"><h3 className="text-xs font-semibold">{modelLabels.SONNET}</h3><RuleEvidence rule={difference.sonnet} sections={sections} /></div>
      <div className="space-y-1"><h3 className="text-xs font-semibold">{modelLabels[secondaryCandidate]}</h3><RuleEvidence rule={second} sections={sections} /></div>
    </div>
    {showDecision ? <div className="grid gap-3 sm:grid-cols-2">
      <Field label="Use candidate"><Select name={`choose-${id}`} defaultValue={initial?.candidate ?? ''}>
        <option value="">Unresolved</option>
        {difference.sonnet && <option value="SONNET">{modelLabels.SONNET}</option>}
        {second && <option value={secondaryCandidate}>{modelLabels[secondaryCandidate]}</option>}
      </Select></Field>
      <Field label="Decision reason"><Input name={`reason-${id}`} defaultValue={initial?.reason} maxLength={280} /></Field>
      {sources.size > 1 && <Field label="Prevailing citation"><Select name={`prevail-${id}`} defaultValue={priorSupersession?.prevailingAnchor ?? ''}>
        <option value="">Select cited rule</option>{anchors.map((anchor) => <option key={anchor}>{anchor}</option>)}
      </Select></Field>}
      {(difference.sonnet?.correctionAllowed || second?.correctionAllowed) && <details className="sm:col-span-2">
        <summary className="cursor-pointer text-sm">Correct the mapping operation</summary>
        <Field label="Operation JSON"><textarea name={`override-${id}`} defaultValue={initial?.override && JSON.stringify(initial.override, null, 2)} rows={4}
          className="w-full rounded-md border bg-background p-2 font-mono text-xs"
          placeholder='{"operation":"COPY","sourcePointers":["/source"],"targetPointer":"/destination"}' /></Field>
      </details>}
    </div> : <input type="hidden" name={`choose-${id}`} value="SONNET" />}
  </fieldset>
}

function JobSummary({ job, busy, action, activeMode, activeModels }: { job: Job; busy: boolean; activeMode?: Job['modelMode']; activeModels?: Job['modelIds']; action: (action: string, body?: DraftRequest) => void }) {
  const runnerChanged = activeMode && (job.modelMode !== activeMode
    || Object.entries(job.modelIds ?? {}).some(([candidate, id]) => activeModels?.[candidate] !== id))
  const uploadsRetained = Boolean(job.uploadsExpireAt && Date.parse(job.uploadsExpireAt) > Date.now())
  const [name, setName] = useState('')
  const hasProfileRules = job.selections?.some((selection) => job.differences?.some((difference) =>
    difference.id === selection.id && (selection.candidate === 'SONNET' ? difference.sonnet : selection.candidate === 'JEV' ? difference.jev : difference.nova)?.profileRule,
  ))
  return <section aria-labelledby="mig-job-title" className="space-y-4 rounded-lg border bg-card p-5">
    <div><h2 id="mig-job-title" aria-live="polite" className="font-semibold">{job.state && labels[job.state]}</h2>
      <p className="text-sm text-muted-foreground">{job.target?.direction} · {job.target?.standard} {job.target?.release} · {job.target?.messageType}</p></div>
    {job.rerunOf && <Link className="text-sm underline underline-offset-4" to={`${migPath}?job=${job.rerunOf}`}>View original run</Link>}
    <dl className="grid grid-cols-3 gap-3 text-sm">
      {[['Estimated credits', job.estimatedCredits], ['Reserved credits', job.reservedCredits], ['Settled credits', job.settledCredits]].map(([label, value]) =>
        <div key={label}><dt className="text-muted-foreground">{label}</dt><dd className="font-semibold tabular-nums">{value ?? 0}</dd></div>,
      )}
    </dl>
    <Findings findings={job.findings} />
    {runnerChanged && <p className="text-sm text-muted-foreground">
      Rerun with the same files to generate with the current model runner.
    </p>}
    <div className="flex flex-wrap gap-2">
      {!runnerChanged && job.state === 'ESTIMATED' && <Button disabled={busy} onClick={() => action('start')}>Reserve credits and generate</Button>}
      {!runnerChanged && job.state === 'INCOMPLETE' && <Button disabled={busy} onClick={() => action('retry')}>Reserve credits and retry</Button>}
      {(['ESTIMATED', 'QUEUED', 'RUNNING', 'INCOMPLETE'] as Job['state'][]).includes(job.state) &&
        <Button disabled={busy} variant="outline" onClick={() => action('cancel')}>Cancel job</Button>}
      {uploadsRetained && <Button disabled={busy} variant="outline" onClick={() => action('rerun')}>Rerun with same files</Button>}
      {job.draftId && job.state === 'IMPORTED' && <Button asChild><Link to={`${rulesPath}/${job.draftId}`}>Open editable draft</Link></Button>}
    </div>
    {(job.state === 'READY' || job.state === 'IMPORTING') && <form className="flex flex-wrap items-end gap-3" onSubmit={(event) => {
      event.preventDefault()
      action('draft', { name: name.trim() || 'MIG mapping draft', includePartnerProfile: Boolean(hasProfileRules) })
    }}>
      {job.state === 'READY' && <Field label="Draft name"><Input required maxLength={160} value={name} onChange={(event) => setName(event.target.value)} /></Field>}
      <Button disabled={busy} type="submit">{job.state === 'IMPORTING' ? 'Resume draft creation' : hasProfileRules ? 'Create mapping and partner profile drafts' : 'Create editable draft'}</Button>
    </form>}
    {job.uploadsExpireAt && !uploadsRetained && <p className="text-sm text-muted-foreground">The original uploads have expired. Upload fresh copies to rerun.</p>}
    <StandardsComparison job={job} />
    {job.profileDraftId && job.state === 'IMPORTED' && <p className="text-sm text-muted-foreground">Partner profile draft {job.profileDraftId}</p>}
    <details><summary className="cursor-pointer text-sm text-muted-foreground">Evidence and file details</summary>
      <ul className="mt-2 text-sm">{job.uploads?.map((upload) => <li key={upload.id}>{upload.name}{upload.edition ? ` · ${upload.edition}` : ''} <span className="text-xs text-muted-foreground">{upload.role}</span></li>)}</ul>
      <p className="mt-2 break-all font-mono text-xs text-muted-foreground">{job.evidenceDigest}</p>
      {job.manifest?.version && <ul className="mt-2 space-y-1 text-xs text-muted-foreground">
        <li>Evidence format {job.manifest.version}</li>
        {job.manifest.ublSchemaDigest && <li className="break-all">UBL schema {job.manifest.ublSchemaDigest}</li>}
        {job.manifest.runtimeDigest && <li className="break-all">Runtime {job.manifest.runtimeDigest}</li>}
        {job.manifest.revisions?.map((revision) => <li key={revision.id} className="break-all">{revision.category} {revision.id} · revision {revision.revision}</li>)}
      </ul>}
    </details>
  </section>
}

export function MigGenerationPage() {
  const [params, setParams] = useSearchParams()
  const jobId = params.get('job')
  const [availability, setAvailability] = useState<Availability>()
  const [job, setJob] = useState<Job>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const current = job?.id === jobId ? job : undefined
  const validJobId = jobId !== null && /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(jobId)
  const polling = current === undefined || activeStates.has(current.state)

  useEffect(() => {
    const controller = new AbortController()
    void readMig<Availability>(migApi, { signal: controller.signal }).then(setAvailability).catch((issue: Error) => {
      if (!controller.signal.aborted) setError(issue.message)
    })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!validJobId || !availability?.enabled || !polling) return
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout>
    async function refresh() {
      try {
        const next = await readMig<Job>(`${migApi}/jobs/${jobId}`, { signal: controller.signal })
        if (controller.signal.aborted) return
        setJob(next)
        if (activeStates.has(next.state)) timer = setTimeout(refresh, 2000)
      } catch (issue) {
        if (!controller.signal.aborted) setError(issue instanceof Error ? issue.message : 'Could not load the job.')
      }
    }
    void refresh()
    return () => { controller.abort(); clearTimeout(timer) }
  }, [availability?.enabled, jobId, polling, validJobId])

  async function run(action: () => Promise<Job>) {
    setBusy(true)
    setError('')
    try {
      const next = await action()
      setJob(next)
      if (next.id && next.id !== jobId) setParams({ job: next.id })
    } catch (issue) {
      setError(issue instanceof Error ? issue.message : 'The MIG request failed.')
    } finally {
      setBusy(false)
    }
  }

  return <AdminPage className="max-w-5xl">
    <AdminPageHeader title="Generate from a MIG" description="Turn a Message Implementation Guide into a cited document-rule draft."
      actions={<Button asChild variant="outline"><Link to={rulesPath}><ArrowLeft className="mr-2 h-4 w-4" />Document rules</Link></Button>} />
    {error && <AdminNotice>{error}</AdminNotice>}
    {availability && !availability.enabled && <AdminNotice>MIG generation is unavailable in this deployment.</AdminNotice>}
    {availability?.modelMode === 'LOCAL_FIXTURE' && <AdminNotice>Local fixture mode processes synthetic guides with RULE declarations. Configure London Bedrock to process a real MIG.</AdminNotice>}
    {availability?.modelMode === 'LONDON_BEDROCK' && <p className="text-sm text-muted-foreground">
      Bedrock runs {labelsForMode(availability.modelMode, availability.modelIds).SONNET} and Amazon Nova Lite.
    </p>}
    {availability?.modelMode === 'BEDROCK_AND_JEV' && <p className="text-sm text-muted-foreground">
      Comparing {labelsForMode(availability.modelMode, availability.modelIds).SONNET} on Bedrock with TypeSafe Jev.
    </p>}
    {!availability && !error && <p role="status" className="text-sm text-muted-foreground">Checking access…</p>}
    {availability?.enabled && !jobId && <>
      {availability.demoJobId && <Button asChild variant="outline" className="mb-4"><Link to={`${migPath}?job=${availability.demoJobId}`}><FileText className="mr-2 h-4 w-4" />Open local demo job</Link></Button>}
      <GuideIntake busy={busy} onEstimate={(event) => {
        event.preventDefault()
        const form = new FormData(event.currentTarget)
        void run(async () => postMig<Job>(migApi, await uploadIntake(form)))
      }} />
    </>}
    {availability?.enabled && jobId && !validJobId && <AdminNotice>The job identifier is invalid.</AdminNotice>}
    {availability?.enabled && validJobId && !current && !error && <p role="status" className="text-sm text-muted-foreground">Loading job…</p>}
    {current && <div className="space-y-5">
      <div className="flex justify-end"><Button asChild variant="ghost"><Link to={migPath}>Generate another mapping</Link></Button></div>
      <JobSummary key={`summary-${current.id}`} job={current} busy={busy} activeMode={availability?.modelMode} activeModels={availability?.modelIds} action={(action, body) => void run(() => postMig<Job>(`${migApi}/jobs/${current.id}?action=${action}`, body))} />
      {current.proposals?.length ? <div className="grid gap-4 sm:grid-cols-2">
        {current.proposals.map((proposal, index) => <section key={proposal.candidate ?? index} className="space-y-2 rounded-lg border bg-card p-5">
          <h2 className="font-semibold">{proposal.candidate && labelsForMode(current.modelMode, current.modelIds)[proposal.candidate]}</h2>
          <p className="text-sm text-muted-foreground">
            {proposal.rules?.filter((rule) => rule.operation || rule.profileRule).length ?? 0} compiled rules · {proposal.rules?.filter((rule) => rule.citations?.length).length ?? 0} cited requirements · {current.invocations?.filter((item) => item.candidate === proposal.candidate).reduce((total, item) => total + (item.settledCredits ?? 0), 0).toFixed(4) ?? '0'} settled credits
          </p>
          <Findings findings={proposal.findings} /><Fixture fixture={proposal.fixture} target={current.target} />
        </section>)}
      </div> : null}
      {(current.state === 'REVIEW' || current.state === 'READY') && <Review key={`review-${current.id}`} job={current} busy={busy}
        onReview={(body) => void run(() => postMig<Job>(`${migApi}/jobs/${current.id}?action=review`, body))} />}
    </div>}
  </AdminPage>
}

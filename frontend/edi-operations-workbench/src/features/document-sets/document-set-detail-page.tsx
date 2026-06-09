import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  projectDocumentSetEvidence,
  validateAddDocumentForm,
  validateAddVersionForm,
  validateDerivativeSourceVersion
} from '@/features/document-sets/document-workflow'
import { ErrorState, LoadingState } from '@/components/workbench-states'
import { useIntegration } from '@/integration/integration-provider'
import { canSubmitWhileDisconnected } from '@/integration/request-lifecycle'
import type { AddDocumentRequest, CreateDerivativeRequest, DocumentResponse, DocumentSetResponse, DocumentType, Format } from '@/integration/documents-api-client'
import { encodeTextToBase64 } from '@/lib/base64'

const documentTypes = ['INVOICE', 'ORDER', 'DESPATCH_ADVICE', 'RECEIPT_ADVICE', 'REMITTANCE_ADVICE', 'APPLICATION_RESPONSE'] as const
const formats = ['XML', 'JSON', 'PDF', 'EDI'] as const
const fieldClassName = 'mt-2 w-full rounded-xl border bg-background px-3 py-2 text-sm outline-none ring-ring focus:ring-2'

type DetailData = {
  documentSet: DocumentSetResponse
  documents: DocumentResponse[]
}

export function DocumentSetDetailPage() {
  const { setId = '' } = useParams<{ setId: string }>()
  const { contractState, runRequest } = useIntegration()
  const [data, setData] = useState<DetailData>()
  const [error, setError] = useState<string>()
  const [loading, setLoading] = useState(true)
  const [addDocumentErrors, setAddDocumentErrors] = useState<string[]>([])
  const [addDocumentResult, setAddDocumentResult] = useState<string>()
  const [submittingAddDocument, setSubmittingAddDocument] = useState(false)
  const [form, setForm] = useState({ documentType: 'ORDER', schemaId: '', schemaVersion: '', contentText: '', createdBy: '', relatedDocumentId: '' })

  async function loadDetail() {
    setLoading(true)
    setError(undefined)
    const result = await runRequest(`documentSetDetail:${setId}`, async (client) => {
      const documentSet = await client.getDocumentSet(setId)
      const documents = await Promise.all(documentSet.documents.map((document) => client.getDocument(setId, document.id)))
      return { documentSet, documents }
    })
    setLoading(false)

    if (result.status !== 'succeeded') {
      setError(result.status === 'failed' ? result.reason : 'The document-set detail request did not complete.')
      return
    }

    setData(result.data)
  }

  useEffect(() => {
    if (contractState.kind === 'connected' || contractState.kind === 'request-failed') {
      void loadDetail()
    }
  }, [contractState.kind, setId])

  async function submitDocument(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors = validateAddDocumentForm(form)
    setAddDocumentErrors(errors)
    setAddDocumentResult(undefined)

    if (errors.length > 0) {
      return
    }

    setSubmittingAddDocument(true)
    const request: AddDocumentRequest = {
      documentType: form.documentType as DocumentType,
      schemaId: form.schemaId.trim(),
      schemaVersion: form.schemaVersion.trim(),
      content: encodeTextToBase64(form.contentText),
      createdBy: form.createdBy.trim(),
      relatedDocumentId: form.relatedDocumentId.trim() || undefined
    }
    const result = await runRequest(`addDocument:${setId}`, (client) => client.addDocument(setId, request))
    setSubmittingAddDocument(false)

    if (result.status !== 'succeeded') {
      setAddDocumentErrors([result.status === 'failed' ? result.reason : 'The add-document request did not complete.'])
      return
    }

    setAddDocumentResult(`${result.data.id} added with schema ${result.data.schemaRef.schemaId}@${result.data.schemaRef.version}.`)
    await loadDetail()
  }

  if (contractState.kind === 'loading-contract' || loading) {
    return <LoadingState title="Loading document-set detail" description="Fetching live set, document, and current-version evidence." />
  }

  if (error || !data) {
    return <ErrorState title="Document-set detail is unavailable" description="The live backend response could not be projected into this workflow view." details={error} onRetry={() => void loadDetail()} />
  }

  const evidence = projectDocumentSetEvidence(data.documentSet, data.documents)

  return (
    <div className="space-y-6">
      <Card className="border-border/70 bg-card/95 shadow-sm">
        <CardHeader>
          <Badge className="w-fit">Document set detail</Badge>
          <CardTitle>{data.documentSet.id}</CardTitle>
          <CardDescription>
            Created by {data.documentSet.createdBy} on {new Date(data.documentSet.createdAt).toLocaleString()}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-muted-foreground">
          <p>{evidence.documentCount} document detail record(s) loaded for this set.</p>
          {data.documentSet.metadata ? <pre className="rounded-2xl bg-muted p-4 text-xs">{JSON.stringify(data.documentSet.metadata, null, 2)}</pre> : null}
        </CardContent>
      </Card>

      <Card className="border-border/70 bg-card/95 shadow-sm">
        <CardHeader>
          <Badge className="w-fit">Workflow view</Badge>
          <CardTitle>Add document</CardTitle>
          <CardDescription>Incomplete content is blocked locally before the live backend call is sent.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4 xl:grid-cols-2" onSubmit={submitDocument}>
            <label className="text-sm font-medium text-foreground">Document type<select className={fieldClassName} value={form.documentType} onChange={(event) => setForm({ ...form, documentType: event.target.value })}>{documentTypes.map((type) => <option key={type} value={type}>{type}</option>)}</select></label>
            <label className="text-sm font-medium text-foreground">Created by<input className={fieldClassName} value={form.createdBy} onChange={(event) => setForm({ ...form, createdBy: event.target.value })} /></label>
            <label className="text-sm font-medium text-foreground">Schema ID<input className={fieldClassName} value={form.schemaId} onChange={(event) => setForm({ ...form, schemaId: event.target.value })} /></label>
            <label className="text-sm font-medium text-foreground">Schema version<input className={fieldClassName} value={form.schemaVersion} onChange={(event) => setForm({ ...form, schemaVersion: event.target.value })} /></label>
            <label className="xl:col-span-2 text-sm font-medium text-foreground">Related document ID (optional)<input className={fieldClassName} value={form.relatedDocumentId} onChange={(event) => setForm({ ...form, relatedDocumentId: event.target.value })} /></label>
            <label className="xl:col-span-2 text-sm font-medium text-foreground">Document content<textarea className={`${fieldClassName} min-h-32`} value={form.contentText} onChange={(event) => setForm({ ...form, contentText: event.target.value })} /></label>
            <div className="xl:col-span-2 space-y-4">
              {!canSubmitWhileDisconnected(contractState) ? <p className="text-sm text-destructive">Live submission is blocked until the backend connection recovers.</p> : null}
              {addDocumentErrors.length > 0 ? <ResultList title="Submission needs attention" items={addDocumentErrors} /> : null}
              {addDocumentResult ? <p className="rounded-2xl border border-primary/20 bg-primary/5 p-4 text-sm text-foreground">{addDocumentResult}</p> : null}
              <Button disabled={!canSubmitWhileDisconnected(contractState) || submittingAddDocument} type="submit">{submittingAddDocument ? 'Submitting…' : 'Add document'}</Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <div className="grid gap-4">
        {data.documents.map((document) => (
          <DocumentWorkflowCard key={document.id} document={document} onRefresh={loadDetail} setId={setId} />
        ))}
      </div>
    </div>
  )
}

function DocumentWorkflowCard(props: { setId: string; document: DocumentResponse; onRefresh: () => Promise<void> }) {
  const { contractState, runRequest } = useIntegration()
  const [versionForm, setVersionForm] = useState({ contentText: '', createdBy: '' })
  const [derivativeForm, setDerivativeForm] = useState({ sourceVersionNumber: `${props.document.currentVersion.versionNumber}`, targetFormat: 'JSON' })
  const [messages, setMessages] = useState<string[]>([])

  async function addVersion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors = validateAddVersionForm(versionForm)
    if (errors.length > 0) {
      setMessages(errors)
      return
    }

    const result = await runRequest(`addVersion:${props.document.id}`, (client) =>
      client.addVersion(props.setId, props.document.id, { content: encodeTextToBase64(versionForm.contentText), createdBy: versionForm.createdBy.trim() })
    )
    setMessages(
      result.status === 'succeeded'
        ? [`Version ${result.data.versionNumber} created as ${result.data.id}.`]
        : [result.status === 'failed' ? result.reason : 'The add-version request did not complete.']
    )
    if (result.status === 'succeeded') await props.onRefresh()
  }

  async function createDerivative(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors = validateDerivativeSourceVersion(derivativeForm.sourceVersionNumber)
    if (errors.length > 0) {
      setMessages(errors)
      return
    }

    const sourceVersionNumber = Number(derivativeForm.sourceVersionNumber)
    const request: CreateDerivativeRequest = { sourceVersionNumber, targetFormat: derivativeForm.targetFormat as Format }
    const result = await runRequest(`createDerivative:${props.document.id}`, (client) => client.createDerivative(props.setId, props.document.id, request))
    setMessages(
      result.status === 'succeeded'
        ? [`Derivative ${result.data.id} created in ${result.data.targetFormat} format.`]
        : [result.status === 'failed' ? result.reason : 'The derivative request did not complete.']
    )
    if (result.status === 'succeeded') await props.onRefresh()
  }

  return (
    <Card className="border-border/70 bg-card/95 shadow-sm">
      <CardHeader>
        <CardTitle>{props.document.type}</CardTitle>
        <CardDescription>
          Schema {props.document.schemaRef.schemaId}@{props.document.schemaRef.version} · current version {props.document.currentVersion.versionNumber}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-sm text-muted-foreground">
        <div className="flex flex-wrap gap-2">
          <Badge className="border-border bg-transparent">{props.document.currentVersion.format}</Badge>
          <Badge className="border-border bg-transparent">{props.document.currentVersion.parseStatus ?? 'No parse status'}</Badge>
          <Badge className="border-border bg-transparent">{props.document.derivatives.length} derivative(s)</Badge>
        </div>
        <p>Current version ID: {props.document.currentVersion.id}</p>
        {props.document.derivatives.length > 0 ? <pre className="rounded-2xl bg-muted p-4 text-xs">{JSON.stringify(props.document.derivatives, null, 2)}</pre> : null}
        <div className="flex flex-wrap gap-2">
          <Button asChild size="sm" variant="outline"><Link to={`/document-sets/${props.setId}/documents/${props.document.id}/versions/${props.document.currentVersion.versionNumber}`}>Inspect current version</Link></Button>
        </div>
        <div className="grid gap-4 xl:grid-cols-2">
          <form className="space-y-3 rounded-2xl border bg-background/70 p-4" onSubmit={addVersion}>
            <p className="font-medium text-foreground">Add version</p>
            <label className="text-sm font-medium text-foreground">Created by<input className={fieldClassName} value={versionForm.createdBy} onChange={(event) => setVersionForm({ ...versionForm, createdBy: event.target.value })} /></label>
            <label className="text-sm font-medium text-foreground">Version content<textarea className={`${fieldClassName} min-h-24`} value={versionForm.contentText} onChange={(event) => setVersionForm({ ...versionForm, contentText: event.target.value })} /></label>
            <Button disabled={!canSubmitWhileDisconnected(contractState)} size="sm" type="submit">Add version</Button>
          </form>
          <form className="space-y-3 rounded-2xl border bg-background/70 p-4" onSubmit={createDerivative}>
            <p className="font-medium text-foreground">Create derivative</p>
            <label className="text-sm font-medium text-foreground">Source version number<input className={fieldClassName} value={derivativeForm.sourceVersionNumber} onChange={(event) => setDerivativeForm({ ...derivativeForm, sourceVersionNumber: event.target.value })} /></label>
            <label className="text-sm font-medium text-foreground">Target format<select className={fieldClassName} value={derivativeForm.targetFormat} onChange={(event) => setDerivativeForm({ ...derivativeForm, targetFormat: event.target.value })}>{formats.map((format) => <option key={format} value={format}>{format}</option>)}</select></label>
            <Button disabled={!canSubmitWhileDisconnected(contractState)} size="sm" type="submit">Create derivative</Button>
          </form>
        </div>
        {messages.length > 0 ? <ResultList title="Live workflow response" items={messages} /> : null}
      </CardContent>
    </Card>
  )
}

function ResultList(props: { title: string; items: string[] }) {
  return (
    <div className="rounded-2xl border border-border bg-background/70 p-4 text-sm">
      <p className="font-medium text-foreground">{props.title}</p>
      <ul className="mt-2 space-y-1 text-muted-foreground">{props.items.map((item) => <li key={item}>• {item}</li>)}</ul>
    </div>
  )
}

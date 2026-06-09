import { Link } from 'react-router-dom'
import { useState, type FormEvent } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState, ErrorState, LoadingState } from '@/components/workbench-states'
import { useDocumentSetCatalogue } from '@/features/document-sets/use-document-set-catalogue'
import type { DocumentType } from '@/integration/documents-api-client'
import { useIntegration } from '@/integration/integration-provider'
import { canSubmitWhileDisconnected } from '@/integration/request-lifecycle'
import { encodeTextToBase64 } from '@/lib/base64'

const documentTypes = ['INVOICE', 'ORDER', 'DESPATCH_ADVICE', 'RECEIPT_ADVICE', 'REMITTANCE_ADVICE', 'APPLICATION_RESPONSE'] as const
const fieldClassName = 'mt-2 w-full rounded-xl border bg-background px-3 py-2 text-sm outline-none ring-ring focus:ring-2'

export function DocumentSetListPage() {
  const { contractState, runRequest } = useIntegration()
  const { goToNextPage, goToPreviousPage, hasNextPage, hasPreviousPage, page, refresh, requestState } = useDocumentSetCatalogue(12)
  const [form, setForm] = useState({ documentType: 'ORDER', schemaId: '', schemaVersion: '', contentText: '', createdBy: '', metadataText: '' })
  const [errors, setErrors] = useState<string[]>([])
  const [resultMessage, setResultMessage] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  if (contractState.kind === 'loading-contract') {
    return <LoadingState title="Loading document-set workflows" description="Fetching the live catalogue before list and intake views render." />
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors = [
      form.schemaId.trim() ? undefined : 'Schema ID is required.',
      form.schemaVersion.trim() ? undefined : 'Schema version is required.',
      form.contentText.trim() ? undefined : 'Document content is required.',
      form.createdBy.trim() ? undefined : 'Created by is required.'
    ].filter(Boolean) as string[]
    setErrors(nextErrors)
    setResultMessage(undefined)

    if (nextErrors.length > 0) {
      return
    }

    setSubmitting(true)
    const result = await runRequest('createDocumentSetWorkflow', (client) =>
      client.createDocumentSet({
        documentType: form.documentType as DocumentType,
        schemaId: form.schemaId.trim(),
        schemaVersion: form.schemaVersion.trim(),
        content: encodeTextToBase64(form.contentText),
        createdBy: form.createdBy.trim(),
        metadata: parseMetadata(form.metadataText)
      })
    )
    setSubmitting(false)

    if (result.status !== 'succeeded') {
      setErrors([result.status === 'failed' ? result.reason : 'The document-set request did not complete.'])
      return
    }

    setResultMessage(`${result.data.id} created for ${result.data.createdBy} with ${result.data.documents.length} document summary record(s).`)
    void refresh()
  }

  return (
    <div className="space-y-6">
      <Card className="border-border/70 bg-card/95 shadow-sm">
        <CardHeader>
          <Badge className="w-fit">Workflow view</Badge>
          <CardTitle>Create document set</CardTitle>
          <CardDescription>Initial document content is base64-encoded only at submission time so the entered payload stays readable in the browser.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4 xl:grid-cols-2" onSubmit={submit}>
            <label className="text-sm font-medium text-foreground">
              Document type
              <select className={fieldClassName} value={form.documentType} onChange={(event) => setForm({ ...form, documentType: event.target.value })}>
                {documentTypes.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-sm font-medium text-foreground">
              Created by
              <input className={fieldClassName} value={form.createdBy} onChange={(event) => setForm({ ...form, createdBy: event.target.value })} />
            </label>
            <label className="text-sm font-medium text-foreground">
              Schema ID
              <input className={fieldClassName} value={form.schemaId} onChange={(event) => setForm({ ...form, schemaId: event.target.value })} />
            </label>
            <label className="text-sm font-medium text-foreground">
              Schema version
              <input className={fieldClassName} value={form.schemaVersion} onChange={(event) => setForm({ ...form, schemaVersion: event.target.value })} />
            </label>
            <label className="xl:col-span-2 text-sm font-medium text-foreground">
              Metadata (optional, one key=value pair per line)
              <textarea className={`${fieldClassName} min-h-24`} value={form.metadataText} onChange={(event) => setForm({ ...form, metadataText: event.target.value })} />
            </label>
            <label className="xl:col-span-2 text-sm font-medium text-foreground">
              Document content
              <textarea className={`${fieldClassName} min-h-40`} value={form.contentText} onChange={(event) => setForm({ ...form, contentText: event.target.value })} />
            </label>
            <div className="xl:col-span-2 space-y-4">
              {!canSubmitWhileDisconnected(contractState) ? <p className="text-sm text-destructive">Live submission is blocked until the backend connection recovers.</p> : null}
              {errors.length > 0 ? <ResultList title="Submission needs attention" items={errors} /> : null}
              {resultMessage ? <p className="rounded-2xl border border-primary/20 bg-primary/5 p-4 text-sm text-foreground">{resultMessage}</p> : null}
              <Button disabled={!canSubmitWhileDisconnected(contractState) || submitting} type="submit">
                {submitting ? 'Submitting…' : 'Create document set'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {requestState.status === 'pending' || requestState.status === 'idle' ? (
        <LoadingState title="Loading document sets" description="Fetching the paginated document-set catalogue from the live backend." />
      ) : requestState.status === 'failed' ? (
        <ErrorState
          title="Document sets could not be loaded"
          description="The backend pagination response failed before the catalogue could render."
          details={requestState.reason}
          onRetry={() => void refresh()}
        />
      ) : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-3xl border bg-card/80 p-4 shadow-sm">
            <p className="text-sm text-muted-foreground">Showing {page?.items.length ?? 0} live document sets. Continuation stays driven by backend pagination tokens.</p>
            <div className="flex gap-2">
              <Button disabled={!hasPreviousPage} onClick={goToPreviousPage} type="button" variant="outline">
                Previous page
              </Button>
              <Button disabled={!hasNextPage} onClick={goToNextPage} type="button" variant="outline">
                Next page
              </Button>
            </div>
          </div>

          {page && page.items.length === 0 ? (
            <EmptyState title="No document sets yet" description="Create a document set to start powering downstream document and validation workflows." />
          ) : null}

          <div className="grid gap-4 xl:grid-cols-2">
            {page?.items.map((documentSet) => (
              <Card key={documentSet.id} className="border-border/70 bg-card/95 shadow-sm">
                <CardHeader>
                  <CardTitle className="text-lg">{documentSet.id}</CardTitle>
                  <CardDescription>
                    Created by {documentSet.createdBy} on {new Date(documentSet.createdAt).toLocaleString()}
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4 text-sm text-muted-foreground">
                  <p>{documentSet.documents.length} document summary record(s) returned in this page result.</p>
                  <div className="flex flex-wrap gap-2">
                    {documentSet.documents.map((document) => (
                      <Badge key={document.id} className="border-border bg-transparent">
                        {document.type} · v{document.versionCount}
                      </Badge>
                    ))}
                  </div>
                  <Button asChild variant="outline">
                    <Link to={`/document-sets/${documentSet.id}`}>Open document set detail</Link>
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function parseMetadata(input: string) {
  const entries = input
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [key, ...valueParts] = line.split('=')
      return [key?.trim(), valueParts.join('=').trim()] as const
    })
    .filter(([key, value]) => key && value)

  return entries.length > 0 ? Object.fromEntries(entries) : undefined
}

function ResultList(props: { title: string; items: string[] }) {
  return (
    <div className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
      <p className="font-medium">{props.title}</p>
      <ul className="mt-2 space-y-1">
        {props.items.map((item) => (
          <li key={item}>• {item}</li>
        ))}
      </ul>
    </div>
  )
}

import { useState, type FormEvent } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { LoadingState } from '@/components/workbench-states'
import { canSubmitWhileDisconnected } from '@/integration/request-lifecycle'
import { useIntegration } from '@/integration/integration-provider'
import { encodeTextToBase64 } from '@/lib/base64'

const schemaFormats = ['XSD', 'JSON_SCHEMA', 'RELAXNG'] as const
const fieldClassName = 'mt-2 w-full rounded-xl border bg-background px-3 py-2 text-sm outline-none ring-ring focus:ring-2'

export function SchemaWorkflowPage() {
  const { contractState, runRequest } = useIntegration()
  const [schemaForm, setSchemaForm] = useState({ name: '', format: 'XSD' as (typeof schemaFormats)[number] })
  const [versionForm, setVersionForm] = useState({ schemaId: '', versionIdentifier: '', definitionText: '' })
  const [schemaErrors, setSchemaErrors] = useState<string[]>([])
  const [versionErrors, setVersionErrors] = useState<string[]>([])
  const [schemaResult, setSchemaResult] = useState<string>()
  const [versionResult, setVersionResult] = useState<string>()
  const [submitting, setSubmitting] = useState<'schema' | 'version' | undefined>()

  if (contractState.kind === 'loading-contract') {
    return <LoadingState title="Loading schema workflows" description="Connecting to the live backend before schema actions can be submitted." />
  }

  const canSubmit = canSubmitWhileDisconnected(contractState)

  async function submitSchema(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const errors = [schemaForm.name.trim() ? undefined : 'Schema name is required.'].filter(Boolean) as string[]
    setSchemaErrors(errors)
    setSchemaResult(undefined)

    if (errors.length > 0) {
      return
    }

    setSubmitting('schema')
    const result = await runRequest('createSchemaWorkflow', (client) =>
      client.createSchema({
        name: schemaForm.name.trim(),
        format: schemaForm.format
      })
    )
    setSubmitting(undefined)

    if (result.status !== 'succeeded') {
      setSchemaErrors([result.status === 'failed' ? result.reason : 'The schema request did not complete.'])
      return
    }

    setVersionForm((current) => ({ ...current, schemaId: result.data.id }))
    setSchemaResult(`${result.data.name} created as ${result.data.id} in ${result.data.format} format.`)
  }

  async function submitVersion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const errors = [
      versionForm.schemaId.trim() ? undefined : 'Schema ID is required.',
      versionForm.versionIdentifier.trim() ? undefined : 'Version identifier is required.',
      versionForm.definitionText.trim() ? undefined : 'Schema definition text is required.'
    ].filter(Boolean) as string[]
    setVersionErrors(errors)
    setVersionResult(undefined)

    if (errors.length > 0) {
      return
    }

    setSubmitting('version')
    const result = await runRequest('addSchemaVersionWorkflow', (client) =>
      client.addSchemaVersion(versionForm.schemaId.trim(), {
        versionIdentifier: versionForm.versionIdentifier.trim(),
        definition: encodeTextToBase64(versionForm.definitionText)
      })
    )
    setSubmitting(undefined)

    if (result.status !== 'succeeded') {
      setVersionErrors([result.status === 'failed' ? result.reason : 'The schema version request did not complete.'])
      return
    }

    setVersionResult(
      `${result.data.versionIdentifier} created as ${result.data.id} on ${new Date(result.data.createdAt).toLocaleString()}. Deprecated: ${result.data.deprecated ? 'yes' : 'no'}.`
    )
  }

  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <Card className="border-border/70 bg-card/95 shadow-sm">
        <CardHeader>
          <Badge className="w-fit">Workflow view</Badge>
          <CardTitle>Create schema</CardTitle>
          <CardDescription>Required fields are checked locally before the live backend request is sent.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submitSchema}>
            <label className="block text-sm font-medium text-foreground">
              Schema name
              <input className={fieldClassName} value={schemaForm.name} onChange={(event) => setSchemaForm({ ...schemaForm, name: event.target.value })} />
            </label>
            <label className="block text-sm font-medium text-foreground">
              Format
              <select className={fieldClassName} value={schemaForm.format} onChange={(event) => setSchemaForm({ ...schemaForm, format: event.target.value as (typeof schemaFormats)[number] })}>
                {schemaFormats.map((format) => (
                  <option key={format} value={format}>
                    {format}
                  </option>
                ))}
              </select>
            </label>
            {!canSubmit ? <p className="text-sm text-destructive">Live submission is blocked until the backend connection recovers.</p> : null}
            {schemaErrors.length > 0 ? <ResultList title="Submission needs attention" items={schemaErrors} /> : null}
            {schemaResult ? <SuccessSummary message={schemaResult} /> : null}
            <Button disabled={!canSubmit || submitting === 'schema'} type="submit">
              {submitting === 'schema' ? 'Submitting…' : 'Create schema'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card className="border-border/70 bg-card/95 shadow-sm">
        <CardHeader>
          <Badge className="w-fit">Workflow view</Badge>
          <CardTitle>Add schema version</CardTitle>
          <CardDescription>Definition text stays visible after backend failures so operators can retry without losing work.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submitVersion}>
            <label className="block text-sm font-medium text-foreground">
              Schema ID
              <input className={fieldClassName} value={versionForm.schemaId} onChange={(event) => setVersionForm({ ...versionForm, schemaId: event.target.value })} />
            </label>
            <label className="block text-sm font-medium text-foreground">
              Version identifier
              <input
                className={fieldClassName}
                value={versionForm.versionIdentifier}
                onChange={(event) => setVersionForm({ ...versionForm, versionIdentifier: event.target.value })}
              />
            </label>
            <label className="block text-sm font-medium text-foreground">
              Schema definition text
              <textarea
                className={`${fieldClassName} min-h-40`}
                value={versionForm.definitionText}
                onChange={(event) => setVersionForm({ ...versionForm, definitionText: event.target.value })}
              />
            </label>
            {!canSubmit ? <p className="text-sm text-destructive">Live submission is blocked until the backend connection recovers.</p> : null}
            {versionErrors.length > 0 ? <ResultList title="Submission needs attention" items={versionErrors} /> : null}
            {versionResult ? <SuccessSummary message={versionResult} /> : null}
            <Button disabled={!canSubmit || submitting === 'version'} type="submit">
              {submitting === 'version' ? 'Submitting…' : 'Add schema version'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
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

function SuccessSummary(props: { message: string }) {
  return <p className="rounded-2xl border border-primary/20 bg-primary/5 p-4 text-sm text-foreground">{props.message}</p>
}

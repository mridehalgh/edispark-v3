import { useMemo, useState } from 'react'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useOutletContext, useParams, useSearchParams } from 'react-router-dom'

import type { AppShellContext } from '@/components/app-shell'
import { EmptyPanel, EndpointHint, JsonDebugPanel, RetryPanel, SectionHeading, SurfaceCard } from '@/components/panels'
import { getKnownSchemaIds, rememberSchemaId } from '@/lib/known-resources'
import { buildAddSchemaVersionRequest, buildCreateSchemaRequest, type AddSchemaVersionFormInput, type CreateSchemaFormInput } from '@/lib/payload-adapter'
import { capabilityAvailable, endpointFor } from '@/lib/view-availability'
import type { SchemaFormat } from '@/lib/models'

function SchemaActions({ schemaId }: { schemaId?: string }) {
  const { resourceClient, catalogue, connection } = useOutletContext<AppShellContext>()
  const queryClient = useQueryClient()
  const [createState, setCreateState] = useState<CreateSchemaFormInput>({ name: 'Retail Orders XSD', format: 'XSD' })
  const [versionState, setVersionState] = useState<AddSchemaVersionFormInput>({
    schemaId: schemaId || '',
    versionIdentifier: '1.0.0',
    definitionText: '<xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"></xsd:schema>',
  })

  const createPreview = useMemo(() => buildCreateSchemaRequest(createState), [createState])
  const versionPreview = useMemo(() => buildAddSchemaVersionRequest(versionState), [versionState])

  const createMutation = useMutation({
    mutationFn: () => resourceClient.createSchema(createPreview),
    onSuccess: (response) => {
      rememberSchemaId(response.id)
      void queryClient.invalidateQueries({ queryKey: ['known-schemas'] })
    },
  })

  const addVersionMutation = useMutation({
    mutationFn: () => resourceClient.addSchemaVersion(versionState.schemaId, versionPreview),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['schema', versionState.schemaId] }),
  })

  return (
    <div className="grid gap-4 xl:grid-cols-2">
      <SurfaceCard>
        <h3 className="font-semibold">Create schema</h3>
        <div className="mt-4 space-y-3">
          <label className="text-sm">Schema name<input className="mt-1 w-full rounded-md border px-3 py-2" value={createState.name} onChange={(event) => setCreateState((current) => ({ ...current, name: event.target.value }))} /></label>
          <label className="text-sm">Schema format<select className="mt-1 w-full rounded-md border px-3 py-2" value={createState.format} onChange={(event) => setCreateState((current) => ({ ...current, format: event.target.value as SchemaFormat }))}><option>XSD</option><option>JSON_SCHEMA</option><option>RELAXNG</option></select></label>
          <EndpointHint endpoint={endpointFor(catalogue, 'createSchema')?.path} />
          <button className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50" disabled={!capabilityAvailable(catalogue, 'createSchema', connection) || createMutation.isPending} onClick={() => createMutation.mutate()} type="button">Create schema</button>
          <JsonDebugPanel payload={createPreview} title="Create schema request" />
          {createMutation.data ? <JsonDebugPanel payload={createMutation.data} title="Create schema response" /> : null}
          {createMutation.error ? <JsonDebugPanel payload={createMutation.error} title="Create schema error" /> : null}
        </div>
      </SurfaceCard>

      <SurfaceCard>
        <h3 className="font-semibold">Add schema version</h3>
        <div className="mt-4 space-y-3">
          <label className="text-sm">Schema ID<input className="mt-1 w-full rounded-md border px-3 py-2" value={versionState.schemaId} onChange={(event) => setVersionState((current) => ({ ...current, schemaId: event.target.value }))} /></label>
          <label className="text-sm">Version identifier<input className="mt-1 w-full rounded-md border px-3 py-2" value={versionState.versionIdentifier} onChange={(event) => setVersionState((current) => ({ ...current, versionIdentifier: event.target.value }))} /></label>
          <label className="text-sm">Definition text<textarea className="mt-1 min-h-28 w-full rounded-md border px-3 py-2 font-mono" value={versionState.definitionText} onChange={(event) => setVersionState((current) => ({ ...current, definitionText: event.target.value }))} /></label>
          <EndpointHint endpoint={endpointFor(catalogue, 'addSchemaVersion')?.path} />
          <button className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50" disabled={!capabilityAvailable(catalogue, 'addSchemaVersion', connection) || addVersionMutation.isPending} onClick={() => addVersionMutation.mutate()} type="button">Add schema version</button>
          <JsonDebugPanel payload={versionPreview} title="Schema version request" />
          {addVersionMutation.data ? <JsonDebugPanel payload={addVersionMutation.data} title="Schema version response" /> : null}
          {addVersionMutation.error ? <JsonDebugPanel payload={addVersionMutation.error} title="Schema version error" /> : null}
        </div>
      </SurfaceCard>
    </div>
  )
}

function SchemaDetail() {
  const { schemaId = '' } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const { resourceClient, connection, catalogue, retryDiscovery } = useOutletContext<AppShellContext>()
  const versionId = searchParams.get('versionId') || undefined

  const schemaEnabled = capabilityAvailable(catalogue, 'getSchema', connection)
  const schemaQuery = useQuery({
    queryKey: ['schema', schemaId, connection.baseUrl],
    queryFn: () => resourceClient.getSchema(schemaId),
    enabled: schemaEnabled && Boolean(schemaId),
  })
  const versionQuery = useQuery({
    queryKey: ['schema-version', schemaId, versionId, connection.baseUrl],
    queryFn: () => resourceClient.getSchemaVersion(schemaId, versionId!),
    enabled: capabilityAvailable(catalogue, 'getSchemaVersion', connection) && Boolean(schemaId && versionId),
  })

  if (!schemaEnabled) {
    return <RetryPanel title="Schema detail unavailable" message="The current contract does not expose schema lookup. Restore the backend discovery contract to resume schema inspections." onRetry={retryDiscovery} />
  }
  if (schemaQuery.isError) {
    return <RetryPanel title="Schema detail failed" message={`Schema ${schemaId} could not be retrieved from the backend.`} onRetry={() => void schemaQuery.refetch()} />
  }
  if (schemaQuery.isPending || !schemaQuery.data) {
    return <SurfaceCard>Loading schema detail…</SurfaceCard>
  }

  const schema = schemaQuery.data
  rememberSchemaId(schema.id)

  return (
    <div className="space-y-6">
      <SurfaceCard>
        <SectionHeading
          eyebrow="Schema detail"
          title={schema.name}
          description={`Format ${schema.format}. Preserve raw backend schema identifiers while framing versions for operator governance.`}
        />
        <EndpointHint endpoint={endpointFor(catalogue, 'getSchema')?.path} />
        <div className="mt-4 grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
          <div className="rounded-2xl border p-4">
            <p className="font-semibold">Published versions</p>
            <div className="mt-3 space-y-2">
              {schema.versions.map((version) => (
                <button className={`flex w-full items-center justify-between rounded-xl border px-4 py-3 text-left ${version.versionIdentifier === versionId ? 'border-primary bg-primary/5' : 'hover:bg-slate-50'}`} key={version.id} onClick={() => setSearchParams({ versionId: version.versionIdentifier })} type="button">
                  <div>
                    <p className="font-medium">{version.versionIdentifier}</p>
                    <p className="text-xs text-muted-foreground">Version ID: {version.id}</p>
                  </div>
                  <span className="text-xs text-muted-foreground">{version.deprecated ? 'Deprecated' : 'Active'}</span>
                </button>
              ))}
            </div>
          </div>
          <JsonDebugPanel payload={schema} title="Schema payload" />
        </div>
      </SurfaceCard>

      {versionQuery.data ? (
        <SurfaceCard>
          <h2 className="text-lg font-semibold">Schema-version inspection</h2>
          <EndpointHint endpoint={endpointFor(catalogue, 'getSchemaVersion')?.path} />
          <div className="mt-4 grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
            <div className="rounded-2xl border p-4">
              <p className="font-semibold">{versionQuery.data.versionIdentifier}</p>
              <p className="mt-2 text-sm text-muted-foreground">Backend version ID {versionQuery.data.id} · created {new Date(versionQuery.data.createdAt).toLocaleString()}</p>
            </div>
            <JsonDebugPanel payload={versionQuery.data} title="Schema-version payload" />
          </div>
        </SurfaceCard>
      ) : versionQuery.isError ? (
        <RetryPanel title="Schema-version lookup failed" message={`Version ${versionId} for schema ${schemaId} could not be loaded.`} onRetry={() => void versionQuery.refetch()} />
      ) : null}

      <SchemaActions schemaId={schema.id} />
    </div>
  )
}

export function SchemaCentreView() {
  const { schemaId } = useParams()
  const { resourceClient, connection, catalogue, retryDiscovery } = useOutletContext<AppShellContext>()

  const knownSchemaIds = getKnownSchemaIds()
  const knownSchemas = useQueries({
    queries: knownSchemaIds.map((id) => ({
      queryKey: ['known-schemas', id, connection.baseUrl],
      queryFn: () => resourceClient.getSchema(id),
      enabled: capabilityAvailable(catalogue, 'getSchema', connection),
    })),
  })

  if (schemaId) {
    return <SchemaDetail />
  }

  if (!capabilityAvailable(catalogue, 'getSchema', connection) && !capabilityAvailable(catalogue, 'createSchema', connection)) {
    return <RetryPanel title="Schema centre degraded" message="Schema actions are unavailable because the local OpenAPI contract is missing schema operations." onRetry={retryDiscovery} />
  }

  return (
    <div className="space-y-6">
      <SurfaceCard>
        <SectionHeading
          eyebrow="Schema management"
          title="Govern schema contracts for retail message validation"
          description="The backend currently exposes create and detail operations. This list view shows known schemas pinned from prior creations or inspections."
        />
      </SurfaceCard>

      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <SurfaceCard>
          <h2 className="text-lg font-semibold">Known schemas</h2>
          <p className="mt-2 text-sm text-muted-foreground">No backend list endpoint is currently published, so this operator list is built from schema details previously returned by the API.</p>
          <div className="mt-4 space-y-3">
            {knownSchemas.length ? knownSchemas.map((query) => {
              if (!query.data) {
                return null
              }

              return (
                <div className="rounded-2xl border p-4" key={query.data.id}>
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="font-semibold">{query.data.name}</p>
                      <p className="text-xs text-muted-foreground">{query.data.id} · {query.data.format}</p>
                    </div>
                    <Link className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground" to={`/schemas/${query.data.id}`}>
                      Inspect
                    </Link>
                  </div>
                </div>
              )
            }) : <EmptyPanel title="No schemas pinned yet" description="Create a schema or inspect one by ID to keep it visible in this list." />}
          </div>
        </SurfaceCard>

        <SchemaActions />
      </div>
    </div>
  )
}

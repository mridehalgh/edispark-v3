import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useOutletContext, useParams, useSearchParams } from 'react-router-dom'

import type { AppShellContext } from '@/components/app-shell'
import { EmptyPanel, EndpointHint, JsonDebugPanel, RetryPanel, SectionHeading, SurfaceCard } from '@/components/panels'
import { messageFraming, supportedMessageTypes } from '@/lib/message-types'
import { rememberDocumentSetId, rememberSchemaId } from '@/lib/known-resources'
import {
  buildAddDocumentRequest,
  buildAddVersionRequest,
  buildCreateDerivativeRequest,
  buildCreateDocumentSetRequest,
  type AddDocumentFormInput,
  type AddVersionFormInput,
  type CreateDerivativeFormInput,
  type CreateDocumentSetFormInput,
} from '@/lib/payload-adapter'
import { endpointFor, capabilityAvailable } from '@/lib/view-availability'
import type { DocumentType } from '@/lib/models'

const documentTypes: DocumentType[] = ['ORDER', 'INVOICE', 'DESPATCH_ADVICE', 'RECEIPT_ADVICE', 'REMITTANCE_ADVICE', 'CATALOGUE', 'APPLICATION_RESPONSE', 'STATEMENT', 'QUOTATION', 'CREDIT_NOTE', 'DEBIT_NOTE']

function DocumentSetActions({ setId, docId }: { setId: string; docId?: string }) {
  const { resourceClient, catalogue, connection } = useOutletContext<AppShellContext>()
  const queryClient = useQueryClient()

  const [createState, setCreateState] = useState<CreateDocumentSetFormInput>({
    documentType: 'ORDER',
    schemaId: '',
    schemaVersion: '1.0',
    createdBy: 'integration.analyst',
    messageType: 'ORDERS',
    contentText: "UNH+1+ORDERS:D:03B:UN:EAN008'",
    metadataText: 'tradingPartner=Retailer-UK\nchannel=ecommerce',
  })
  const [addDocumentState, setAddDocumentState] = useState<AddDocumentFormInput>({
    setId,
    documentType: 'INVOICE',
    schemaId: '',
    schemaVersion: '1.0',
    createdBy: 'operations.user',
    messageType: 'INVOIC',
    relatedDocumentId: '',
    contentText: "UNH+1+INVOIC:D:03B:UN:EAN008'",
  })
  const [addVersionState, setAddVersionState] = useState<AddVersionFormInput>({
    setId,
    docId: docId || '',
    createdBy: 'operations.user',
    messageType: 'DESADV',
    contentText: "UNH+1+DESADV:D:03B:UN:EAN008'",
  })
  const [derivativeState, setDerivativeState] = useState<CreateDerivativeFormInput>({
    setId,
    docId: docId || '',
    sourceVersionNumber: 1,
    targetFormat: 'JSON',
  })

  useEffect(() => {
    setAddDocumentState((current) => ({ ...current, setId }))
    setAddVersionState((current) => ({ ...current, setId, docId: docId || '' }))
    setDerivativeState((current) => ({ ...current, setId, docId: docId || '' }))
  }, [docId, setId])

  const createPreview = useMemo(() => buildCreateDocumentSetRequest(createState), [createState])
  const addDocumentPreview = useMemo(() => buildAddDocumentRequest(addDocumentState), [addDocumentState])
  const addVersionPreview = useMemo(() => buildAddVersionRequest(addVersionState), [addVersionState])
  const derivativePreview = useMemo(() => buildCreateDerivativeRequest(derivativeState), [derivativeState])

  const createMutation = useMutation({
    mutationFn: () => resourceClient.createDocumentSet(createPreview),
    onSuccess: (response) => {
      rememberDocumentSetId(response.id)
      void queryClient.invalidateQueries({ queryKey: ['document-sets'] })
    },
  })
  const addDocumentMutation = useMutation({
    mutationFn: () => resourceClient.addDocument(addDocumentState.setId, addDocumentPreview),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['document-set', addDocumentState.setId] }),
  })
  const addVersionMutation = useMutation({
    mutationFn: () => resourceClient.addVersion(addVersionState.setId, addVersionState.docId, addVersionPreview),
  })
  const derivativeMutation = useMutation({
    mutationFn: () => resourceClient.createDerivative(derivativeState.setId, derivativeState.docId, derivativePreview),
  })

  const operations = [
    { title: 'Create document set', capability: 'createDocumentSet' as const, disabled: !capabilityAvailable(catalogue, 'createDocumentSet', connection) },
    { title: 'Add document', capability: 'addDocument' as const, disabled: !capabilityAvailable(catalogue, 'addDocument', connection) || !setId },
    { title: 'Add version', capability: 'addVersion' as const, disabled: !capabilityAvailable(catalogue, 'addVersion', connection) || !docId },
    { title: 'Create derivative', capability: 'createDerivative' as const, disabled: !capabilityAvailable(catalogue, 'createDerivative', connection) || !docId },
  ]

  return (
    <div className="grid gap-4 xl:grid-cols-2">
      <SurfaceCard>
        <h3 className="font-semibold">Document-set workflows</h3>
        <p className="mt-2 text-sm text-muted-foreground">Associate new work with a retail message type, preview encoded payloads, and submit against the discovered backend operation.</p>
        <div className="mt-4 flex flex-wrap gap-2">
          {operations.map((operation) => (
            <span className={`rounded-full px-2 py-1 text-xs font-semibold ${operation.disabled ? 'bg-amber-50 text-amber-700' : 'bg-emerald-50 text-emerald-700'}`} key={operation.title}>
              {operation.title}: {operation.disabled ? 'disabled' : 'ready'}
            </span>
          ))}
        </div>

        <div className="mt-5 space-y-4">
          <div className="space-y-3 rounded-2xl border p-4">
            <div className="grid gap-3 md:grid-cols-2">
              <label className="text-sm">Document type<select className="mt-1 w-full rounded-md border px-3 py-2" value={createState.documentType} onChange={(event) => setCreateState((current) => ({ ...current, documentType: event.target.value as DocumentType }))}>{documentTypes.map((type) => <option key={type}>{type}</option>)}</select></label>
              <label className="text-sm">Message type<select className="mt-1 w-full rounded-md border px-3 py-2" value={createState.messageType} onChange={(event) => setCreateState((current) => ({ ...current, messageType: event.target.value as (typeof supportedMessageTypes)[number] }))}>{supportedMessageTypes.map((type) => <option key={type}>{type}</option>)}</select></label>
              <label className="text-sm">Schema ID<input className="mt-1 w-full rounded-md border px-3 py-2" value={createState.schemaId} onChange={(event) => setCreateState((current) => ({ ...current, schemaId: event.target.value }))} /></label>
              <label className="text-sm">Schema version<input className="mt-1 w-full rounded-md border px-3 py-2" value={createState.schemaVersion} onChange={(event) => setCreateState((current) => ({ ...current, schemaVersion: event.target.value }))} /></label>
            </div>
            <label className="text-sm">Created by<input className="mt-1 w-full rounded-md border px-3 py-2" value={createState.createdBy} onChange={(event) => setCreateState((current) => ({ ...current, createdBy: event.target.value }))} /></label>
            <label className="text-sm">Metadata key=value lines<textarea className="mt-1 min-h-24 w-full rounded-md border px-3 py-2" value={createState.metadataText} onChange={(event) => setCreateState((current) => ({ ...current, metadataText: event.target.value }))} /></label>
            <label className="text-sm">EDI payload<textarea className="mt-1 min-h-28 w-full rounded-md border px-3 py-2 font-mono" value={createState.contentText} onChange={(event) => setCreateState((current) => ({ ...current, contentText: event.target.value }))} /></label>
            <EndpointHint endpoint={endpointFor(catalogue, 'createDocumentSet')?.path} />
            <button className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50" disabled={!capabilityAvailable(catalogue, 'createDocumentSet', connection) || createMutation.isPending} onClick={() => createMutation.mutate()} type="button">Create document set</button>
            <JsonDebugPanel payload={createPreview} title="Request preview" />
            {createMutation.data ? <JsonDebugPanel payload={createMutation.data} title="Response payload" /> : null}
            {createMutation.error ? <JsonDebugPanel payload={createMutation.error} title="Error payload" /> : null}
          </div>

          <div className="space-y-3 rounded-2xl border p-4">
            <label className="text-sm">Target set ID<input className="mt-1 w-full rounded-md border px-3 py-2" value={addDocumentState.setId} onChange={(event) => setAddDocumentState((current) => ({ ...current, setId: event.target.value }))} /></label>
            <div className="grid gap-3 md:grid-cols-2">
              <label className="text-sm">Document type<select className="mt-1 w-full rounded-md border px-3 py-2" value={addDocumentState.documentType} onChange={(event) => setAddDocumentState((current) => ({ ...current, documentType: event.target.value as DocumentType }))}>{documentTypes.map((type) => <option key={type}>{type}</option>)}</select></label>
              <label className="text-sm">Message type<select className="mt-1 w-full rounded-md border px-3 py-2" value={addDocumentState.messageType} onChange={(event) => setAddDocumentState((current) => ({ ...current, messageType: event.target.value as (typeof supportedMessageTypes)[number] }))}>{supportedMessageTypes.map((type) => <option key={type}>{type}</option>)}</select></label>
            </div>
            <label className="text-sm">Schema ID<input className="mt-1 w-full rounded-md border px-3 py-2" value={addDocumentState.schemaId} onChange={(event) => setAddDocumentState((current) => ({ ...current, schemaId: event.target.value }))} /></label>
            <label className="text-sm">Related document ID<input className="mt-1 w-full rounded-md border px-3 py-2" value={addDocumentState.relatedDocumentId} onChange={(event) => setAddDocumentState((current) => ({ ...current, relatedDocumentId: event.target.value }))} /></label>
            <label className="text-sm">Document payload<textarea className="mt-1 min-h-24 w-full rounded-md border px-3 py-2 font-mono" value={addDocumentState.contentText} onChange={(event) => setAddDocumentState((current) => ({ ...current, contentText: event.target.value }))} /></label>
            <EndpointHint endpoint={endpointFor(catalogue, 'addDocument')?.path} />
            <button className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50" disabled={!capabilityAvailable(catalogue, 'addDocument', connection) || addDocumentMutation.isPending} onClick={() => addDocumentMutation.mutate()} type="button">Add document</button>
            <JsonDebugPanel payload={addDocumentPreview} title="Add document preview" />
            {addDocumentMutation.data ? <JsonDebugPanel payload={addDocumentMutation.data} title="Document response" /> : null}
            {addDocumentMutation.error ? <JsonDebugPanel payload={addDocumentMutation.error} title="Document error" /> : null}
          </div>
        </div>
      </SurfaceCard>

      <SurfaceCard>
        <h3 className="font-semibold">Versioning and derivative actions</h3>
        <div className="mt-4 space-y-4">
          <div className="space-y-3 rounded-2xl border p-4">
            <label className="text-sm">Document ID<input className="mt-1 w-full rounded-md border px-3 py-2" value={addVersionState.docId} onChange={(event) => setAddVersionState((current) => ({ ...current, docId: event.target.value }))} /></label>
            <label className="text-sm">Message framing<select className="mt-1 w-full rounded-md border px-3 py-2" value={addVersionState.messageType} onChange={(event) => setAddVersionState((current) => ({ ...current, messageType: event.target.value as (typeof supportedMessageTypes)[number] }))}>{supportedMessageTypes.map((type) => <option key={type}>{type}</option>)}</select></label>
            <label className="text-sm">Updated payload<textarea className="mt-1 min-h-24 w-full rounded-md border px-3 py-2 font-mono" value={addVersionState.contentText} onChange={(event) => setAddVersionState((current) => ({ ...current, contentText: event.target.value }))} /></label>
            <EndpointHint endpoint={endpointFor(catalogue, 'addVersion')?.path} />
            <button className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50" disabled={!capabilityAvailable(catalogue, 'addVersion', connection) || addVersionMutation.isPending} onClick={() => addVersionMutation.mutate()} type="button">Add version</button>
            <JsonDebugPanel payload={addVersionPreview} title="Add version preview" />
            {addVersionMutation.data ? <JsonDebugPanel payload={addVersionMutation.data} title="Version response" /> : null}
            {addVersionMutation.error ? <JsonDebugPanel payload={addVersionMutation.error} title="Version error" /> : null}
          </div>

          <div className="space-y-3 rounded-2xl border p-4">
            <label className="text-sm">Derivative source version<input className="mt-1 w-full rounded-md border px-3 py-2" min={1} type="number" value={derivativeState.sourceVersionNumber} onChange={(event) => setDerivativeState((current) => ({ ...current, sourceVersionNumber: Number(event.target.value) }))} /></label>
            <label className="text-sm">Target format<select className="mt-1 w-full rounded-md border px-3 py-2" value={derivativeState.targetFormat} onChange={(event) => setDerivativeState((current) => ({ ...current, targetFormat: event.target.value as 'JSON' | 'XML' | 'PDF' | 'EDI' }))}><option>JSON</option><option>XML</option><option>PDF</option><option>EDI</option></select></label>
            <EndpointHint endpoint={endpointFor(catalogue, 'createDerivative')?.path} />
            <button className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50" disabled={!capabilityAvailable(catalogue, 'createDerivative', connection) || derivativeMutation.isPending} onClick={() => derivativeMutation.mutate()} type="button">Create derivative</button>
            <JsonDebugPanel payload={derivativePreview} title="Derivative request" />
            {derivativeMutation.data ? <JsonDebugPanel payload={derivativeMutation.data} title="Derivative response" /> : null}
            {derivativeMutation.error ? <JsonDebugPanel payload={derivativeMutation.error} title="Derivative error" /> : null}
          </div>

          <div className="rounded-2xl border bg-slate-50 p-4 text-sm text-muted-foreground">
            <p className="font-semibold text-slate-900">Retail message cues</p>
            <div className="mt-3 space-y-2">
              {messageFraming.slice(0, 4).map((message) => (
                <div key={message.messageType}>
                  <span className="font-medium text-slate-900">{message.messageType}</span> · {message.businessIntent}
                </div>
              ))}
            </div>
          </div>
        </div>
      </SurfaceCard>
    </div>
  )
}

function DocumentSetDetail() {
  const { setId = '' } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const { resourceClient, connection, retryDiscovery, catalogue } = useOutletContext<AppShellContext>()

  const detailEnabled = capabilityAvailable(catalogue, 'getDocumentSet', connection)
  const documentId = searchParams.get('docId') || undefined
  const versionNumber = searchParams.get('versionNumber') ? Number(searchParams.get('versionNumber')) : undefined

  const detailQuery = useQuery({
    queryKey: ['document-set', setId, connection.baseUrl],
    queryFn: () => resourceClient.getDocumentSet(setId),
    enabled: detailEnabled && Boolean(setId),
  })
  const documentQuery = useQuery({
    queryKey: ['document', setId, documentId, connection.baseUrl],
    queryFn: () => resourceClient.getDocument(setId, documentId!),
    enabled: capabilityAvailable(catalogue, 'getDocument', connection) && Boolean(setId && documentId),
  })
  const versionQuery = useQuery({
    queryKey: ['version', setId, documentId, versionNumber, connection.baseUrl],
    queryFn: () => resourceClient.getVersion(setId, documentId!, versionNumber!),
    enabled: capabilityAvailable(catalogue, 'getVersion', connection) && Boolean(setId && documentId && versionNumber),
  })
  const contentQuery = useQuery({
    queryKey: ['version-content', setId, documentId, versionNumber, connection.baseUrl],
    queryFn: () => resourceClient.getVersionContent(setId, documentId!, versionNumber!),
    enabled: false,
  })
  const derivativesQuery = useQuery({
    queryKey: ['derivatives', setId, documentId, connection.baseUrl],
    queryFn: () => resourceClient.getDerivatives(setId, documentId!),
    enabled: capabilityAvailable(catalogue, 'getDerivatives', connection) && Boolean(setId && documentId),
  })

  if (!detailEnabled) {
    return <RetryPanel title="Document-set detail unavailable" message="The current OpenAPI contract does not expose document-set retrieval. Restore /api-docs to re-enable this surface." onRetry={retryDiscovery} />
  }
  if (detailQuery.isError) {
    return <RetryPanel title="Document-set detail failed" message={`Document set ${setId} could not be loaded. Retry the active resource view.`} onRetry={() => void detailQuery.refetch()} />
  }
  if (detailQuery.isPending || !detailQuery.data) {
    return <SurfaceCard>Loading document-set detail…</SurfaceCard>
  }

  const documentSet = detailQuery.data
  rememberDocumentSetId(documentSet.id)
  documentQuery.data?.schemaRef?.schemaId && rememberSchemaId(documentQuery.data.schemaRef.schemaId)

  return (
    <div className="space-y-6">
      <SurfaceCard>
        <SectionHeading
          eyebrow="Document set detail"
          title={`Set ${documentSet.id}`}
          description={`Created by ${documentSet.createdBy} on ${new Date(documentSet.createdAt).toLocaleString()}. Keep raw identifiers visible while mapping set contents to retail message intent.`}
        />
        <EndpointHint endpoint={endpointFor(catalogue, 'getDocumentSet')?.path} />
        <div className="mt-4 grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
          <div className="rounded-2xl border p-4">
            <h3 className="font-semibold">Documents in this set</h3>
            <div className="mt-3 space-y-2">
              {documentSet.documents.map((document) => (
                <button className={`flex w-full items-center justify-between rounded-xl border px-4 py-3 text-left ${document.id === documentId ? 'border-primary bg-primary/5' : 'hover:bg-slate-50'}`} key={document.id} onClick={() => setSearchParams((current) => {
                  const next = new URLSearchParams(current)
                  next.set('docId', document.id)
                  next.delete('versionNumber')
                  return next
                })} type="button">
                  <div>
                    <p className="font-medium">{document.type}</p>
                    <p className="text-xs text-muted-foreground">Document ID: {document.id}</p>
                  </div>
                  <span className="text-xs text-muted-foreground">{document.versionCount} versions</span>
                </button>
              ))}
              {!documentSet.documents.length ? (
                <EmptyPanel
                  description="This set has been created, but no documents have been attached yet. Use the add-document workflow below once the trading payload is ready."
                  title="No documents in this set yet"
                />
              ) : null}
            </div>
          </div>
          <JsonDebugPanel payload={documentSet} title="Raw document-set payload" />
        </div>
      </SurfaceCard>

      {documentQuery.data ? (
        <SurfaceCard>
          <h2 className="text-lg font-semibold">Selected document</h2>
          <p className="mt-1 text-sm text-muted-foreground">{documentQuery.data.type} · schema {documentQuery.data.schemaRef.schemaId} · backend document ID {documentQuery.data.id}</p>
          <EndpointHint endpoint={endpointFor(catalogue, 'getDocument')?.path} />
          <div className="mt-4 grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
            <div className="space-y-4">
              <div className="rounded-2xl border p-4">
                <p className="font-semibold">Current version</p>
                <div className="mt-2 text-sm text-muted-foreground">Version {documentQuery.data.currentVersion.versionNumber} · {documentQuery.data.currentVersion.format} · parse status {documentQuery.data.currentVersion.parseStatus || 'n/a'}</div>
                <button className="mt-3 text-sm font-medium text-primary underline underline-offset-4" onClick={() => setSearchParams((current) => {
                  const next = new URLSearchParams(current)
                  next.set('docId', documentQuery.data!.id)
                  next.set('versionNumber', `${documentQuery.data!.currentVersion.versionNumber}`)
                  return next
                })} type="button">Inspect version</button>
              </div>
              <div className="rounded-2xl border p-4">
                <p className="font-semibold">Derivatives</p>
                <div className="mt-3 space-y-2">
                  {(derivativesQuery.data || documentQuery.data.derivatives).map((derivative) => (
                    <div className="rounded-xl border bg-slate-50 px-4 py-3" key={derivative.id}>
                      <div className="font-medium">{derivative.targetFormat}</div>
                      <div className="text-xs text-muted-foreground">Derivative ID: {derivative.id} · Source version: {derivative.sourceVersionId}</div>
                    </div>
                  ))}
                  {!(derivativesQuery.data || documentQuery.data.derivatives).length ? (
                    <EmptyPanel
                      description="No derivative payloads have been generated for this document yet. Use the derivative workflow below to create downstream formats for debugging."
                      title="No derivatives returned"
                    />
                  ) : null}
                </div>
              </div>
            </div>
            <JsonDebugPanel payload={documentQuery.data} title="Raw document payload" />
          </div>
        </SurfaceCard>
      ) : documentQuery.isError ? (
        <RetryPanel title="Document lookup failed" message={`Document ${documentId} could not be retrieved for this set.`} onRetry={() => void documentQuery.refetch()} />
      ) : null}

      {versionQuery.data ? (
        <SurfaceCard>
          <h2 className="text-lg font-semibold">Version inspection</h2>
          <EndpointHint endpoint={endpointFor(catalogue, 'getVersion')?.path} />
          <div className="mt-4 grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
            <div className="space-y-4">
              <div className="rounded-2xl border p-4">
                <p className="font-semibold">Version {versionQuery.data.versionNumber}</p>
                <p className="mt-2 text-sm text-muted-foreground">Content hash {versionQuery.data.contentHash} · backend version ID {versionQuery.data.id}</p>
                <p className="mt-2 text-sm text-muted-foreground">Message type {versionQuery.data.messageType || 'Not parsed'} · parse status {versionQuery.data.parseStatus || 'Unavailable'}</p>
                <button className="mt-3 rounded-md border px-3 py-2 text-sm font-medium" onClick={() => void contentQuery.refetch()} type="button">Load content</button>
              </div>
              {contentQuery.data ? <JsonDebugPanel payload={contentQuery.data} title="Content debug panel" /> : null}
            </div>
            <JsonDebugPanel payload={versionQuery.data} title="Version payload" />
          </div>
        </SurfaceCard>
      ) : versionQuery.isError ? (
        <RetryPanel title="Version lookup failed" message={`Version ${versionNumber} for document ${documentId} could not be loaded.`} onRetry={() => void versionQuery.refetch()} />
      ) : null}

      <DocumentSetActions docId={documentId} setId={documentSet.id} />
    </div>
  )
}

export function DocumentSetsView() {
  const { resourceClient, connection, retryDiscovery, catalogue } = useOutletContext<AppShellContext>()
  const { setId } = useParams()
  const listEnabled = capabilityAvailable(catalogue, 'listDocumentSets', connection)
  const [nextToken, setNextToken] = useState<string | null>(null)

  const listQuery = useQuery({
    queryKey: ['document-sets', connection.baseUrl, nextToken],
    queryFn: () => resourceClient.listDocumentSets(8, nextToken),
    enabled: listEnabled && !setId,
  })

  if (setId) {
    return <DocumentSetDetail />
  }

  if (!listEnabled) {
    return <RetryPanel title="Document-set listing unavailable" message="The local contract is missing the document-set list operation, so backend-driven lists are disabled until discovery recovers." onRetry={retryDiscovery} />
  }

  return (
    <div className="space-y-6">
      <SurfaceCard>
        <SectionHeading
          eyebrow="Document sets"
          title="Track retail message bundles"
          description="Use backend continuation tokens to page through message bundles and drill into individual documents, versions, derivatives, and raw content."
        />
        <EndpointHint endpoint={endpointFor(catalogue, 'listDocumentSets')?.path} />
      </SurfaceCard>

      {listQuery.isError ? (
        <RetryPanel title="Document-set list failed" message="The backend rejected the active document-set list request. Retry the view and keep the resource shell intact." onRetry={() => void listQuery.refetch()} />
      ) : listQuery.isPending ? (
        <SurfaceCard>Loading document sets…</SurfaceCard>
      ) : listQuery.data?.items.length ? (
        <div className="space-y-6">
          <div className="grid gap-4 lg:grid-cols-2">
            {listQuery.data.items.map((item) => (
              <SurfaceCard key={item.id}>
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="text-lg font-semibold">Document set {item.id}</p>
                    <p className="mt-1 text-sm text-muted-foreground">Created by {item.createdBy} · {new Date(item.createdAt).toLocaleString()}</p>
                    <p className="mt-3 text-xs text-muted-foreground">Documents: {item.documents.map((document) => document.type).join(', ') || 'None yet'}</p>
                  </div>
                  <Link className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground" to={`/document-sets/${item.id}`}>
                    Inspect
                  </Link>
                </div>
              </SurfaceCard>
            ))}
          </div>

          <SurfaceCard className="flex flex-wrap items-center justify-between gap-3">
            <div className="text-sm text-muted-foreground">Current continuation token: <code>{nextToken || 'initial page'}</code></div>
            <div className="flex gap-2">
              <button className="rounded-md border px-3 py-2 text-sm font-medium disabled:opacity-50" disabled={!listQuery.data.previousToken} onClick={() => setNextToken(listQuery.data?.previousToken || null)} type="button">Previous token</button>
              <button className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50" disabled={!listQuery.data.nextToken} onClick={() => setNextToken(listQuery.data?.nextToken || null)} type="button">Next page</button>
            </div>
          </SurfaceCard>
        </div>
      ) : (
        <EmptyPanel title="No document sets returned" description="The backend is healthy but no document-set data was returned for the active page. Create one from the detail workflow once you have a schema ready." linkLabel="Manage schemas" linkTo="/schemas" />
      )}
    </div>
  )
}

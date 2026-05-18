import { useMemo, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useOutletContext } from 'react-router-dom'

import type { AppShellContext } from '@/components/app-shell'
import { EndpointHint, JsonDebugPanel, RetryPanel, SectionHeading, SurfaceCard } from '@/components/panels'
import { capabilityAvailable, endpointFor } from '@/lib/view-availability'

export function ValidationView() {
  const { resourceClient, catalogue, connection, retryDiscovery } = useOutletContext<AppShellContext>()
  const [input, setInput] = useState({ setId: '', docId: '', versionNumber: 1 })

  const validateMutation = useMutation({
    mutationFn: () => resourceClient.validateDocument(input.setId, input.docId, input.versionNumber),
  })

  const available = capabilityAvailable(catalogue, 'validateDocument', connection)
  if (!available) {
    return <RetryPanel title="Validation workflow unavailable" message="The current OpenAPI contract does not expose document validation, so the workflow is disabled while the rest of the UI stays available." onRetry={retryDiscovery} />
  }

  const requestPreview = useMemo(() => ({
    setId: input.setId,
    docId: input.docId,
    versionNumber: input.versionNumber,
  }), [input])

  return (
    <div className="space-y-6">
      <SurfaceCard>
        <SectionHeading
          eyebrow="Validation workflows"
          title="Run document checks against discovered schema bindings"
          description="Preserve operator input after failures and keep request and response payloads visible for local debugging."
        />
      </SurfaceCard>

      <SurfaceCard>
        <div className="space-y-3">
          <label className="text-sm">Document-set ID<input className="mt-1 w-full rounded-md border px-3 py-2" value={input.setId} onChange={(event) => setInput((current) => ({ ...current, setId: event.target.value }))} /></label>
          <label className="text-sm">Document ID<input className="mt-1 w-full rounded-md border px-3 py-2" value={input.docId} onChange={(event) => setInput((current) => ({ ...current, docId: event.target.value }))} /></label>
          <label className="text-sm">Version number<input className="mt-1 w-full rounded-md border px-3 py-2" min={1} type="number" value={input.versionNumber} onChange={(event) => setInput((current) => ({ ...current, versionNumber: Number(event.target.value) }))} /></label>
          <EndpointHint endpoint={endpointFor(catalogue, 'validateDocument')?.path} />
          <button className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50" disabled={validateMutation.isPending} onClick={() => validateMutation.mutate()} type="button">Validate document</button>
          <JsonDebugPanel payload={requestPreview} title="Validation request" />
          {validateMutation.data ? <JsonDebugPanel payload={validateMutation.data} title="Validation response" /> : null}
          {validateMutation.error ? <JsonDebugPanel payload={validateMutation.error} title="Validation error" /> : null}
        </div>
      </SurfaceCard>
    </div>
  )
}

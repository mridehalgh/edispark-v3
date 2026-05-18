import { useOutletContext } from 'react-router-dom'

import type { AppShellContext } from '@/components/app-shell'
import { EmptyPanel, SurfaceCard, SectionHeading } from '@/components/panels'
import { messageFraming } from '@/lib/message-types'

export function DashboardView() {
  const { catalogue, connection } = useOutletContext<AppShellContext>()

  const groupSummaries = catalogue
    ? Object.entries(catalogue.groups).map(([group, operations]) => ({ group, count: operations.length }))
    : []

  return (
    <div className="space-y-6">
      <SurfaceCard>
        <SectionHeading
          eyebrow="Operations home"
          title="Retail and e-commerce message desk"
          description="Start with live backend discovery, then move into document set triage, schema governance, validation checks, and payload debugging."
        />

        <div className="mt-6 grid gap-4 md:grid-cols-3">
          <div className="rounded-2xl border bg-slate-50 p-4">
            <p className="text-sm font-semibold">Local backend origin</p>
            <p className="mt-2 break-all text-sm text-muted-foreground">{connection.baseUrl}</p>
          </div>
          <div className="rounded-2xl border bg-slate-50 p-4">
            <p className="text-sm font-semibold">OpenAPI contract</p>
            <p className="mt-2 text-sm text-muted-foreground">{catalogue ? `${catalogue.sourceTitle} · ${catalogue.sourceVersion}` : 'Waiting for /api-docs discovery'}</p>
          </div>
          <div className="rounded-2xl border bg-slate-50 p-4">
            <p className="text-sm font-semibold">Operator mode</p>
            <p className="mt-2 text-sm text-muted-foreground">{connection.status === 'healthy' ? 'Connected for live EDI operations' : 'Degraded local mode with backend-dependent actions disabled'}</p>
          </div>
        </div>
      </SurfaceCard>

      <div className="grid gap-6 xl:grid-cols-[1.3fr_1fr]">
        <SurfaceCard>
          <h2 className="text-lg font-semibold">Supported retail message intent</h2>
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            {messageFraming.map((message) => (
              <div className="rounded-2xl border p-4" key={message.messageType}>
                <p className="text-sm font-semibold text-slate-900">{message.displayLabel}</p>
                <p className="mt-2 text-sm text-muted-foreground">{message.businessIntent}</p>
              </div>
            ))}
          </div>
        </SurfaceCard>

        <SurfaceCard>
          <h2 className="text-lg font-semibold">Discovered operator groups</h2>
          {groupSummaries.length ? (
            <div className="mt-4 space-y-3">
              {groupSummaries.map((summary) => (
                <div className="flex items-center justify-between rounded-xl border bg-slate-50 px-4 py-3" key={summary.group}>
                  <span className="text-sm font-medium capitalize">{summary.group.replace('-', ' ')}</span>
                  <span className="rounded-full bg-primary/10 px-2 py-1 text-xs font-semibold text-primary">{summary.count} ops</span>
                </div>
              ))}
            </div>
          ) : (
            <EmptyPanel title="Discovery pending" description="The dashboard stays available in degraded mode. Once /api-docs is reachable, this panel will summarise backend-backed workflows." />
          )}
        </SurfaceCard>
      </div>
    </div>
  )
}

import { useOutletContext } from 'react-router-dom'

import type { AppShellContext } from '@/components/app-shell'
import { EmptyPanel, SurfaceCard, SectionHeading } from '@/components/panels'

export function ApiExplorerView() {
  const { catalogue } = useOutletContext<AppShellContext>()

  return (
    <div className="space-y-6">
      <SurfaceCard>
        <SectionHeading
          eyebrow="API explorer"
          title="Contract-derived endpoint catalogue"
          description="Every operator-facing route maps back to a discovered backend operation so local debugging stays transparent."
        />
      </SurfaceCard>

      {catalogue ? (
        Object.entries(catalogue.groups).map(([group, operations]) => (
          <SurfaceCard key={group}>
            <h2 className="text-lg font-semibold capitalize">{group.replace('-', ' ')}</h2>
            <div className="mt-4 overflow-hidden rounded-2xl border">
              <table className="min-w-full divide-y">
                <thead className="bg-slate-50 text-left text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3">Method</th>
                    <th className="px-4 py-3">Path</th>
                    <th className="px-4 py-3">Summary</th>
                    <th className="px-4 py-3">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y bg-white text-sm">
                  {operations.length ? (
                    operations.map((operation) => (
                      <tr key={`${operation.method}-${operation.path}`}>
                        <td className="px-4 py-3 font-semibold text-primary">{operation.method}</td>
                        <td className="px-4 py-3 font-mono text-xs">{operation.path}</td>
                        <td className="px-4 py-3">
                          <div>{operation.summary}</div>
                          <div className="text-xs text-muted-foreground">Tag: {operation.tag}</div>
                        </td>
                        <td className="px-4 py-3">
                          <span className={`rounded-full px-2 py-1 text-xs font-semibold ${operation.available ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
                            {operation.available ? 'Available' : 'Unavailable'}
                          </span>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td className="px-4 py-4 text-muted-foreground" colSpan={4}>
                        No operations in this group for the current contract.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </SurfaceCard>
        ))
      ) : (
        <EmptyPanel title="No contract available" description="The explorer is waiting for a valid /api-docs document. Start the backend or verify the configured origin, then retry discovery from the banner." />
      )}
    </div>
  )
}

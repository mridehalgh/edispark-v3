import type { MappingValidationReport, MappingValidationRequest } from '@edispark/integration-client'
import { ShieldCheck } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export function MappingDocumentValidator({ mappingId, side, content, label }: {
  mappingId: string
  side: MappingValidationRequest['side']
  content?: string
  label: string
}) {
  const request = useRef<AbortController | null>(null)
  const [check, setCheck] = useState<{ mappingId: string; side: MappingValidationRequest['side']; content: string; report?: MappingValidationReport; error?: string }>()
  const current = check?.mappingId === mappingId && check.side === side && check.content === content ? check : undefined
  const pending = !!current && !current.report && !current.error

  useEffect(() => () => request.current?.abort(), [mappingId, side, content])

  async function validate() {
    if (!content?.trim() || pending) return
    request.current?.abort()
    const controller = new AbortController()
    request.current = controller
    const snapshot = { mappingId, side, content }
    setCheck(snapshot)
    try {
      const body: MappingValidationRequest = { side, content }
      const response = await fetch(`/api/integration/mappings/${mappingId}/validation`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: controller.signal,
      })
      const payload = await response.json()
      if (!response.ok) throw new Error(payload?.error?.message ?? 'Validation could not be completed.')
      if (!controller.signal.aborted) setCheck({ ...snapshot, report: payload })
    } catch (error) {
      if (controller.signal.aborted) setCheck((previous) => previous === snapshot ? undefined : previous)
      else setCheck({ ...snapshot, error: error instanceof Error ? error.message : 'Validation could not be completed.' })
    }
  }

  return <div className="min-w-0 space-y-2">
    <div className="flex flex-wrap items-center gap-2">
      <Button type="button" variant="outline" size="sm" onClick={() => void validate()} disabled={!content?.trim() || pending} className="gap-1.5">
        <ShieldCheck aria-hidden="true" className="size-4" />{pending ? 'Validating…' : label}
      </Button>
      {current?.report && <span role="status" className={cn('text-xs font-medium', current.report.valid ? 'text-success-foreground' : 'text-destructive')}>
        {current.report.valid ? current.report.findings?.length ? 'Passed with findings' : 'Passed' : 'Needs attention'}
      </span>}
    </div>
    {current?.error && <p role="alert" className="break-words text-xs text-destructive">{current.error}</p>}
    {current?.report && <div className="rounded-md border bg-muted/25 px-3 py-2 text-xs">
      <p className="font-medium">{current.report.scope}</p>
      {(current.report.findings?.length ?? 0) > 0 && <ul className="mt-1.5 max-h-52 space-y-2 overflow-auto">
        {current.report.findings?.map((finding, index) => <li key={index} className="break-words">
          <span className="font-medium">{finding.location || finding.code}{finding.severity === 'WARNING' ? ' · Warning' : ''}</span>
          <span className="block text-muted-foreground">{finding.message}</span>
        </li>)}
      </ul>}
    </div>}
  </div>
}

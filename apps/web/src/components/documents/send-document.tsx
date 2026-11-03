'use client'

import type { EligibleRoute, ExchangeResponse } from '@edispark/integration-client'
import { CheckCircle2, CircleAlert, Send, Settings2 } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: 'no-store' })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.error?.message ?? 'The document could not be sent.')
  return body as T
}

function routeLabel(route: EligibleRoute) {
  const capability = route.capability
  return [route.agreementName, capability?.standard, capability?.release, capability?.messageType]
    .filter(Boolean)
    .join(' · ')
}

export function SendDocument({ documentSetId, documentId }: { documentSetId: string; documentId: string }) {
  const [routes, setRoutes] = useState<EligibleRoute[]>()
  const [selected, setSelected] = useState('')
  const [loading, setLoading] = useState(false)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string>()
  const [exchange, setExchange] = useState<ExchangeResponse>()

  async function openRoutes() {
    setLoading(true)
    setError(undefined)
    try {
      const query = new URLSearchParams({ documentSetId, documentId })
      const available = await requestJson<EligibleRoute[]>(`/api/integration/exchanges/eligible-routes?${query}`)
      setRoutes(available)
      setSelected((current) => current || available[0]?.capability?.id || '')
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Available routes could not be loaded.')
    } finally {
      setLoading(false)
    }
  }

  async function send() {
    const route = routes?.find((item) => item.capability?.id === selected)
    if (!route?.agreementId || !route.capability?.id) return
    setSending(true)
    setError(undefined)
    try {
      const result = await requestJson<ExchangeResponse>('/api/integration/exchanges', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          agreementId: route.agreementId,
          capabilityId: route.capability.id,
          documentSetId,
          documentId,
          idempotencyKey: crypto.randomUUID(),
        }),
      })
      setExchange(result)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'The document could not be sent.')
    } finally {
      setSending(false)
    }
  }

  if (exchange) {
    const needsAttention = exchange.status === 'FAILED' || exchange.status === 'NEEDS_REVIEW'
    return (
      <div className={`mt-3 flex max-w-md items-start gap-2 rounded-md px-3 py-2 text-left text-xs ${needsAttention ? 'bg-destructive/10 text-destructive' : 'bg-success/25 text-success-foreground'}`} role="status">
        {needsAttention ? <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> : <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />}
        <span>{needsAttention ? 'The exchange needs attention.' : 'Queued for delivery.'}</span>
      </div>
    )
  }

  if (!routes) {
    return (
      <div className="mt-3 flex flex-col items-start gap-2">
        <Button type="button" className="gap-2" onClick={() => void openRoutes()} disabled={loading}><Send className="h-4 w-4" aria-hidden="true" />{loading ? 'Finding routes…' : 'Send to partner'}</Button>
        {error && <p className="text-xs text-destructive" role="alert">{error}</p>}
      </div>
    )
  }

  if (routes.length === 0) {
    return (
      <div className="mt-3 max-w-md rounded-md border bg-muted/35 p-3 text-left text-xs">
        <p className="font-medium">No qualified route is active</p>
        <p className="mt-1 leading-5 text-muted-foreground">Ask a workspace administrator to finish this partner connection.</p>
        <Button asChild variant="link" size="sm" className="mt-1 h-auto gap-1 p-0"><Link to="/settings/connections"><Settings2 className="h-3.5 w-3.5" />Open connections</Link></Button>
      </div>
    )
  }

  if (routes.length === 1) {
    return <div className="mt-3"><Button type="button" className="gap-2" onClick={() => void send()} disabled={sending}><Send className="h-4 w-4" aria-hidden="true" />{sending ? 'Queuing…' : 'Send to partner'}</Button>{error && <p className="mt-2 text-xs text-destructive" role="alert">{error}</p>}</div>
  }

  return (
    <div className="mt-3 flex max-w-md flex-col gap-2 sm:flex-row" aria-label="Send document to trading partner">
      <label className="sr-only" htmlFor={`route-${documentId}`}>Trading agreement route</label>
      <select id={`route-${documentId}`} value={selected} onChange={(event) => setSelected(event.target.value)} className="h-10 min-w-0 flex-1 rounded-md border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
        {routes.map((route) => <option key={route.capability?.id} value={route.capability?.id}>{routeLabel(route)}</option>)}
      </select>
      <Button type="button" className="gap-2" onClick={() => void send()} disabled={sending || !selected}><Send className="h-4 w-4" aria-hidden="true" />{sending ? 'Queuing…' : 'Send securely'}</Button>
      {error && <p className="text-xs text-destructive" role="alert">{error}</p>}
    </div>
  )
}

import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { cn } from '@/lib/utils'

export function SurfaceCard({ className, children }: { className?: string; children: ReactNode }) {
  return <section className={cn('rounded-2xl border bg-card p-5 shadow-sm', className)}>{children}</section>
}

export function SectionHeading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string
  title: string
  description: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-[0.22em] text-primary">{eyebrow}</p>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        <p className="max-w-3xl text-sm text-muted-foreground">{description}</p>
      </div>
      {action}
    </div>
  )
}

export function EndpointHint({ endpoint }: { endpoint?: string }) {
  if (!endpoint) {
    return <p className="text-xs text-muted-foreground">Endpoint unavailable from current contract.</p>
  }

  return <p className="text-xs text-muted-foreground">Backend endpoint: <code>{endpoint}</code></p>
}

export function RetryPanel({
  title,
  message,
  onRetry,
}: {
  title: string
  message: string
  onRetry?: () => void
}) {
  return (
    <SurfaceCard className="border-dashed">
      <h3 className="font-semibold">{title}</h3>
      <p className="mt-2 text-sm text-muted-foreground">{message}</p>
      {onRetry ? (
        <button className="mt-4 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground" onClick={onRetry} type="button">
          Retry
        </button>
      ) : null}
    </SurfaceCard>
  )
}

export function EmptyPanel({ title, description, linkTo, linkLabel }: { title: string; description: string; linkTo?: string; linkLabel?: string }) {
  return (
    <SurfaceCard className="border-dashed">
      <h3 className="font-semibold">{title}</h3>
      <p className="mt-2 text-sm text-muted-foreground">{description}</p>
      {linkTo && linkLabel ? (
        <Link className="mt-4 inline-flex text-sm font-medium text-primary underline underline-offset-4" to={linkTo}>
          {linkLabel}
        </Link>
      ) : null}
    </SurfaceCard>
  )
}

export function JsonDebugPanel({
  title,
  payload,
}: {
  title: string
  payload: unknown
}) {
  return (
    <div className="space-y-2 rounded-xl border bg-slate-950 p-4 text-slate-50">
      <h4 className="text-sm font-semibold">{title}</h4>
      <pre className="overflow-x-auto text-xs leading-6">{JSON.stringify(payload, null, 2)}</pre>
    </div>
  )
}

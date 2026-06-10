import type { ReactNode } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

type StateCardProps = {
  badge?: string
  title: string
  description: string
  children?: ReactNode
  action?: ReactNode
}

function StateCard({ badge, title, description, children, action }: StateCardProps) {
  return (
    <Card className="border-border/70 bg-card/95 shadow-sm">
      <CardHeader className="space-y-4">
        {badge ? <Badge className="w-fit">{badge}</Badge> : null}
        <div className="space-y-2">
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </div>
      </CardHeader>
      <CardContent className="space-y-4 text-sm text-muted-foreground">
        {children}
        {action ? <div>{action}</div> : null}
      </CardContent>
    </Card>
  )
}

export function LoadingState(props: {
  title: string
  description: string
}) {
  return (
    <StateCard badge="Loading" title={props.title} description={props.description}>
      <div className="space-y-3">
        <div className="h-3 w-full rounded-full bg-muted" />
        <div className="h-3 w-4/5 rounded-full bg-muted" />
        <div className="h-3 w-3/5 rounded-full bg-muted" />
      </div>
    </StateCard>
  )
}

export function EmptyState(props: {
  title: string
  description: string
  action?: ReactNode
}) {
  return <StateCard badge="Empty" title={props.title} description={props.description} action={props.action} />
}

export function ErrorState(props: {
  title: string
  description: string
  details?: string
  retryLabel?: string
  onRetry?: () => void
}) {
  return (
    <StateCard
      badge="Needs attention"
      title={props.title}
      description={props.description}
      action={
        props.onRetry ? (
          <Button onClick={props.onRetry} type="button" variant="outline">
            {props.retryLabel ?? 'Retry'}
          </Button>
        ) : undefined
      }
    >
      {props.details ? <p className="rounded-2xl bg-muted p-4">{props.details}</p> : null}
    </StateCard>
  )
}

export function PlannedCapabilityState(props: {
  title: string
  description: string
  actionLabel?: string
  action?: ReactNode
}) {
  return (
    <StateCard badge="Planned capability" title={props.title} description={props.description}>
      {props.action ? (
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
            {props.actionLabel ?? 'Current fallback'}
          </span>
          {props.action}
        </div>
      ) : null}
    </StateCard>
  )
}

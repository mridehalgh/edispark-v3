import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EmptyState, ErrorState, PlannedCapabilityState } from '@/components/workbench-states'
import { useIntegration } from '@/integration/integration-provider'
import { localBackendConfig, resolveLocalOpenApiUrl } from '@/integration/local-backend-config'

export const primaryDestinations = [
  { path: '/', label: 'Dashboard' },
  { path: '/schemas', label: 'Schemas' },
  { path: '/document-sets', label: 'Document sets' },
  { path: '/retail-journeys', label: 'Retail journeys' },
  { path: '/api-explorer', label: 'API explorer' }
] as const

type PageMeta = {
  title: string
  description: string
}

function getPageMeta(pathname: string): PageMeta {
  if (pathname === '/') {
    return {
      title: 'EDI operations workbench',
      description: 'A live operator shell for backend-aware dashboards, retail journeys, and document workflows.'
    }
  }

  if (pathname.startsWith('/schemas')) {
    return {
      title: 'Schemas',
      description: 'Monitor schema readiness and prepare version workflows aligned to live backend integration.'
    }
  }

  if (pathname.startsWith('/document-sets')) {
    return {
      title: 'Document sets',
      description: 'Inspect document-set intake areas and the records powering retail EDI journeys.'
    }
  }

  if (pathname.startsWith('/retail-journeys')) {
    return {
      title: 'Retail journeys',
      description: 'Curated UK retail ORDERS, DESADV, RECADV, and INVOIC views projected from live document evidence.'
    }
  }

  return {
    title: 'API explorer',
    description: 'Review the live OpenAPI contract and the backend operations available in the current environment.'
  }
}

function renderConnectionCopy() {
  const openApiUrl = resolveLocalOpenApiUrl()

  return `Local backend defaults resolve to ${localBackendConfig.defaultBaseUrl} and OpenAPI discovery to ${openApiUrl}.`
}

export function WorkbenchShell() {
  const { contractState, refreshContract } = useIntegration()
  const { pathname } = useLocation()
  const pageMeta = getPageMeta(pathname)
  const openApiUrl = resolveLocalOpenApiUrl()

  return (
    <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col gap-6 px-4 py-8 sm:px-6 lg:px-8">
      <header className="rounded-3xl border bg-card/80 p-6 shadow-sm backdrop-blur">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="space-y-3">
            <Badge className="w-fit bg-accent text-accent-foreground hover:bg-accent">EDI operations workbench</Badge>
            <div className="space-y-2">
              <h1 className="text-3xl font-semibold tracking-tight">EdiSpark frontend module</h1>
              <p className="text-sm text-muted-foreground">{renderConnectionCopy()}</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button onClick={() => void refreshContract()} type="button" variant="outline">
              {contractState.kind === 'contract-failed' ? 'Retry connection' : 'Refresh contract'}
            </Button>
            <Button asChild>
              <a href={openApiUrl} rel="noreferrer" target="_blank">
                View local OpenAPI contract
              </a>
            </Button>
          </div>
        </div>
        <nav className="mt-6 flex flex-wrap gap-2">
          {primaryDestinations.map((destination) => (
            <NavLink key={destination.path} to={destination.path}>
              {({ isActive }) => (
                <span
                  className={[
                    'inline-flex rounded-full border px-3 py-2 text-sm font-medium transition-colors',
                    isActive
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'border-border bg-background text-foreground hover:bg-secondary'
                  ].join(' ')}
                >
                  {destination.label}
                </span>
              )}
            </NavLink>
          ))}
        </nav>
      </header>

      <section className="grid gap-4 rounded-3xl border bg-card/80 p-6 shadow-sm backdrop-blur lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-[0.3em] text-muted-foreground">Page chrome</p>
          <h2 className="text-2xl font-semibold tracking-tight">{pageMeta.title}</h2>
          <p className="text-sm text-muted-foreground">{pageMeta.description}</p>
        </div>
        <div className="grid gap-3 rounded-2xl border bg-background/70 p-4">
          <p className="text-xs font-semibold uppercase tracking-[0.3em] text-muted-foreground">Status region</p>
          <ConnectionStatus />
        </div>
      </section>

      {contractState.kind === 'contract-failed' ? (
        <ErrorState
          title="Initial application state is unavailable"
          description="The workbench could not load the live backend contract needed to initialise workflow views."
          details={contractState.reason}
          onRetry={() => void refreshContract()}
          retryLabel="Retry contract load"
        />
      ) : (
        <Outlet />
      )}
    </div>
  )
}

function ConnectionStatus() {
  const { contractState, refreshContract } = useIntegration()

  if (contractState.kind === 'loading-contract') {
    return (
      <div className="space-y-2">
        <Badge className="border-transparent bg-secondary text-secondary-foreground">Pending</Badge>
        <p className="text-sm text-muted-foreground">Loading the live contract from {contractState.openApiUrl}.</p>
      </div>
    )
  }

  if (contractState.kind === 'connected') {
    return (
      <div className="space-y-2">
        <Badge>Connected</Badge>
        <p className="text-sm text-muted-foreground">
          {contractState.contractTitle} v{contractState.contractVersion} with {contractState.operationCount} live operations.
        </p>
      </div>
    )
  }

  if (contractState.kind === 'request-failed') {
    return (
      <div className="space-y-3">
        <Badge className="border-transparent bg-destructive text-destructive-foreground">Degraded request</Badge>
        <p className="text-sm text-muted-foreground">
          {contractState.operationId} failed while the contract stayed connected.
        </p>
        <p className="text-sm text-muted-foreground">{contractState.reason}</p>
        <Button onClick={() => void refreshContract()} type="button" variant="outline">
          Refresh integration state
        </Button>
      </div>
    )
  }

  return (
    <EmptyState
      title="Backend connection unavailable"
      description={contractState.reason}
      action={
        <Button onClick={() => void refreshContract()} type="button" variant="outline">
          Retry contract load
        </Button>
      }
    />
  )
}

export function PlannedWorkbenchPage(props: {
  title: string
  description: string
  actionPath: string
  actionLabel: string
}) {
  return (
    <PlannedCapabilityState
      title={props.title}
      description={props.description}
      action={
        <Button asChild variant="outline">
          <Link to={props.actionPath}>{props.actionLabel}</Link>
        </Button>
      }
    />
  )
}

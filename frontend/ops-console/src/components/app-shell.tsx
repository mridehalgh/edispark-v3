import { useMemo } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { BookOpenText, FileCheck2, FileStack, LayoutDashboard, Network } from 'lucide-react'

import { discoverBackend } from '@/lib/discovery'
import { createResourceClient } from '@/lib/resource-client'
import type { ConnectionState, EndpointCatalogue } from '@/lib/models'
import { messageFraming } from '@/lib/message-types'

const navigation = [
  { to: '/dashboard', label: 'Operations home', icon: LayoutDashboard },
  { to: '/document-sets', label: 'Document sets', icon: FileStack },
  { to: '/schemas', label: 'Schemas', icon: BookOpenText },
  { to: '/validation', label: 'Validation', icon: FileCheck2 },
  { to: '/api-explorer', label: 'API explorer', icon: Network },
]

export type AppShellContext = {
  connection: ConnectionState
  catalogue?: EndpointCatalogue
  retryDiscovery: () => void
  resourceClient: ReturnType<typeof createResourceClient>
}

const bannerTone = (status: ConnectionState['status']) => {
  if (status === 'healthy') {
    return 'border-emerald-200 bg-emerald-50 text-emerald-900'
  }
  if (status === 'checking') {
    return 'border-sky-200 bg-sky-50 text-sky-900'
  }
  return 'border-amber-200 bg-amber-50 text-amber-900'
}

export function AppShell() {
  const discoveryQuery = useQuery({
    queryKey: ['backend-discovery'],
    queryFn: () => discoverBackend(),
    retry: false,
  })

  const discovery = discoveryQuery.data
  const connection: ConnectionState = discovery?.connection || {
    status: 'checking',
    baseUrl: 'http://localhost:8080',
    openApiUrl: 'http://localhost:8080/api-docs',
    message: 'Checking local retail EDI backend…',
  }

  const resourceClient = useMemo(
    () => createResourceClient({ baseUrl: connection.baseUrl }),
    [connection.baseUrl],
  )

  const contextValue: AppShellContext = {
    connection,
    catalogue: discovery?.catalogue,
    retryDiscovery: () => {
      void discoveryQuery.refetch()
    },
    resourceClient,
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-50 via-background to-slate-100">
      <div className="mx-auto flex min-h-screen max-w-7xl flex-col px-4 py-4 sm:px-6 lg:px-8">
        <header className="rounded-[1.75rem] border bg-white/90 p-6 shadow-sm backdrop-blur">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div className="space-y-3">
              <p className="text-xs font-semibold uppercase tracking-[0.28em] text-primary">EdiSpark retail operations</p>
              <div className="space-y-2">
                <h1 className="text-3xl font-semibold tracking-tight text-slate-950">UK retail EDI control surface</h1>
                <p className="max-w-3xl text-sm text-muted-foreground">
                  Monitor document lineage, manage schema changes, and debug local integrations without leaving the operator workbench.
                </p>
              </div>
            </div>

            <div className="grid gap-2 text-sm text-muted-foreground sm:grid-cols-2 lg:min-w-[24rem]">
              <div className="rounded-xl border bg-slate-50 p-3">
                <p className="font-medium text-slate-900">Active backend</p>
                <p className="mt-1 break-all text-xs">{connection.baseUrl}</p>
              </div>
              <div className="rounded-xl border bg-slate-50 p-3">
                <p className="font-medium text-slate-900">Contract source</p>
                <p className="mt-1 break-all text-xs">{connection.openApiUrl}</p>
              </div>
            </div>
          </div>

          <div className={`mt-6 rounded-2xl border px-4 py-3 ${bannerTone(connection.status)}`}>
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <p className="text-sm font-semibold capitalize">Connection state: {connection.status}</p>
                <p className="mt-1 text-sm">{connection.message}</p>
                {connection.status === 'healthy' && discovery?.catalogue ? (
                  <p className="mt-1 text-xs">
                    Contract: {discovery.catalogue.sourceTitle} · version {discovery.catalogue.sourceVersion}
                  </p>
                ) : null}
              </div>
              <button className="rounded-md border border-current px-3 py-2 text-sm font-medium" onClick={() => void discoveryQuery.refetch()} type="button">
                Retry discovery
              </button>
            </div>
          </div>

          <div className="mt-6 flex flex-wrap gap-2">
            {messageFraming.map((message) => (
              <span className="rounded-full border bg-slate-50 px-3 py-1 text-xs font-medium text-slate-700" key={message.messageType}>
                {message.displayLabel}
              </span>
            ))}
          </div>
        </header>

        <div className="mt-6 grid flex-1 gap-6 lg:grid-cols-[18rem_minmax(0,1fr)]">
          <aside className="rounded-[1.5rem] border bg-white/90 p-4 shadow-sm backdrop-blur">
            <nav className="space-y-2">
              {navigation.map(({ to, label, icon: Icon }) => (
                <NavLink
                  className={({ isActive }) =>
                    `flex items-center gap-3 rounded-xl px-3 py-3 text-sm font-medium transition ${
                      isActive ? 'bg-primary text-primary-foreground shadow-sm' : 'text-slate-700 hover:bg-slate-100'
                    }`
                  }
                  key={to}
                  to={to}
                >
                  <Icon className="size-4" />
                  {label}
                </NavLink>
              ))}
            </nav>

            <div className="mt-6 rounded-2xl border bg-slate-50 p-4 text-sm text-muted-foreground">
              <p className="font-semibold text-slate-900">Retail framing</p>
              <p className="mt-2">Keep transport details visible while translating generic resources into orders, despatch, invoice, and stock workflows.</p>
              <Link className="mt-3 inline-flex text-primary underline underline-offset-4" to="/api-explorer">
                Inspect discovered endpoints
              </Link>
            </div>
          </aside>

          <main>
            <Outlet context={contextValue} />
          </main>
        </div>
      </div>
    </div>
  )
}

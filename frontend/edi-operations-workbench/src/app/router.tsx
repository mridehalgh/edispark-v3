import { createBrowserRouter, NavLink, Outlet } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { localBackendConfig, resolveLocalOpenApiUrl } from '@/integration/local-backend-config'

const primaryDestinations = [
  { path: '/', label: 'Dashboard' },
  { path: '/schemas', label: 'Schemas' },
  { path: '/document-sets', label: 'Document sets' },
  { path: '/retail-journeys', label: 'Retail journeys' },
  { path: '/api-explorer', label: 'API explorer' }
] as const

const pageCopy = {
  '/': {
    title: 'EDI operations workbench',
    description: 'Retail-oriented EDI workflows will be added on top of the live Documents API.'
  },
  '/schemas': {
    title: 'Schemas',
    description: 'Schema creation and version workflows will mount here.'
  },
  '/document-sets': {
    title: 'Document sets',
    description: 'Document set listing, intake, and inspection flows will mount here.'
  },
  '/retail-journeys': {
    title: 'Retail journeys',
    description: 'ORDERS, DESADV, RECADV, and INVOIC journey views will mount here.'
  },
  '/api-explorer': {
    title: 'API explorer',
    description: 'The live OpenAPI catalogue will mount here.'
  }
} as const

function WorkbenchLayout() {
  const openApiUrl = resolveLocalOpenApiUrl()

  return (
    <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col gap-6 px-4 py-8 sm:px-6 lg:px-8">
      <header className="flex flex-col gap-4 rounded-3xl border bg-card/80 p-6 shadow-sm backdrop-blur">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="space-y-2">
            <Badge className="w-fit bg-accent text-accent-foreground hover:bg-accent">
              EDI operations workbench
            </Badge>
            <div>
              <h1 className="text-3xl font-semibold tracking-tight">EdiSpark frontend module</h1>
              <p className="text-sm text-muted-foreground">
                Local backend defaults resolve to {localBackendConfig.defaultBaseUrl} and OpenAPI discovery to {openApiUrl}.
              </p>
            </div>
          </div>
          <Button asChild>
            <a href={openApiUrl} rel="noreferrer" target="_blank">
              View local OpenAPI contract
            </a>
          </Button>
        </div>
        <nav className="flex flex-wrap gap-2">
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
      <Outlet />
    </div>
  )
}

function SetupPage(props: { route: keyof typeof pageCopy }) {
  const copy = pageCopy[props.route]

  return (
    <Card>
      <CardHeader>
        <CardTitle>{copy.title}</CardTitle>
        <CardDescription>{copy.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-sm text-muted-foreground">
        <p>
          This placeholder confirms React routing, shared styling, and local backend configuration are wired before
          workflow features land.
        </p>
        <ul className="list-disc space-y-2 pl-5">
          <li>React Router is mounted with primary navigation destinations.</li>
          <li>shadcn/ui styling is available through the shared component primitives.</li>
          <li>OpenAPI client generation is available via <code>npm run api:generate</code>.</li>
        </ul>
      </CardContent>
    </Card>
  )
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <WorkbenchLayout />,
    children: [
      { index: true, element: <SetupPage route="/" /> },
      { path: 'schemas', element: <SetupPage route="/schemas" /> },
      { path: 'document-sets', element: <SetupPage route="/document-sets" /> },
      { path: 'retail-journeys', element: <SetupPage route="/retail-journeys" /> },
      { path: 'api-explorer', element: <SetupPage route="/api-explorer" /> }
    ]
  }
])

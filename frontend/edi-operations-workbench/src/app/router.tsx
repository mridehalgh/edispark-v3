import { createBrowserRouter } from 'react-router-dom'

import { DashboardPage } from '@/features/dashboard/dashboard-page'
import { RetailJourneyDetailPage, RetailJourneysPage } from '@/features/journeys/retail-journey-pages'
import { PlannedWorkbenchPage, WorkbenchShell } from '@/app/workbench-shell'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <WorkbenchShell />,
    children: [
      { index: true, element: <DashboardPage /> },
      {
        path: 'schemas',
        element: (
          <PlannedWorkbenchPage
            actionLabel="Review retail journeys"
            actionPath="/retail-journeys"
            description="Guided schema creation and version workflows are planned next. The shell keeps this area visible so navigation, status, and future workflow entry points stay stable."
            title="Schema workflow views are planned"
          />
        )
      },
      {
        path: 'document-sets',
        element: (
          <PlannedWorkbenchPage
            actionLabel="Inspect dashboard evidence"
            actionPath="/"
            description="Document-set list, detail, and intake workflows will mount here once the next task group lands."
            title="Document-set workflow views are planned"
          />
        )
      },
      { path: 'retail-journeys', element: <RetailJourneysPage /> },
      { path: 'retail-journeys/:journeyKey', element: <RetailJourneyDetailPage /> },
      {
        path: 'api-explorer',
        element: (
          <PlannedWorkbenchPage
            actionLabel="Return to dashboard"
            actionPath="/"
            description="The live OpenAPI explorer remains planned while the shell already exposes connection state and a direct contract link."
            title="API explorer view is planned"
          />
        )
      }
    ]
  }
])

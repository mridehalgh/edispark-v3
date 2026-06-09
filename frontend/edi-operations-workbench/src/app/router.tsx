import { createBrowserRouter } from 'react-router-dom'

import { DashboardPage } from '@/features/dashboard/dashboard-page'
import { DocumentSetDetailPage } from '@/features/document-sets/document-set-detail-page'
import { DocumentSetListPage } from '@/features/document-sets/document-set-list-page'
import { DocumentVersionPage } from '@/features/document-sets/document-version-page'
import { RetailJourneyDetailPage, RetailJourneysPage } from '@/features/journeys/retail-journey-pages'
import { SchemaWorkflowPage } from '@/features/schemas/schema-workflow-page'
import { PlannedWorkbenchPage, WorkbenchShell } from '@/app/workbench-shell'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <WorkbenchShell />,
    children: [
      { index: true, element: <DashboardPage /> },
      {
        path: 'schemas',
        element: <SchemaWorkflowPage />
      },
      {
        path: 'document-sets',
        element: <DocumentSetListPage />
      },
      { path: 'document-sets/:setId', element: <DocumentSetDetailPage /> },
      { path: 'document-sets/:setId/documents/:docId/versions/:versionNumber', element: <DocumentVersionPage /> },
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

import { Navigate, Route, Routes } from 'react-router-dom'

import { AppShell } from '@/components/app-shell'
import { ApiExplorerView } from '@/views/api-explorer-view'
import { DashboardView } from '@/views/dashboard-view'
import { DocumentSetsView } from '@/views/document-sets-view'
import { SchemaCentreView } from '@/views/schema-centre-view'
import { ValidationView } from '@/views/validation-view'

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route element={<Navigate replace to="/dashboard" />} path="/" />
        <Route element={<DashboardView />} path="/dashboard" />
        <Route element={<DocumentSetsView />} path="/document-sets" />
        <Route element={<DocumentSetsView />} path="/document-sets/:setId" />
        <Route element={<SchemaCentreView />} path="/schemas" />
        <Route element={<SchemaCentreView />} path="/schemas/:schemaId" />
        <Route element={<ValidationView />} path="/validation" />
        <Route element={<ApiExplorerView />} path="/api-explorer" />
      </Route>
    </Routes>
  )
}

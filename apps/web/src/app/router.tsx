'use client';

import React from 'react';
import {Navigate, Outlet, RouterProvider, createBrowserRouter, useParams, useRouteError} from "react-router-dom";
import Header from "@/components/layout/header";
import {AccountSettingsPage} from "@/app/account/settings";
import { FilesPage } from '@/app/files/files';
import { FilesList } from './files/list';
import { DocumentsList } from '@/app/documents/list';
import { BusinessDocumentPage } from '@/app/documents/document';
import { AuthGate, ForgotPasswordPage, ResetPasswordPage, SignInPage, SignUpPage, TwoFactorPage, WorkspaceSelectionPage } from "@/components/auth/auth-pages"
import { OrganizationSettingsPage } from "@/app/organization/settings"
import { InvitePage } from "@/components/auth/invite-page"
import { JwtDebugPage } from "@/app/debug/jwt"
import { MftPage } from "@/app/mft/mft"
import { MftDocsPage } from "@/app/mft/docs"
import { TradingNetworkPage } from "@/app/trading-network/trading-network"
import { MappingsPage } from "@/app/mappings/mappings"
import { MappingWorkspacePage } from "@/app/mappings/mapping-workspace"
import { RequireOrganizationAdmin, RequirePlatformAdmin } from "@/components/auth/access-gates"
import { SettingsNavigation } from "@/components/layout/settings-navigation"
import { LayoutBody } from "@/components/layout/layout"

const AppLayout = () => (
    <div className="min-h-screen bg-background">
      <a href="#main-content" className="fixed left-4 top-3 z-50 -translate-y-20 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground focus:translate-y-0">
        Skip to main content
      </a>
      <Header />
      <main id="main-content" className="min-h-screen pt-16 lg:pl-60">
          <Outlet />
      </main>
    </div>
);

const SettingsLayout = () => <><SettingsNavigation /><Outlet /></>

function LegacyMappingRedirect() {
  const { mappingId } = useParams<{ mappingId: string }>()
  return <Navigate to={mappingId ? `/settings/mappings/${mappingId}` : "/settings/mappings"} replace />
}

function LegacyInvoiceRedirect() {
  const { setId, documentId } = useParams<{ setId: string; documentId: string }>()
  return <Navigate to={`/documents/${setId}/${documentId}`} replace />
}

function PlatformBoundary() {
  return <LayoutBody className="mx-auto w-full max-w-[96rem] py-8"><h1 className="text-2xl font-semibold tracking-tight">Platform</h1></LayoutBody>
}

function RouterError() {
  const error = useRouteError()
  console.error(error)
  return <main className="grid min-h-screen place-items-center p-6"><section className="max-w-md text-center"><h1 className="text-xl font-semibold">Something went wrong</h1><p className="mt-2 text-sm text-muted-foreground">Refresh the page and try again. If the problem continues, contact your workspace administrator.</p></section></main>
}

function ReactRouterComponent() {
  const router = createBrowserRouter([
      {
        element: <AppLayout />,
        errorElement: <RouterError />,
        children: [
          {
            path: "/",
            element: <AuthGate><Navigate to="/documents" replace /></AuthGate>,
          },
          {
            path: "/account/settings",
            element: <AuthGate><AccountSettingsPage /></AuthGate>
          },
          {
            path: "/documents",
            element: <AuthGate><DocumentsList /></AuthGate>,
          },
          {
            path: "/documents/:setId/:documentId",
            element: <AuthGate><BusinessDocumentPage /></AuthGate>,
          },
          {
            path: "/documents/:setId/:documentId/invoice",
            element: <AuthGate><LegacyInvoiceRedirect /></AuthGate>,
          },
          {
            path: "/workspace/setup",
            element: <AuthGate><OrganizationSettingsPage setupOnly /></AuthGate>,
          },
          ...(process.env.NODE_ENV === "development" ? [{
            path: "/debug/jwt",
            element: <AuthGate><JwtDebugPage /></AuthGate>,
          }] : []),
          {
            element: <RequireOrganizationAdmin />,
            children: [
              { path: "/settings", element: <Navigate to="/settings/connections" replace /> },
              {
                element: <SettingsLayout />,
                children: [
                  { path: "/settings/connections", element: <TradingNetworkPage /> },
                  { path: "/settings/connections/transport", element: <MftPage /> },
                  { path: "/settings/mappings", element: <MappingsPage /> },
                  { path: "/settings/workspace", element: <OrganizationSettingsPage /> },
                ],
              },
              { path: "/settings/connections/guide", element: <MftDocsPage /> },
              { path: "/settings/mappings/:mappingId", element: <MappingWorkspacePage /> },
              { path: "/file", element: <FilesList /> },
              { path: "/file/:id", element: <FilesPage /> },
              { path: "/mft", element: <Navigate to="/settings/connections/transport" replace /> },
              { path: "/mft/docs", element: <Navigate to="/settings/connections/guide" replace /> },
              { path: "/trading-network", element: <Navigate to="/settings/connections" replace /> },
              { path: "/mappings", element: <Navigate to="/settings/mappings" replace /> },
              { path: "/mappings/:mappingId", element: <LegacyMappingRedirect /> },
              { path: "/organization", element: <Navigate to="/settings/workspace" replace /> },
            ],
          },
          {
            element: <RequirePlatformAdmin />,
            children: [{ path: "/platform", element: <PlatformBoundary /> }],
          },
        ]
      },
      { path: "/sign-in", element: <SignInPage /> },
      { path: "/sign-up", element: <SignUpPage /> },
      { path: "/forgot-password", element: <ForgotPasswordPage /> },
      { path: "/reset-password", element: <ResetPasswordPage /> },
      { path: "/two-factor", element: <TwoFactorPage /> },
      { path: "/invite", element: <InvitePage /> },
      { path: "/workspace", element: <WorkspaceSelectionPage /> },
  ]);


  return <RouterProvider router={router}/>
}


export default ReactRouterComponent;

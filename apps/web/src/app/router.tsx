'use client';

import React from 'react';
import {Navigate, Outlet, RouterProvider, createBrowserRouter, useParams, useRouteError} from "react-router-dom";
import Header from "@/components/layout/header";
import {AccountSettingsPage} from "@/app/account/settings";
import { FilesPage } from '@/app/files/files';
import { FilesList } from './files/list';
import { SendFilePage } from './files/send-file';
import { TransferPage } from './files/transfer';
import { DocumentsList } from '@/app/documents/list';
import { BusinessDocumentPage } from '@/app/documents/document';
import { AuthGate, ForgotPasswordPage, ResetPasswordPage, SignInPage, SignUpPage, TwoFactorPage, WorkspaceSelectionPage } from "@/components/auth/auth-pages"
import { OrganizationSettingsPage } from "@/app/organization/settings"
import { NewCertificatePage } from "@/app/connections/new-certificate"
import { ConnectionCertificatesPage } from "@/app/connections/certificate-list-page"
import { InvitePage } from "@/components/auth/invite-page"
import { JwtDebugPage } from "@/app/debug/jwt"
import { MftPage } from "@/app/mft/mft"
import { MftDocsPage } from "@/app/mft/docs"
import { TradingNetworkPage } from "@/app/trading-network/trading-network"
import { MappingsPage } from "@/app/mappings/mappings"
import { MappingWorkspacePage } from "@/app/mappings/mapping-workspace"
import { NewDocumentRulePage } from "@/app/mappings/new-rule"
import { PartnerPage } from "@/app/partners/partner"
import { PartnersPage } from "@/app/partners/partners"
import { NewPartnerPage } from "@/app/partners/new-partner"
import { OverviewPage } from "@/app/overview/overview"
import { ConnectionsPage } from "@/app/connections/connections"
import { ConnectionPage } from "@/app/connections/connection"
import { ConnectionEditorPage } from "@/app/connections/editor"
import { RequireOrganizationAdmin, RequirePlatformAdmin } from "@/components/auth/access-gates"
import { LayoutBody } from "@/components/layout/layout"
import { canManageOrganization } from "@/lib/access"
import { useAccess } from "@/lib/use-access"

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

function LegacyMappingRedirect() {
  const { mappingId } = useParams<{ mappingId: string }>()
  return <Navigate to={mappingId ? `/settings/mappings/${mappingId}` : "/settings/mappings"} replace />
}

function LegacyInvoiceRedirect() {
  const { setId, documentId } = useParams<{ setId: string; documentId: string }>()
  return <Navigate to={`/documents/${setId}/${documentId}`} replace />
}

function LegacyFileRedirect() {
  const { id } = useParams<{ id: string }>()
  return <Navigate to={id ? `/files/${id}` : "/files"} replace />
}

function PlatformBoundary() {
  return <LayoutBody className="mx-auto w-full max-w-[96rem] py-8"><h1 className="text-2xl font-semibold tracking-tight">Platform</h1></LayoutBody>
}

function HomeRedirect() {
  const { access, loading } = useAccess()
  if (loading) return <LayoutBody className="py-8"><div className="h-8 w-40 animate-pulse bg-muted" /></LayoutBody>
  return <Navigate to={canManageOrganization(access?.organizationRole) ? "/overview" : "/documents"} replace />
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
            element: <AuthGate><HomeRedirect /></AuthGate>,
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
              { path: "/partners", element: <PartnersPage /> },
              { path: "/partners/new", element: <NewPartnerPage /> },
              { path: "/partners/:partnerId", element: <PartnerPage /> },
              { path: "/overview", element: <OverviewPage /> },
              { path: "/files", element: <FilesList /> },
              { path: "/files/send", element: <SendFilePage /> },
              { path: "/files/transfers/:transferId", element: <TransferPage /> },
              { path: "/files/:id", element: <FilesPage /> },
              { path: "/connections", element: <ConnectionsPage /> },
              { path: "/connections/new", element: <ConnectionEditorPage /> },
              { path: "/connections/certificates", element: <ConnectionCertificatesPage /> },
              { path: "/connections/certificates/new", element: <NewCertificatePage /> },
              { path: "/connections/:connectionId", element: <ConnectionPage /> },
              { path: "/connections/:connectionId/edit", element: <ConnectionEditorPage /> },
              { path: "/settings", element: <Navigate to="/organisation/people" replace /> },
              { path: "/settings/workspace", element: <Navigate to="/organisation/people" replace /> },
              { path: "/settings/certificates", element: <Navigate to="/connections/certificates" replace /> },
              { path: "/settings/connections", element: <Navigate to="/connections" replace /> },
              { path: "/settings/connections/transport", element: <MftPage /> },
              { path: "/settings/connections/guide", element: <MftDocsPage /> },
              { path: "/settings/mappings", element: <Navigate to="/organisation/document-rules" replace /> },
              { path: "/settings/mappings/:mappingId", element: <MappingWorkspacePage /> },
              { path: "/organisation/people", element: <OrganizationSettingsPage initialTab="people" /> },
              { path: "/organisation/details", element: <OrganizationSettingsPage initialTab="settings" /> },
              { path: "/organisation/sign-in", element: <OrganizationSettingsPage initialTab="sso" /> },
              { path: "/organisation/certificates", element: <Navigate to="/connections/certificates" replace /> },
              { path: "/organisation/certificates/new", element: <Navigate to="/connections/certificates/new" replace /> },
              { path: "/organisation/document-rules", element: <MappingsPage /> },
              { path: "/organisation/document-rules/new", element: <NewDocumentRulePage /> },
              { path: "/organisation/document-rules/:mappingId", element: <MappingWorkspacePage /> },
              { path: "/settings/advanced/exchange", element: <TradingNetworkPage /> },
              { path: "/settings/evidence", element: <Navigate to="/files" replace /> },
              { path: "/settings/evidence/:id", element: <LegacyFileRedirect /> },
              { path: "/file", element: <Navigate to="/files" replace /> },
              { path: "/file/:id", element: <LegacyFileRedirect /> },
              { path: "/mft", element: <Navigate to="/connections" replace /> },
              { path: "/mft/docs", element: <Navigate to="/settings/connections/guide" replace /> },
              { path: "/trading-network", element: <Navigate to="/partners" replace /> },
              { path: "/mappings", element: <Navigate to="/organisation/document-rules" replace /> },
              { path: "/mappings/:mappingId", element: <LegacyMappingRedirect /> },
              { path: "/organization", element: <Navigate to="/organisation/people" replace /> },
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

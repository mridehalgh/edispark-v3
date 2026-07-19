'use client';

import React from 'react';
import {Outlet, RouterProvider, createBrowserRouter, useRouteError} from "react-router-dom";
import Home from "@/app/page";
import Header from "@/components/layout/header";
import {AccountSettingsPage} from "@/app/account/settings";
import { FilesPage } from '@/app/files/files';
import { FilesList } from './files/list';
import { AuthGate, ForgotPasswordPage, ResetPasswordPage, SignInPage, SignUpPage, WorkspaceSelectionPage } from "@/components/auth/auth-pages"
import { OrganizationSettingsPage } from "@/app/organization/settings"
import { InvitePage } from "@/components/auth/invite-page"

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
            element: <AuthGate><Home /></AuthGate>,
          },
          {
            path: "/account/settings",
            element: <AuthGate><AccountSettingsPage /></AuthGate>
          },
          {
            path: "/file",
            element: <AuthGate><FilesList /></AuthGate>,
          },
          {
            path: "/file/:id",
            element: <AuthGate><FilesPage /></AuthGate>,
          },
          {
            path: "/organization",
            element: <AuthGate><OrganizationSettingsPage /></AuthGate>,
          },
        ]
      },
      { path: "/sign-in", element: <SignInPage /> },
      { path: "/sign-up", element: <SignUpPage /> },
      { path: "/forgot-password", element: <ForgotPasswordPage /> },
      { path: "/reset-password", element: <ResetPasswordPage /> },
      { path: "/invite", element: <InvitePage /> },
      { path: "/workspace", element: <WorkspaceSelectionPage /> },
  ]);


  return <RouterProvider router={router}/>
}


export default ReactRouterComponent;

"use client"

import { AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { CertificateList } from "@/components/connections/certificate-list"
import { ConnectionsNavigation } from "@/components/connections/connections-navigation"

export function ConnectionCertificatesPage() {
  return <AdminPage className="max-w-[96rem]">
    <AdminPageHeader title="Connections" />
    <ConnectionsNavigation />
    <CertificateList />
  </AdminPage>
}

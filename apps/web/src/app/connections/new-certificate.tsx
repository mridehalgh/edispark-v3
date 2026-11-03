"use client"

import { ArrowLeft, Upload } from "lucide-react"
import { useState } from "react"
import { Link, useNavigate } from "react-router-dom"

import { AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { adminApi } from "@/lib/admin-api"

export function NewCertificatePage() {
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(undefined)
    try {
      await adminApi("/api/mft/certificates", { method: "POST", body: new FormData(event.currentTarget) })
      navigate("/connections/certificates")
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The certificate could not be added.") }
    finally { setBusy(false) }
  }

  return <AdminPage className="max-w-3xl"><Button asChild variant="ghost" className="-ml-3 mb-3"><Link to="/connections/certificates"><ArrowLeft className="mr-2 h-4 w-4" />Certificates</Link></Button><AdminPageHeader title="Add certificate" />{error ? <AdminNotice>{error}</AdminNotice> : null}<form onSubmit={submit} className="border-y"><div className="grid gap-5 px-1 py-6 sm:px-4"><label className="grid gap-1.5 text-sm font-medium">Certificate name<Input name="name" required autoFocus /></label><label className="grid gap-1.5 text-sm font-medium">Purpose<select name="kind" className="h-10 rounded-md border bg-background px-3 text-sm"><option value="PARTNER_PUBLIC">Remote public certificate</option>{process.env.NODE_ENV === "development" ? <option value="LOCAL_IDENTITY">Our test identity</option> : null}</select></label><label className="grid gap-1.5 text-sm font-medium">Certificate file<Input name="file" type="file" required accept=".p12,.pfx,.pem,.cer,.crt" /></label>{process.env.NODE_ENV === "development" ? <label className="grid gap-1.5 text-sm font-medium">Test identity password<Input name="password" type="password" autoComplete="new-password" /></label> : null}</div><div className="flex justify-end gap-2 border-t py-4"><Button asChild type="button" variant="ghost"><Link to="/connections/certificates">Cancel</Link></Button><Button disabled={busy}><Upload className="mr-2 h-4 w-4" />{busy ? "Checking…" : "Add certificate"}</Button></div></form></AdminPage>
}

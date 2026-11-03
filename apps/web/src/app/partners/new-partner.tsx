"use client"

import type { TradingPartner } from "@edispark/integration-client"
import { ArrowLeft, Plus } from "lucide-react"
import { useState } from "react"
import { Link, useNavigate } from "react-router-dom"

import { AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { adminApi } from "@/lib/admin-api"

export function NewPartnerPage() {
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(undefined)
    const form = new FormData(event.currentTarget)
    try {
      const partner = await adminApi<TradingPartner>("/api/integration/partners", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ displayName: String(form.get("displayName")).trim(), identities: [{ scheme: form.get("scheme"), value: String(form.get("value")).trim() }] }) })
      navigate(partner.id ? `/partners/${partner.id}` : "/partners")
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "The partner could not be created.") }
    finally { setBusy(false) }
  }

  return <AdminPage className="max-w-3xl"><Button asChild variant="ghost" className="-ml-3 mb-3"><Link to="/partners"><ArrowLeft className="mr-2 h-4 w-4" />Partners</Link></Button><AdminPageHeader title="Add partner" />{error ? <AdminNotice>{error}</AdminNotice> : null}<form onSubmit={submit} className="border-y"><div className="grid gap-5 px-1 py-6 sm:px-4"><label className="grid gap-1.5 text-sm font-medium">Business name<Input name="displayName" required autoFocus /></label><div className="grid gap-5 sm:grid-cols-[12rem_1fr]"><label className="grid gap-1.5 text-sm font-medium">Identifier type<select name="scheme" className="h-10 rounded-md border bg-background px-3 text-sm"><option value="GLN">GLN</option><option value="DUNS">DUNS</option><option value="EDI">EDI identifier</option></select></label><label className="grid gap-1.5 text-sm font-medium">Identifier<Input name="value" required /></label></div></div><div className="flex justify-end gap-2 border-t py-4"><Button asChild type="button" variant="ghost"><Link to="/partners">Cancel</Link></Button><Button disabled={busy}><Plus className="mr-2 h-4 w-4" />{busy ? "Creating…" : "Create partner"}</Button></div></form></AdminPage>
}

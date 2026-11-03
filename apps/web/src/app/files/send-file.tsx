"use client"

import type { PartnerResponse } from "@edispark/mft-client"
import { ArrowLeft, FileUp } from "lucide-react"
import { useEffect, useState } from "react"
import { Link, useNavigate, useSearchParams } from "react-router-dom"

import { AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { adminApi } from "@/lib/admin-api"

export function SendFilePage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [connections, setConnections] = useState<PartnerResponse[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    void adminApi<PartnerResponse[]>("/api/mft/partners")
      .then((items) => setConnections(items.filter((item) => item.status === "ACTIVE")))
      .catch((requestError) => setError(requestError instanceof Error ? requestError.message : "Connections could not be loaded."))
  }, [])

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(undefined)
    const form = new FormData(event.currentTarget)
    try {
      const transfer = await adminApi<{ id?: string }>("/api/mft/transfers", { method: "POST", body: form, headers: { "Idempotency-Key": crypto.randomUUID() } })
      navigate(transfer.id ? `/files/transfers/${transfer.id}` : "/files")
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "The file could not be sent.")
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminPage className="max-w-3xl">
      <Button asChild variant="ghost" className="-ml-3 mb-3"><Link to="/files"><ArrowLeft className="mr-2 h-4 w-4" />Files</Link></Button>
      <AdminPageHeader title="Send file" />
      {error ? <AdminNotice>{error}</AdminNotice> : null}
      <form onSubmit={submit} className="border-y">
        <div className="grid gap-5 px-1 py-6 sm:px-4">
          <label className="grid gap-1.5 text-sm font-medium">Connection<select name="partnerId" required defaultValue={searchParams.get("connection") ?? ""} className="h-10 rounded-md border bg-background px-3 text-sm"><option value="">Choose a connection</option>{connections.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label className="grid gap-1.5 text-sm font-medium">File<Input name="file" type="file" required accept=".edi,.x12,.dat,.txt,.xml,.csv,application/edi-x12,application/edifact,text/plain,text/csv,application/xml" /></label>
        </div>
        <div className="flex justify-end gap-2 border-t py-4"><Button asChild type="button" variant="ghost"><Link to="/files">Cancel</Link></Button><Button disabled={busy || connections.length === 0}><FileUp className="mr-2 h-4 w-4" />{busy ? "Sending…" : "Send file"}</Button></div>
      </form>
      {!error && connections.length === 0 ? <p className="mt-5 text-sm text-muted-foreground">Add and activate a connection before sending a file.</p> : null}
    </AdminPage>
  )
}

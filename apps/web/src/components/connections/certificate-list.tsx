"use client"

import type { CertificateResponse } from "@edispark/mft-client"
import { KeyRound, RefreshCw, Trash2, Upload } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { Link } from "react-router-dom"

import { AdminEmptyState, AdminNotice } from "@/components/admin/admin-page"
import { Button } from "@/components/ui/button"

async function readApi<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: "no-store" })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.error?.message ?? "The request could not be completed.")
  return body as T
}

export function CertificateList() {
  const [certificates, setCertificates] = useState<CertificateResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  const refresh = useCallback(async () => {
    setError(undefined)
    try {
      setCertificates(await readApi<CertificateResponse[]>("/api/mft/certificates"))
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Certificates could not be loaded.")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  async function deleteCertificate(certificate: CertificateResponse) {
    if (!certificate.id) return
    setBusy(true); setError(undefined); setNotice(undefined)
    try {
      await readApi(`/api/mft/certificates/${certificate.id}`, { method: "DELETE" })
      setNotice("Unused certificate deleted."); await refresh()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "This certificate is still used by a connection.")
    } finally { setBusy(false) }
  }

  return (
    <section aria-labelledby="certificates-heading">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b pb-4">
        <div className="max-w-2xl"><h3 id="certificates-heading" className="text-base font-semibold">Certificates</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">Shared security credentials used by your partner connections. Your identities prove files came from you; partner certificates verify and encrypt files for them.</p></div>
        <div className="flex gap-2"><Button variant="ghost" size="icon" onClick={() => void refresh()} disabled={loading} aria-label="Refresh certificates"><RefreshCw className="h-4 w-4" /></Button><Button asChild><Link to="/connections/certificates/new"><Upload className="mr-2 h-4 w-4" />Add certificate</Link></Button></div>
      </div>

      {error ? <AdminNotice>{error}</AdminNotice> : null}
      {notice ? <AdminNotice tone="success">{notice}</AdminNotice> : null}

      <div className="mt-5 divide-y border-y">{certificates.map((certificate) => <div key={certificate.id} className="flex min-h-16 items-center gap-4 py-3"><KeyRound className="h-4 w-4 text-primary" aria-hidden="true" /><span className="min-w-0 flex-1"><span className="block truncate text-sm font-medium">{certificate.name}</span><span className="block text-xs text-muted-foreground">{certificate.kind === "LOCAL_IDENTITY" ? "Our identity" : "Partner certificate"}</span></span><span className="hidden text-sm text-muted-foreground sm:block">Expires {certificate.notAfter ? new Date(certificate.notAfter).toLocaleDateString("en-GB") : "unknown"}</span><Button variant="ghost" size="icon" onClick={() => void deleteCertificate(certificate)} disabled={busy} aria-label={`Delete ${certificate.name ?? "certificate"}`}><Trash2 className="h-4 w-4" /></Button></div>)}{!loading && !certificates.length ? <AdminEmptyState title="No certificates" /> : null}</div>
    </section>
  )
}

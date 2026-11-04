"use client"

import { useEffect, useState } from "react"
import { AlertCircle, ArrowLeft, Building2, FileText, RefreshCw } from "lucide-react"
import { Link, useParams } from "react-router-dom"

import { DocumentTechnicalDetails } from "@/components/documents/document-technical-details"
import { SendDocument } from "@/components/documents/send-document"
import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { getDerivativeContent, getDocumentSetDetail, getDocumentVersionContent } from "@/lib/documents-api"
import {
  parseOperationalDocumentJson,
  type OperationalDocumentKind,
  type OperationalDocumentViewModel,
} from "@/lib/ubl-operational-document"

function formatDate(value: string) {
  const parsed = new Date(`${value}T00:00:00`)
  return Number.isNaN(parsed.getTime())
    ? value
    : new Intl.DateTimeFormat("en-GB", { dateStyle: "medium" }).format(parsed)
}

function formatQuantity(value?: { value: number; unit?: string }) {
  return value ? `${value.value.toLocaleString("en-GB")}${value.unit ? ` ${value.unit}` : ""}` : "—"
}

function formatMoney(value?: { value: number; currency?: string }, fallback?: string) {
  if (!value) return "—"
  const currency = value.currency ?? fallback
  if (!currency) return value.value.toLocaleString("en-GB")
  try {
    return new Intl.NumberFormat("en-GB", { style: "currency", currency }).format(value.value)
  } catch {
    return `${value.value.toLocaleString("en-GB")} ${currency}`
  }
}

export function OperationalDocumentPage({ kind }: { kind: OperationalDocumentKind }) {
  const { setId, documentId } = useParams<{ setId: string; documentId: string }>()
  const [document, setDocument] = useState<OperationalDocumentViewModel>()
  const [representation, setRepresentation] = useState<string>()
  const [error, setError] = useState<string>()
  const [loading, setLoading] = useState(true)
  const [reloadKey, setReloadKey] = useState(0)
  const [lifecycleStatus, setLifecycleStatus] = useState("FINAL")

  useEffect(() => {
    const controller = new AbortController()
    if (!setId || !documentId) {
      setError("The document identifier is missing.")
      setLoading(false)
      return () => controller.abort()
    }
    setLoading(true)
    setError(undefined)
    setDocument(undefined)
    void getDocumentSetDetail(setId, controller.signal)
      .then(async (detail) => {
        const metadata = detail.documents.find((item) => item.id === documentId)
        if (!metadata || metadata.type !== kind) throw new Error("The requested business document type does not match.")
        setLifecycleStatus(metadata.lifecycleStatus ?? "FINAL")
        const candidates: Array<{ label: string; load: () => Promise<Blob> }> = []
        if (metadata.currentVersion?.format === "JSON" && metadata.currentVersion.versionNumber) {
          candidates.push({
            label: `Source version ${metadata.currentVersion.versionNumber}`,
            load: () => getDocumentVersionContent(setId, documentId, metadata.currentVersion!.versionNumber!, controller.signal),
          })
        }
        ;[...(metadata.derivatives ?? [])]
          .filter((item) => item.targetFormat === "JSON" && item.id)
          .sort((left, right) => (right.createdAt ?? "").localeCompare(left.createdAt ?? ""))
          .forEach((item) => candidates.push({
            label: "Generated UBL JSON",
            load: () => getDerivativeContent(setId, documentId, item.id!, controller.signal),
          }))
        let reason = "No UBL JSON representation is available."
        for (const candidate of candidates) {
          const parsed = parseOperationalDocumentJson(await (await candidate.load()).text(), kind)
          if (parsed.ok) {
            setDocument(parsed.document)
            setRepresentation(candidate.label)
            return
          }
          reason = parsed.reason
        }
        throw new Error(reason)
      })
      .catch((caught: unknown) => {
        if (!(caught instanceof DOMException && caught.name === "AbortError")) {
          setError(caught instanceof Error ? caught.message : "The document could not be loaded.")
        }
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [documentId, kind, reloadKey, setId])

  if (loading) return <LayoutBody className="mx-auto w-full max-w-[96rem] py-8"><p className="text-sm text-muted-foreground">Loading document…</p></LayoutBody>
  if (!document || error) return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-8">
      <Link to="/documents" className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary hover:underline"><ArrowLeft className="h-4 w-4" aria-hidden="true" />Back to documents</Link>
      <section className="mt-8 max-w-xl rounded-lg border bg-card p-6" role="alert">
        <AlertCircle className="h-5 w-5 text-destructive" aria-hidden="true" />
        <h1 className="mt-3 text-lg font-semibold">Document unavailable</h1>
        <p className="mt-2 text-sm text-muted-foreground">{error}</p>
        <Button className="mt-5 gap-2" variant="outline" onClick={() => setReloadKey((value) => value + 1)}><RefreshCw className="h-4 w-4" aria-hidden="true" />Try again</Button>
      </section>
    </LayoutBody>
  )

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
      <Link to="/documents" className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary hover:underline"><ArrowLeft className="h-4 w-4" aria-hidden="true" />Back to documents</Link>
      <header className="mt-5 flex flex-col justify-between gap-4 border-b pb-6 sm:flex-row sm:items-start">
        <div><p className="text-xs text-muted-foreground">{document.title}</p><h1 className="mt-1 text-2xl font-semibold tracking-tight">{document.id}</h1><p className="mt-2 text-sm text-muted-foreground">Issued {formatDate(document.issueDate)}{document.reference ? ` · Reference ${document.reference}` : ""}</p></div>
        <SendDocument documentSetId={setId!} documentId={documentId!} lifecycleStatus={lifecycleStatus} />
      </header>

      <section className="grid gap-6 border-b py-6 md:grid-cols-3" aria-label="Document summary">
        <div><h2 className="text-sm font-semibold">Status</h2><p className="mt-2 text-sm">{document.status ?? "Recorded"}</p></div>
        {document.totals.map((total) => <div key={total.label}><h2 className="text-sm font-semibold">{total.label}</h2><p className="mt-2 text-lg font-semibold tabular-nums">{formatMoney(total.amount, document.currency)}</p></div>)}
        {document.notes.length > 0 && <div><h2 className="text-sm font-semibold">Notes</h2>{document.notes.map((note) => <p key={note} className="mt-2 text-sm text-muted-foreground">{note}</p>)}</div>}
      </section>

      {document.parties.length > 0 && <section className="grid gap-8 border-b py-6 md:grid-cols-2" aria-labelledby="parties-title"><h2 id="parties-title" className="sr-only">Parties</h2>{document.parties.map(({ label, party }) => <article key={label}><div className="flex items-center gap-2"><Building2 className="h-4 w-4 text-primary" aria-hidden="true" /><h3 className="text-sm font-semibold">{label}</h3></div><p className="mt-3 font-medium">{party.name ?? party.legalName ?? "Name not supplied"}</p>{party.endpointId && <p className="mt-1 font-mono text-xs text-muted-foreground">{party.endpointScheme ?? "Endpoint"}: {party.endpointId}</p>}</article>)}</section>}

      <section className="py-6" aria-labelledby="lines-title">
        <h2 id="lines-title" className="text-base font-semibold">Lines</h2>
        {document.lines.length === 0 ? <p className="mt-3 text-sm text-muted-foreground">No line-level details were supplied.</p> : <div className="mt-4 overflow-x-auto rounded-lg border"><table className="w-full min-w-[48rem] text-left text-sm"><thead className="border-b bg-muted/55 text-xs text-muted-foreground"><tr><th className="px-4 py-2.5">Line</th><th className="px-4 py-2.5">Item or reference</th><th className="px-4 py-2.5">Quantity</th><th className="px-4 py-2.5">Difference</th><th className="px-4 py-2.5">Amount</th></tr></thead><tbody className="divide-y">{document.lines.map((line) => <tr key={line.id}><td className="px-4 py-3 font-mono text-xs">{line.id}</td><td className="px-4 py-3"><span className="block font-medium">{line.name ?? line.reference ?? "Line detail"}</span>{line.description && <span className="mt-1 block text-xs text-muted-foreground">{line.description}</span>}</td><td className="px-4 py-3 tabular-nums">{formatQuantity(line.quantity)}</td><td className="px-4 py-3 tabular-nums">{formatQuantity(line.secondaryQuantity)}</td><td className="px-4 py-3 tabular-nums">{formatMoney(line.balance ?? line.credit ?? line.debit, document.currency)}</td></tr>)}</tbody></table></div>}
      </section>

      <details className="border-t py-6"><summary className="flex min-h-11 cursor-pointer items-center gap-2 text-sm font-semibold"><FileText className="h-4 w-4 text-primary" aria-hidden="true" />All populated UBL fields ({document.populatedFields.length})</summary><dl className="mt-4 divide-y rounded-lg border">{document.populatedFields.map((field, index) => <div key={`${field.path}-${index}`} className="grid gap-1 px-4 py-3 md:grid-cols-[minmax(16rem,2fr)_3fr]"><dt className="break-all font-mono text-xs text-muted-foreground">{field.path}</dt><dd className="break-words text-sm">{field.value}</dd></div>)}</dl></details>
      <DocumentTechnicalDetails setId={setId!} representation={representation} />
    </LayoutBody>
  )
}


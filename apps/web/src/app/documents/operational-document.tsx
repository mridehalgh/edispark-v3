"use client"

import { useEffect, useState } from "react"
import { AlertCircle, ArrowDown, Building2, ChevronDown, Download, RefreshCw } from "lucide-react"
import { useParams } from "react-router-dom"

import { DocumentTechnicalDetails } from "@/components/documents/document-technical-details"
import { DocumentAttention } from "@/components/documents/document-attention"
import { DocumentActivity } from "@/components/documents/exchange-timeline"
import { CreateNextDocument } from "@/components/documents/create-next-document"
import { SendDocument } from "@/components/documents/send-document"
import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
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

type LabelFormat = "PDF" | "ZPL"

export function OperationalDocumentPage({ kind }: { kind: OperationalDocumentKind }) {
  const { setId, documentId } = useParams<{ setId: string; documentId: string }>()
  const [document, setDocument] = useState<OperationalDocumentViewModel>()
  const [representation, setRepresentation] = useState<string>()
  const [error, setError] = useState<string>()
  const [loading, setLoading] = useState(true)
  const [reloadKey, setReloadKey] = useState(0)
  const [lifecycleStatus, setLifecycleStatus] = useState("FINAL")
  const [versionNumber, setVersionNumber] = useState<number>()
  const [labelBusy, setLabelBusy] = useState(false)
  const [labelError, setLabelError] = useState<string>()
  const [labelDerivatives, setLabelDerivatives] = useState<Partial<Record<LabelFormat, string>>>({})

  useEffect(() => {
    const controller = new AbortController()
    if (!setId || !documentId) {
      setError("The document identifier is missing.")
      setLoading(false)
      return () => controller.abort()
    }
    setLoading(true)
    setError(undefined)
    setLabelError(undefined)
    setLabelDerivatives({})
    setDocument(undefined)
    void getDocumentSetDetail(setId, controller.signal)
      .then(async (detail) => {
        const metadata = detail.documents.find((item) => item.id === documentId)
        if (!metadata || metadata.type !== kind) throw new Error("The requested business document type does not match.")
        setLifecycleStatus(metadata.lifecycleStatus ?? "FINAL")
        setVersionNumber(metadata.currentVersion?.versionNumber)
        const currentVersionId = metadata.currentVersion?.id
        const existingLabels: Partial<Record<LabelFormat, string>> = {}
        ;[...(metadata.derivatives ?? [])]
          .filter((item) => !currentVersionId || item.sourceVersionId === currentVersionId)
          .forEach((item) => {
            if ((item.targetFormat === "PDF" || item.targetFormat === "ZPL") && item.id) {
              existingLabels[item.targetFormat] = item.id
            }
          })
        setLabelDerivatives(existingLabels)
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
            label: "Prepared document",
            load: () => getDerivativeContent(setId, documentId, item.id!, controller.signal),
          }))
        let reason = "No readable version of this document is available."
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

  async function createLabels(format: LabelFormat) {
    if (!setId || !documentId || !versionNumber) return
    const existing = labelDerivatives[format]
    if (existing) {
      window.location.assign(`/api/documents/${setId}/documents/${documentId}/derivatives/${existing}/content`)
      return
    }
    setLabelBusy(true)
    setLabelError(undefined)
    try {
      const response = await fetch(`/api/documents/${setId}/documents/${documentId}/derivatives`, {
        method: "POST", credentials: "include", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sourceVersionNumber: versionNumber, targetFormat: format }),
      })
      const body = await response.json() as { id?: string; error?: { message?: string } }
      if (!response.ok || !body.id) throw new Error(body.error?.message ?? "The labels could not be generated.")
      setLabelDerivatives((current) => ({ ...current, [format]: body.id }))
      window.location.assign(`/api/documents/${setId}/documents/${documentId}/derivatives/${body.id}/content`)
    } catch (caught) {
      setLabelError(caught instanceof Error ? caught.message : "The labels could not be generated.")
    } finally {
      setLabelBusy(false)
    }
  }

  if (loading) return <LayoutBody className="mx-auto w-full max-w-[96rem] pt-0"><p className="text-sm text-muted-foreground">Loading document…</p></LayoutBody>
  if (!document || error) return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] pt-0">
      <section className="mt-8 max-w-xl rounded-lg border bg-card p-6" role="alert">
        <AlertCircle className="h-5 w-5 text-destructive" aria-hidden="true" />
        <h1 className="mt-3 text-lg font-semibold">Document unavailable</h1>
        <p className="mt-2 text-sm text-muted-foreground">{error}</p>
        <Button className="mt-5 gap-2" variant="outline" onClick={() => setReloadKey((value) => value + 1)}><RefreshCw className="h-4 w-4" aria-hidden="true" />Try again</Button>
      </section>
    </LayoutBody>
  )

  const hasExistingLabels = Boolean(labelDerivatives.PDF || labelDerivatives.ZPL)
  const canCreateLabels = document.printableLabelCount > 0
  const hasSummaryDetails = document.totals.length > 0 || document.notes.length > 0
  const hasQuantity = document.lines.some((line) => line.quantity != null)
  const hasDifference = document.lines.some((line) => line.secondaryQuantity != null)
  const hasAmount = document.lines.some((line) => (line.balance ?? line.credit ?? line.debit) != null)

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] pt-0">
      <header className="flex flex-col justify-between gap-4 border-b pb-5 pt-5 sm:flex-row sm:items-end">
        <div className="min-w-0"><p className="text-sm font-semibold text-primary">{document.title}</p><h1 className="mt-1 break-words text-balance text-2xl font-semibold tracking-tight">{document.id}</h1><p className="mt-2 text-sm text-muted-foreground">Issued {formatDate(document.issueDate)}{document.reference ? ` · Reference ${document.reference}` : ""}</p></div>
        <div className="grid justify-items-start gap-2 sm:justify-items-end">
          <div className="flex flex-wrap items-start gap-2 sm:justify-end"><CreateNextDocument setId={setId!} documentId={documentId!} sourceType={kind} />{kind === "DESPATCH_ADVICE" ? <DropdownMenu><DropdownMenuTrigger asChild><Button variant="outline" disabled={labelBusy || (!canCreateLabels && !hasExistingLabels)} aria-describedby={!canCreateLabels && !hasExistingLabels ? "labels-unavailable" : undefined}><Download className="mr-2 h-4 w-4" />{labelBusy ? "Preparing labels…" : "Download labels"}<ChevronDown className="ml-2 h-3.5 w-3.5 text-muted-foreground" /></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuItem disabled={!canCreateLabels && !labelDerivatives.PDF} onSelect={() => void createLabels("PDF")}>PDF for printing</DropdownMenuItem><DropdownMenuItem disabled={!canCreateLabels && !labelDerivatives.ZPL} onSelect={() => void createLabels("ZPL")}>ZPL for label printers</DropdownMenuItem></DropdownMenuContent></DropdownMenu> : null}<SendDocument key={documentId} documentType={kind} documentSetId={setId!} documentId={documentId!} lifecycleStatus={lifecycleStatus} /></div>
          {kind === "DESPATCH_ADVICE" && !canCreateLabels && !hasExistingLabels ? <p id="labels-unavailable" className="max-w-xs text-xs text-muted-foreground sm:text-right">Add a valid SSCC to a handling unit to create labels.</p> : null}
        </div>
      </header>
      <DocumentAttention documentSetId={setId!} documentId={documentId!} />
      <a href="#document-activity" className="my-1 inline-flex min-h-11 items-center gap-1.5 text-sm font-medium text-primary underline-offset-4 hover:underline">Processing timeline <ArrowDown className="h-3.5 w-3.5" aria-hidden="true" /></a>
      {labelError ? <p className="mt-4 flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2.5 text-sm text-destructive" role="alert"><AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />{labelError}</p> : null}

      <section className="grid gap-4 rounded-lg bg-muted/65 px-4 py-4 sm:px-5 md:grid-cols-3" aria-label="Document summary">
        <div className={hasSummaryDetails ? undefined : "flex flex-wrap items-baseline gap-x-3 gap-y-1"}>
          <h2 className="text-sm font-semibold">Status</h2>
          <p className={hasSummaryDetails ? "mt-2 text-sm" : "text-sm"}>{document.status ?? "Recorded"}</p>
        </div>
        {document.totals.map((total) => <div key={total.label}><h2 className="text-sm font-semibold">{total.label}</h2><p className="mt-2 text-lg font-semibold tabular-nums">{formatMoney(total.amount, document.currency)}</p></div>)}
        {document.notes.length > 0 && <div><h2 className="text-sm font-semibold">Notes</h2>{document.notes.map((note) => <p key={note} className="mt-2 text-sm text-muted-foreground">{note}</p>)}</div>}
      </section>

      {document.parties.length > 0 && <section className="grid gap-8 border-b py-6 md:grid-cols-2" aria-labelledby="parties-title"><h2 id="parties-title" className="sr-only">Parties</h2>{document.parties.map(({ label, party }) => <article key={label}><div className="flex items-center gap-2"><Building2 className="h-4 w-4 text-primary" aria-hidden="true" /><h3 className="text-sm font-semibold">{label}</h3></div><p className="mt-3 font-medium">{party.name ?? party.legalName ?? "Name not supplied"}</p>{party.endpointId && <p className="mt-1 font-mono text-xs text-muted-foreground">{party.endpointScheme ?? "Endpoint"}: {party.endpointId}</p>}</article>)}</section>}

      <section className="py-6" aria-labelledby="lines-title">
        <h2 id="lines-title" className="text-base font-semibold">Lines</h2>
        {document.lines.length === 0 ? (
          <p className="mt-3 text-sm text-muted-foreground">No line-level details were supplied.</p>
        ) : (
          <div className="mt-4 overflow-x-auto rounded-lg border" role="region" aria-labelledby="lines-title" tabIndex={0}>
            <table className="w-full text-left text-sm">
              <thead className="border-b bg-muted/55 text-xs text-muted-foreground">
                <tr>
                  <th scope="col" className="w-16 px-3 py-2.5 sm:px-4">Line</th>
                  <th scope="col" className="px-3 py-2.5 sm:px-4">Item or reference</th>
                  {hasQuantity && <th scope="col" className="px-3 py-2.5 text-right sm:px-4">Quantity</th>}
                  {hasDifference && <th scope="col" className="px-3 py-2.5 text-right sm:px-4">Difference</th>}
                  {hasAmount && <th scope="col" className="px-3 py-2.5 text-right sm:px-4">Amount</th>}
                </tr>
              </thead>
              <tbody className="divide-y">
                {document.lines.map((line) => (
                  <tr key={line.id}>
                    <td className="px-3 py-3 font-mono text-xs sm:px-4">{line.id}</td>
                    <td className="px-3 py-3 [overflow-wrap:anywhere] sm:px-4">
                      <span className="block font-medium">{line.name ?? line.reference ?? "Line detail"}</span>
                      {line.description && <span className="mt-1 block text-xs text-muted-foreground">{line.description}</span>}
                    </td>
                    {hasQuantity && <td className="whitespace-nowrap px-3 py-3 text-right tabular-nums sm:px-4">{formatQuantity(line.quantity)}</td>}
                    {hasDifference && <td className="whitespace-nowrap px-3 py-3 text-right tabular-nums sm:px-4">{formatQuantity(line.secondaryQuantity)}</td>}
                    {hasAmount && <td className="whitespace-nowrap px-3 py-3 text-right tabular-nums sm:px-4">{formatMoney(line.balance ?? line.credit ?? line.debit, document.currency)}</td>}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <DocumentActivity key={`${setId}:${documentId}`} documentSetId={setId!} documentId={documentId!} />
      <DocumentTechnicalDetails setId={setId!} representation={representation} />
    </LayoutBody>
  )
}


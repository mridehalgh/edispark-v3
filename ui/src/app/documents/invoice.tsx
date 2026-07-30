"use client"

import { useEffect, useState } from "react"
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Building2,
  CreditCard,
  FileCode2,
  ReceiptText,
  RefreshCw,
} from "lucide-react"
import { Link, useParams } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import {
  getDerivativeContent,
  getDocumentSetDetail,
  getDocumentVersionContent,
} from "@/lib/documents-api"
import {
  parseUblInvoiceJson,
  type UblInvoiceViewModel,
} from "@/lib/ubl-invoice"
import type { UblAddress, UblMoney, UblParty, UblQuantity } from "@/lib/ubl-common"

function formatDate(value?: string) {
  if (!value) return "Not recorded"
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat("en-GB", { dateStyle: "medium" }).format(date)
}

function formatMoney(value?: UblMoney, fallbackCurrency?: string) {
  if (!value) return "—"
  const currency = value.currency ?? fallbackCurrency
  if (!currency) return new Intl.NumberFormat("en-GB", { maximumFractionDigits: 2 }).format(value.value)
  try {
    return new Intl.NumberFormat("en-GB", {
      style: "currency",
      currency,
      currencyDisplay: "symbol",
    }).format(value.value)
  } catch {
    return `${value.value.toLocaleString("en-GB")} ${currency}`
  }
}

function formatQuantity(value?: UblQuantity) {
  if (!value) return "—"
  return `${value.value.toLocaleString("en-GB")}${value.unit ? ` ${value.unit}` : ""}`
}

function addressLines(address?: UblAddress) {
  if (!address) return []
  return [
    ...address.lines,
    [address.city, address.region, address.postalCode].filter(Boolean).join(", "),
    address.country,
  ].filter((line): line is string => Boolean(line))
}

function PartyBlock({ label, party }: { label: string; party: UblParty }) {
  const lines = addressLines(party.address)
  return (
    <section aria-label={label} className="min-w-0">
      <div className="flex items-center gap-2 text-primary">
        <Building2 className="h-4 w-4" aria-hidden="true" />
        <h2 className="text-sm font-semibold text-foreground">{label}</h2>
      </div>
      <p className="mt-3 text-base font-semibold">{party.name ?? party.legalName ?? "Name not supplied"}</p>
      {party.legalName && party.legalName !== party.name && (
        <p className="mt-1 text-sm text-muted-foreground">{party.legalName}</p>
      )}
      <dl className="mt-3 space-y-2 text-sm">
        {party.endpointId && (
          <div>
            <dt className="inline text-muted-foreground">{party.endpointScheme ?? "Endpoint"}: </dt>
            <dd className="inline font-mono text-xs">{party.endpointId}</dd>
          </div>
        )}
        {party.identifiers.map((id) => (
          <div key={id}>
            <dt className="inline text-muted-foreground">Party ID: </dt>
            <dd className="inline font-mono text-xs">{id}</dd>
          </div>
        ))}
        {party.taxIds.map(({ id, scheme }) => (
          <div key={`${scheme ?? "tax"}-${id}`}>
            <dt className="inline text-muted-foreground">{scheme ?? "Tax ID"}: </dt>
            <dd className="inline font-mono text-xs">{id}</dd>
          </div>
        ))}
        {party.companyId && (
          <div>
            <dt className="inline text-muted-foreground">Company ID: </dt>
            <dd className="inline font-mono text-xs">{party.companyId}</dd>
          </div>
        )}
      </dl>
      {lines.length > 0 && (
        <address className="mt-3 text-sm not-italic leading-6 text-muted-foreground">
          {lines.map((line, index) => <span key={`${line}-${index}`} className="block">{line}</span>)}
        </address>
      )}
    </section>
  )
}

function MetaItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className="mt-1 text-sm">{value}</dd>
    </div>
  )
}

function InvoiceLoading() {
  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8" aria-label="Loading invoice">
      <div className="animate-pulse">
        <div className="h-4 w-28 rounded bg-muted" />
        <div className="mt-6 h-8 w-72 rounded bg-muted" />
        <div className="mt-3 h-4 w-48 rounded bg-muted" />
        <div className="mt-8 grid gap-6 border-y py-6 md:grid-cols-2">
          <div className="h-40 rounded bg-muted" />
          <div className="h-40 rounded bg-muted" />
        </div>
        <div className="mt-8 h-80 rounded bg-muted" />
      </div>
    </LayoutBody>
  )
}

export function InvoiceDocumentPage() {
  const { setId, documentId } = useParams<{ setId: string; documentId: string }>()
  const [invoice, setInvoice] = useState<UblInvoiceViewModel | null>(null)
  const [representation, setRepresentation] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    if (!setId || !documentId) {
      setError("The invoice identifier is missing.")
      setLoading(false)
      return () => controller.abort()
    }

    setLoading(true)
    setError(null)
    setInvoice(null)
    setRepresentation(null)

    void getDocumentSetDetail(setId, controller.signal)
      .then(async (detail) => {
        const document = detail.documents.find((item) => item.id === documentId)
        if (!document || document.type !== "INVOICE") {
          throw new Error("This business document is not an invoice.")
        }

        const candidates: Array<{ label: string; load: () => Promise<Blob> }> = []
        if (document.currentVersion?.format === "JSON" && document.currentVersion.versionNumber) {
          candidates.push({
            label: `Source version ${document.currentVersion.versionNumber}`,
            load: () => getDocumentVersionContent(
              setId,
              documentId,
              document.currentVersion!.versionNumber!,
              controller.signal,
            ),
          })
        }
        ;[...(document.derivatives ?? [])]
          .filter((derivative) => derivative.targetFormat === "JSON" && derivative.id)
          .sort((left, right) => (right.createdAt ?? "").localeCompare(left.createdAt ?? ""))
          .forEach((derivative) => {
            candidates.push({
              label: "Generated UBL JSON",
              load: () => getDerivativeContent(setId, documentId, derivative.id!, controller.signal),
            })
          })

        let lastReason = "No UBL JSON representation is available for this invoice."
        for (const candidate of candidates) {
          const parsed = parseUblInvoiceJson(await (await candidate.load()).text())
          if (parsed.ok) {
            setInvoice(parsed.invoice)
            setRepresentation(candidate.label)
            return
          }
          lastReason = parsed.reason
        }
        throw new Error(lastReason)
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return
        setError(requestError instanceof Error ? requestError.message : "The invoice could not be loaded.")
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [documentId, reloadKey, setId])

  if (loading) return <InvoiceLoading />

  if (error || !invoice) {
    return (
      <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
        <Link to="/documents" className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary hover:underline">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          Back to documents
        </Link>
        <section className="mt-8 max-w-xl rounded-lg border bg-card p-6">
          <AlertCircle className="h-6 w-6 text-destructive" aria-hidden="true" />
          <h1 className="mt-4 text-lg font-semibold">Invoice view unavailable</h1>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">{error ?? "This invoice could not be found."}</p>
          <div className="mt-5 flex flex-wrap gap-2">
            <Button onClick={() => setReloadKey((value) => value + 1)} className="gap-2">
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              Try again
            </Button>
            {setId && (
              <Button asChild variant="outline">
                <Link to={`/file/${setId}`}>Inspect file</Link>
              </Button>
            )}
          </div>
        </section>
      </LayoutBody>
    )
  }

  const totalRows = [
    ["Line net total", invoice.totals.lineExtension],
    ["Allowances", invoice.totals.allowance],
    ["Charges", invoice.totals.charge],
    ["Tax exclusive", invoice.totals.taxExclusive],
    ["Tax inclusive", invoice.totals.taxInclusive],
    ["Prepaid", invoice.totals.prepaid],
    ["Rounding", invoice.totals.rounding],
  ] as const

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
      <Link to="/documents" className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Back to documents
      </Link>

      <header className="mt-3 flex flex-col gap-5 border-b pb-6 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-accent px-2.5 py-1 text-xs font-medium text-primary">Invoice</span>
            {representation && <span className="text-xs text-muted-foreground">{representation}</span>}
          </div>
          <h1 className="mt-3 text-2xl font-semibold tracking-tight">{invoice.id}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Issued {formatDate(invoice.issueDate)}{invoice.issueTime ? ` at ${invoice.issueTime}` : ""}
          </p>
        </div>
        <div className="sm:text-right">
          <p className="text-xs font-medium text-muted-foreground">Amount due</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums">
            {formatMoney(invoice.totals.payable, invoice.currency)}
          </p>
          {invoice.dueDate && (
            <p className="mt-1 text-xs text-muted-foreground">Due {formatDate(invoice.dueDate)}</p>
          )}
        </div>
      </header>

      <dl className="grid gap-x-8 gap-y-5 border-b py-5 sm:grid-cols-2 lg:grid-cols-4">
        <MetaItem label="Order reference" value={invoice.orderReference ?? "Not supplied"} />
        <MetaItem label="Buyer reference" value={invoice.buyerReference ?? "Not supplied"} />
        <MetaItem label="Invoice type code" value={invoice.invoiceType ?? "Not supplied"} />
        <MetaItem label="Document currency" value={invoice.currency ?? "Not supplied"} />
      </dl>

      <div className="grid gap-8 border-b py-7 md:grid-cols-2">
        <PartyBlock label="Supplier" party={invoice.supplier} />
        <PartyBlock label="Bill to" party={invoice.customer} />
      </div>

      {(invoice.paymentMeans.length > 0 || invoice.paymentTerms.length > 0) && (
        <section aria-labelledby="payment-title" className="border-b py-7">
          <div className="flex items-center gap-2">
            <CreditCard className="h-4 w-4 text-primary" aria-hidden="true" />
            <h2 id="payment-title" className="text-base font-semibold">Payment</h2>
          </div>
          <div className="mt-4 grid gap-6 md:grid-cols-2">
            {invoice.paymentMeans.map((means, index) => (
              <article key={`${means.code}-${means.paymentId ?? index}`} className="grid gap-3 sm:grid-cols-2">
                <MetaItem label="Method" value={means.name ? `${means.name} (${means.code})` : means.code} />
                <MetaItem label="Payment reference" value={means.paymentId ?? "Not supplied"} />
                {means.dueDate && <MetaItem label="Payment due" value={formatDate(means.dueDate)} />}
                {means.accountId && <MetaItem label="Payee account" value={means.accountName ? `${means.accountName} · ${means.accountId}` : means.accountId} />}
              </article>
            ))}
            {invoice.paymentTerms.map((terms, index) => (
              <article key={`terms-${index}`} className="text-sm">
                <p className="text-xs font-medium text-muted-foreground">Terms</p>
                {terms.notes.length > 0
                  ? terms.notes.map((note, noteIndex) => <p key={`${note}-${noteIndex}`} className="mt-1 leading-6">{note}</p>)
                  : <p className="mt-1 text-muted-foreground">No written terms supplied.</p>}
                {terms.dueDate && <p className="mt-2 text-muted-foreground">Due {formatDate(terms.dueDate)}</p>}
              </article>
            ))}
          </div>
        </section>
      )}

      <section aria-labelledby="items-title" className="py-7">
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <ReceiptText className="h-4 w-4 text-primary" aria-hidden="true" />
            <h2 id="items-title" className="text-base font-semibold">Invoice lines</h2>
          </div>
          <span className="text-xs tabular-nums text-muted-foreground">
            {invoice.lines.length} {invoice.lines.length === 1 ? "line" : "lines"}
          </span>
        </div>

        <div className="mt-4 hidden overflow-hidden rounded-lg border md:block">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[68rem] text-left text-sm">
              <thead className="border-b bg-muted/55 text-xs text-muted-foreground">
                <tr>
                  <th scope="col" className="w-16 px-4 py-2.5 font-medium">Line</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Item</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Identifiers</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Quantity</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Unit price</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Tax</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Net amount</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {invoice.lines.map((line) => (
                  <tr key={line.id}>
                    <td className="px-4 py-3 font-mono text-xs tabular-nums">{line.id}</td>
                    <td className="px-4 py-3">
                      <span className="block font-medium">{line.name ?? "Unnamed item"}</span>
                      {line.description && <span className="mt-0.5 block max-w-md text-xs text-muted-foreground">{line.description}</span>}
                    </td>
                    <td className="px-4 py-3">
                      {line.sellerItemId && <span className="block font-mono text-xs">SKU {line.sellerItemId}</span>}
                      {line.standardItemId && (
                        <span className="mt-0.5 block font-mono text-xs text-muted-foreground">
                          {line.standardItemScheme ?? "Standard"} {line.standardItemId}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">{formatQuantity(line.quantity)}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{formatMoney(line.price, invoice.currency)}</td>
                    <td className="px-4 py-3 text-right tabular-nums">
                      {line.taxPercent ? `${line.taxPercent}%` : formatMoney(line.taxAmount, invoice.currency)}
                    </td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">{formatMoney(line.lineExtension, invoice.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="mt-4 divide-y rounded-lg border md:hidden">
          {invoice.lines.map((line) => (
            <article key={line.id} className="p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs text-muted-foreground">Line {line.id}</p>
                  <h3 className="mt-1 text-sm font-semibold">{line.name ?? "Unnamed item"}</h3>
                  {line.description && <p className="mt-1 text-xs leading-5 text-muted-foreground">{line.description}</p>}
                </div>
                <p className="shrink-0 text-sm font-semibold tabular-nums">{formatMoney(line.lineExtension, invoice.currency)}</p>
              </div>
              <dl className="mt-4 grid grid-cols-2 gap-3 text-xs">
                <MetaItem label="Quantity" value={formatQuantity(line.quantity)} />
                <MetaItem label="Unit price" value={formatMoney(line.price, invoice.currency)} />
                <MetaItem label="Seller SKU" value={line.sellerItemId ?? "Not supplied"} />
                <MetaItem label="Tax" value={line.taxPercent ? `${line.taxPercent}%` : formatMoney(line.taxAmount, invoice.currency)} />
              </dl>
            </article>
          ))}
        </div>
      </section>

      {invoice.taxes.some((tax) => tax.subtotals.length > 0) && (
        <section aria-labelledby="tax-title" className="border-t py-7">
          <h2 id="tax-title" className="text-base font-semibold">Tax breakdown</h2>
          <div className="mt-4 overflow-x-auto rounded-lg border">
            <table className="w-full min-w-[36rem] text-left text-sm">
              <thead className="border-b bg-muted/55 text-xs text-muted-foreground">
                <tr>
                  <th scope="col" className="px-4 py-2.5 font-medium">Category</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Scheme</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Taxable amount</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Rate</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Tax amount</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {invoice.taxes.flatMap((tax) => tax.subtotals).map((tax, index) => (
                  <tr key={`${tax.categoryId ?? "tax"}-${tax.percent ?? index}`}>
                    <td className="px-4 py-3">{tax.categoryId ?? "Not supplied"}</td>
                    <td className="px-4 py-3 text-muted-foreground">{tax.scheme ?? "Not supplied"}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{formatMoney(tax.taxableAmount, invoice.currency)}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{tax.percent ? `${tax.percent}%` : "—"}</td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">{formatMoney(tax.taxAmount, invoice.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <div className="grid gap-8 border-t py-7 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <section aria-labelledby="notes-title">
          <h2 id="notes-title" className="text-base font-semibold">Invoice notes</h2>
          {invoice.notes.length > 0 ? (
            <ul className="mt-3 space-y-2 text-sm leading-6 text-muted-foreground">
              {invoice.notes.map((note, index) => <li key={`${note}-${index}`}>{note}</li>)}
            </ul>
          ) : (
            <p className="mt-3 text-sm text-muted-foreground">No notes were supplied with this invoice.</p>
          )}
        </section>

        <section aria-labelledby="summary-title">
          <h2 id="summary-title" className="text-base font-semibold">Invoice summary</h2>
          <dl className="mt-3 divide-y border-y text-sm">
            {totalRows.flatMap(([label, value]) => value ? [(
              <div key={label} className="flex items-center justify-between gap-4 py-2.5">
                <dt className="text-muted-foreground">{label}</dt>
                <dd className="tabular-nums">{formatMoney(value, invoice.currency)}</dd>
              </div>
            )] : [])}
            {invoice.taxes.map((tax, index) => (
              <div key={`tax-total-${index}`} className="flex items-center justify-between gap-4 py-2.5">
                <dt className="text-muted-foreground">Tax total</dt>
                <dd className="tabular-nums">{formatMoney(tax.taxAmount, invoice.currency)}</dd>
              </div>
            ))}
            <div className="flex items-center justify-between gap-4 py-3 font-semibold">
              <dt>Amount due</dt>
              <dd className="tabular-nums">{formatMoney(invoice.totals.payable, invoice.currency)}</dd>
            </div>
          </dl>
        </section>
      </div>

      <footer className="flex flex-col gap-3 border-t py-5 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2">
          <FileCode2 className="h-4 w-4" aria-hidden="true" />
          <span>
            UBL {invoice.ublVersion ?? "version not supplied"}
            {invoice.profileId ? ` · ${invoice.profileId}` : ""}
          </span>
        </div>
        {setId && (
          <Link to={`/file/${setId}`} className="inline-flex min-h-11 items-center gap-1.5 font-medium text-primary hover:underline">
            Inspect source file
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        )}
      </footer>
    </LayoutBody>
  )
}

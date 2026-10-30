'use client'

import { useMemo, useState } from "react"
import { AlertCircle, ArrowRight, CheckCircle2, FileText, Search } from "lucide-react"
import { Link } from "react-router-dom"

import { DocumentStatus } from "@/components/documents/document-status"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { LayoutBody } from "@/components/layout/layout"
import { exchangeDocuments } from "@/lib/documents"

const attentionDocuments = exchangeDocuments.filter((document) => document.status === "attention")

export default function Home() {
  const [query, setQuery] = useState("")
  const [documentType, setDocumentType] = useState("all")

  const visibleDocuments = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    return exchangeDocuments.filter((document) => {
      const matchesQuery = !normalizedQuery || [document.reference, document.retailer, document.type]
        .some((value) => value.toLowerCase().includes(normalizedQuery))
      return matchesQuery && (documentType === "all" || document.type === documentType)
    })
  }, [documentType, query])

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Good morning</h1>
        <p className="mt-1 text-sm text-muted-foreground">Here’s what’s happening today.</p>
      </header>

      <section aria-label="Today’s summary" className="mb-6 flex flex-wrap items-center gap-x-0 gap-y-3 border-y bg-card py-3 text-sm">
        <Link to="/file?status=attention" className="flex min-h-11 items-center gap-2 border-r px-4 first:pl-0 hover:text-primary sm:px-6">
          <AlertCircle className="h-4 w-4 text-destructive" aria-hidden="true" />
          <span className="font-medium">Needs attention</span>
          <span className="tabular-nums text-destructive">{attentionDocuments.length}</span>
        </Link>
        <Link to="/file" className="flex min-h-11 items-center gap-2 border-r px-4 hover:text-primary sm:px-6">
          <FileText className="h-4 w-4 text-primary" aria-hidden="true" />
          <span className="font-medium">Documents exchanged</span>
          <span className="tabular-nums text-primary">128</span>
        </Link>
        <div className="flex min-h-11 items-center gap-2 px-4 sm:px-6">
          <CheckCircle2 className="h-4 w-4 text-success-foreground" aria-hidden="true" />
          <span className="font-medium text-success-foreground">All systems ready</span>
        </div>
      </section>

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_20rem]">
        <section aria-labelledby="exchange-title" className="min-w-0 overflow-hidden rounded-lg border bg-card">
          <div className="flex flex-col gap-3 border-b px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 id="exchange-title" className="text-base font-semibold">Today’s exchange</h2>
              <p className="mt-0.5 text-xs text-muted-foreground">Latest retailer documents, newest first</p>
            </div>
            <div className="flex gap-2">
              <label className="relative flex-1 sm:w-56 sm:flex-none">
                <span className="sr-only">Filter documents</span>
                <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Filter documents…" className="h-9 pl-8" />
              </label>
              <label>
                <span className="sr-only">Document type</span>
                <select value={documentType} onChange={(event) => setDocumentType(event.target.value)} className="h-9 rounded-md border bg-background px-2.5 text-xs font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                  <option value="all">All types</option>
                  <option value="Purchase order">Orders</option>
                  <option value="Order response">Responses</option>
                  <option value="Invoice">Invoices</option>
                </select>
              </label>
            </div>
          </div>

          <div className="hidden overflow-x-auto md:block">
            <table className="w-full min-w-[46rem] text-left text-sm">
              <thead className="border-b bg-muted/55 text-xs font-medium text-muted-foreground">
                <tr>
                  <th scope="col" className="w-20 px-4 py-2.5 font-medium">Time</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Document</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Retailer</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Status</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium"><span className="sr-only">Action</span></th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {visibleDocuments.map((document) => (
                  <tr key={document.id} className="group transition-colors hover:bg-muted/45">
                    <td className="whitespace-nowrap px-4 py-3 tabular-nums text-muted-foreground">{document.receivedAt}</td>
                    <td className="px-4 py-3">
                      <div className="font-medium">{document.type}</div>
                      <div className="mt-0.5 font-mono text-xs tabular-nums text-muted-foreground">{document.reference}</div>
                    </td>
                    <td className="px-4 py-3">{document.retailer}</td>
                    <td className="px-4 py-3"><DocumentStatus status={document.status} /></td>
                    <td className="px-4 py-3 text-right">
                      <Link to={`/file/${document.id}`} className="inline-flex min-h-11 items-center gap-1.5 text-xs font-medium text-primary hover:underline">
                        View document <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="divide-y md:hidden">
            {visibleDocuments.map((document) => (
              <Link key={document.id} to={`/file/${document.id}`} className="block px-4 py-3 transition-colors hover:bg-muted/45">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{document.type} · {document.retailer}</p>
                    <p className="mt-1 font-mono text-xs tabular-nums text-muted-foreground">{document.reference} · {document.receivedAt}</p>
                  </div>
                  <DocumentStatus status={document.status} />
                </div>
              </Link>
            ))}
          </div>

          {visibleDocuments.length === 0 && (
            <div className="px-4 py-10 text-center">
              <p className="text-sm font-medium">No documents match these filters</p>
              <p className="mt-1 text-xs text-muted-foreground">Clear the search or choose another document type.</p>
            </div>
          )}

          <div className="border-t px-4 py-3 text-center">
            <Link to="/file" className="inline-flex min-h-11 items-center gap-1.5 text-sm font-medium text-primary hover:underline">
              View all documents <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
          </div>
        </section>

        <aside aria-labelledby="attention-title" className="overflow-hidden rounded-lg border bg-card xl:sticky xl:top-20">
          <div className="flex items-center justify-between border-b px-4 py-3">
            <div>
              <h2 id="attention-title" className="text-base font-semibold">Needs attention</h2>
              <p className="mt-0.5 text-xs text-muted-foreground">Resolve these next</p>
            </div>
            <span className="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-destructive/10 px-1.5 text-xs font-semibold tabular-nums text-destructive">{attentionDocuments.length}</span>
          </div>
          <div className="divide-y">
            {attentionDocuments.map((document) => (
              <article key={document.id} className="px-4 py-4">
                <div className="flex gap-2.5">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-destructive" aria-hidden="true" />
                  <div className="min-w-0">
                    <h3 className="text-sm font-semibold">{document.issue}</h3>
                    <p className="mt-1 text-xs leading-5 text-muted-foreground">
                      {document.retailer} · <span className="font-mono tabular-nums">{document.reference}</span>
                    </p>
                  </div>
                </div>
                <Button asChild size="sm" className="mt-3 w-full">
                  <Link to={`/file/${document.id}`}>Review issue</Link>
                </Button>
              </article>
            ))}
          </div>
          <div className="border-t px-4 py-3">
            <Link to="/file?status=attention" className="flex min-h-11 items-center justify-between text-sm font-medium text-primary hover:underline">
              View all issues <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
          </div>
        </aside>
      </div>
    </LayoutBody>
  )
}

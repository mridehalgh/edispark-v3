'use client'

import { useMemo, useState } from "react"
import { ArrowRight, Download, Search } from "lucide-react"
import { Link, useSearchParams } from "react-router-dom"

import { DocumentStatus } from "@/components/documents/document-status"
import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { DocumentStatus as Status, exchangeDocuments } from "@/lib/documents"
import { cn } from "@/lib/utils"

const statusFilters: Array<{ label: string; value: "all" | Status }> = [
  { label: "All documents", value: "all" },
  { label: "Needs attention", value: "attention" },
  { label: "Ready", value: "ready" },
  { label: "Delivered", value: "delivered" },
]

export function FilesList() {
  const [searchParams] = useSearchParams()
  const requestedStatus = searchParams.get("status") as Status | null
  const [status, setStatus] = useState<"all" | Status>(requestedStatus ?? "all")
  const [query, setQuery] = useState(searchParams.get("q") ?? "")
  const [retailer, setRetailer] = useState("all")
  const retailers = Array.from(new Set(exchangeDocuments.map((document) => document.retailer)))

  const filteredDocuments = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    return exchangeDocuments.filter((document) => {
      const matchesStatus = status === "all" || document.status === status
      const matchesRetailer = retailer === "all" || document.retailer === retailer
      const matchesQuery = !normalizedQuery || [document.reference, document.relatedReference ?? "", document.retailer, document.type]
        .some((value) => value.toLowerCase().includes(normalizedQuery))
      return matchesStatus && matchesRetailer && matchesQuery
    })
  }, [query, retailer, status])

  const exportDocuments = () => {
    const header = ["Reference", "Type", "Retailer", "Direction", "Status", "Received"]
    const rows = filteredDocuments.map((item) => [item.reference, item.type, item.retailer, item.direction, item.status, item.receivedAt])
    const csv = [header, ...rows].map((row) => row.map((value) => `"${value.replace(/"/g, '""')}"`).join(",")).join("\n")
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }))
    const link = window.document.createElement("a")
    link.href = url
    link.download = "edi-spark-documents.csv"
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
      <header className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-medium text-muted-foreground">Exchange workspace</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">Documents</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">Follow every order, invoice, and response from arrival to delivery.</p>
        </div>
        <Button variant="outline" onClick={exportDocuments} className="gap-2 self-start sm:self-auto">
          <Download className="h-4 w-4" aria-hidden="true" /> Export CSV
        </Button>
      </header>

      <section aria-label="Document filters" className="mb-4 space-y-3">
        <div className="flex gap-1 overflow-x-auto border-b" role="tablist" aria-label="Filter by status">
          {statusFilters.map((filter) => (
            <button
              key={filter.value}
              type="button"
              role="tab"
              aria-selected={status === filter.value}
              onClick={() => setStatus(filter.value)}
              className={cn(
                "relative min-h-11 whitespace-nowrap px-3 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground",
                status === filter.value && "text-primary after:absolute after:inset-x-2 after:bottom-0 after:h-0.5 after:bg-primary"
              )}
            >
              {filter.label}
            </button>
          ))}
        </div>

        <div className="flex flex-col gap-2 sm:flex-row">
          <label className="relative flex-1 sm:max-w-md">
            <span className="sr-only">Search documents</span>
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search reference, retailer, or type…" className="pl-9" />
          </label>
          <label>
            <span className="sr-only">Filter by retailer</span>
            <select value={retailer} onChange={(event) => setRetailer(event.target.value)} className="h-10 w-full rounded-md border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:w-52">
              <option value="all">All retailers</option>
              {retailers.map((name) => <option key={name} value={name}>{name}</option>)}
            </select>
          </label>
        </div>
      </section>

      <section aria-labelledby="results-title" className="overflow-hidden rounded-lg border bg-card">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <h2 id="results-title" className="text-sm font-semibold">Document results</h2>
          <span className="text-xs tabular-nums text-muted-foreground">{filteredDocuments.length} documents</span>
        </div>

        <div className="hidden overflow-x-auto md:block">
          <table className="w-full min-w-[56rem] text-left text-sm">
            <thead className="border-b bg-muted/55 text-xs text-muted-foreground">
              <tr>
                <th scope="col" className="px-4 py-2.5 font-medium">Reference</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Type</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Retailer</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Direction</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Status</th>
                <th scope="col" className="px-4 py-2.5 text-right font-medium">Received</th>
                <th scope="col" className="px-4 py-2.5"><span className="sr-only">Action</span></th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {filteredDocuments.map((item) => (
                <tr key={item.id} className="group transition-colors hover:bg-muted/45">
                  <td className="px-4 py-3">
                    <span className="font-mono text-xs font-medium tabular-nums text-primary">{item.reference}</span>
                    {item.relatedReference && <span className="mt-0.5 block font-mono text-xs tabular-nums text-muted-foreground">For {item.relatedReference}</span>}
                  </td>
                  <td className="px-4 py-3 font-medium">{item.type}</td>
                  <td className="px-4 py-3">{item.retailer}</td>
                  <td className="px-4 py-3 text-muted-foreground">{item.direction}</td>
                  <td className="px-4 py-3"><DocumentStatus status={item.status} /></td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums text-muted-foreground">{item.receivedAt}</td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/file/${item.id}`} className="inline-flex min-h-11 items-center gap-1 text-xs font-medium text-primary hover:underline">
                      View <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="divide-y md:hidden">
          {filteredDocuments.map((item) => (
            <Link key={item.id} to={`/file/${item.id}`} className="block px-4 py-4 transition-colors hover:bg-muted/45">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{item.type} · {item.retailer}</p>
                  <p className="mt-1 font-mono text-xs tabular-nums text-primary">{item.reference}</p>
                </div>
                <DocumentStatus status={item.status} />
              </div>
              <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground">
                <span>{item.direction}</span>
                <time className="tabular-nums">{item.receivedAt}</time>
              </div>
            </Link>
          ))}
        </div>

        {filteredDocuments.length === 0 && (
          <div className="px-4 py-12 text-center">
            <p className="text-sm font-medium">No documents found</p>
            <p className="mt-1 text-xs text-muted-foreground">Try a different status, retailer, or search term.</p>
          </div>
        )}
      </section>
    </LayoutBody>
  )
}

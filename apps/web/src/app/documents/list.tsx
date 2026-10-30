"use client"

import { useMemo, useState } from "react"
import { ArrowRight, FileText, RefreshCw, Search } from "lucide-react"
import { Link, useSearchParams } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useDocumentSets } from "@/lib/use-document-sets"

type SupportedDocumentType = "ORDER" | "INVOICE"

interface BusinessDocumentSummary {
  setId: string
  documentId: string
  type: SupportedDocumentType
  reference: string
  name: string
  direction?: string
  standard?: string
  createdAt?: string
}

const supportedDocumentTypes = new Set<SupportedDocumentType>(["ORDER", "INVOICE"])

function documentLabel(type: SupportedDocumentType) {
  return type === "INVOICE" ? "Invoice" : "Order"
}

function documentHref(document: BusinessDocumentSummary) {
  const suffix = document.type === "INVOICE" ? "/invoice" : ""
  return `/documents/${document.setId}/${document.documentId}${suffix}`
}

function formatDate(value?: string) {
  if (!value) return "Not recorded"
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value))
}

export function DocumentsList() {
  const [searchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get("q") ?? "")
  const {
    documentSets,
    error,
    hasNext,
    loading,
    loadingMore,
    loadNextPage,
    reload,
  } = useDocumentSets()

  const documents = useMemo(() => documentSets.flatMap((documentSet): BusinessDocumentSummary[] => {
    if (!documentSet.id) return []
    return (documentSet.documents ?? []).flatMap((document) => {
      if (!document.id || !supportedDocumentTypes.has(document.type as SupportedDocumentType)) return []
      const type = document.type as SupportedDocumentType
      const metadata = documentSet.metadata ?? {}
      return [{
        setId: documentSet.id!,
        documentId: document.id,
        type,
        reference: metadata.businessDocumentNumber ?? metadata.name ?? document.id,
        name: metadata.name ?? `${documentLabel(type)} ${metadata.businessDocumentNumber ?? document.id.slice(0, 8)}`,
        direction: metadata.direction,
        standard: metadata.standard ?? metadata.sourceStandard,
        createdAt: documentSet.createdAt,
      }]
    })
  }), [documentSets])

  const visibleDocuments = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return documents
    return documents.filter((document) => [
      documentLabel(document.type),
      document.reference,
      document.name,
      document.direction,
      document.standard,
    ].some((value) => value?.toLowerCase().includes(normalized)))
  }, [documents, query])

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
      <header className="mb-7">
        <h1 className="text-2xl font-semibold tracking-tight">Documents</h1>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">
          Review business documents in the same familiar form used by your operations team.
        </p>
      </header>

      <label className="relative mb-4 block w-full sm:max-w-lg">
        <span className="sr-only">Search documents</span>
        <Search
          className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search reference, document type, name, or standard…"
          className="pl-9"
        />
      </label>

      {error && (
        <div className="mb-4 flex items-center justify-between gap-4 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">
          <span>{error}</span>
          <Button variant="outline" size="sm" onClick={reload} className="shrink-0 gap-2">
            <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
            Retry
          </Button>
        </div>
      )}

      <section aria-labelledby="documents-title" className="overflow-hidden rounded-lg border bg-card">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <div>
            <h2 id="documents-title" className="text-sm font-semibold">Business documents</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">Orders and invoices rendered from their UBL representation.</p>
          </div>
          <span className="text-xs tabular-nums text-muted-foreground">{visibleDocuments.length} shown</span>
        </div>

        {loading ? (
          <div className="divide-y" aria-label="Loading documents">
            {Array.from({ length: 5 }, (_, index) => (
              <div key={index} className="grid animate-pulse gap-3 px-4 py-4 md:grid-cols-[minmax(16rem,1fr)_10rem_10rem_8rem]">
                <span className="h-4 rounded bg-muted" />
                <span className="h-4 rounded bg-muted" />
                <span className="h-4 rounded bg-muted" />
                <span className="h-4 rounded bg-muted" />
              </div>
            ))}
          </div>
        ) : (
          <>
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full min-w-[48rem] text-left text-sm">
                <thead className="border-b bg-muted/55 text-xs text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-4 py-2.5 font-medium">Document</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Type</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Direction</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Source</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Received</th>
                    <th scope="col" className="px-4 py-2.5"><span className="sr-only">Action</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {visibleDocuments.map((document) => (
                    <tr key={`${document.setId}-${document.documentId}`} className="transition-colors hover:bg-muted/45">
                      <td className="px-4 py-3">
                        <span className="block font-medium">{document.name}</span>
                        <span className="mt-0.5 block font-mono text-xs tabular-nums text-muted-foreground">{document.reference}</span>
                      </td>
                      <td className="px-4 py-3">{documentLabel(document.type)}</td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {document.direction ? document.direction.toLowerCase().replace(/^./, (letter) => letter.toUpperCase()) : "Not recorded"}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{document.standard ?? "Not recorded"}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-muted-foreground">{formatDate(document.createdAt)}</td>
                      <td className="px-4 py-3 text-right">
                        <Link
                          to={documentHref(document)}
                          className="inline-flex min-h-11 items-center gap-1 text-xs font-medium text-primary hover:underline"
                        >
                          View {documentLabel(document.type).toLowerCase()}
                          <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="divide-y md:hidden">
              {visibleDocuments.map((document) => (
                <Link
                  key={`${document.setId}-${document.documentId}`}
                  to={documentHref(document)}
                  className="flex items-start gap-3 px-4 py-4 transition-colors hover:bg-muted/45"
                >
                  <FileText className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{document.name}</span>
                    <span className="mt-1 block text-xs font-medium text-primary">{documentLabel(document.type)}</span>
                    <span className="mt-1 block font-mono text-xs text-muted-foreground">{document.reference}</span>
                    <span className="mt-2 block text-xs text-muted-foreground">
                      {document.direction ?? "Direction not recorded"} · {document.standard ?? "Standard not recorded"}
                    </span>
                    <span className="mt-1 block text-xs text-muted-foreground">{formatDate(document.createdAt)}</span>
                  </span>
                  <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                </Link>
              ))}
            </div>
          </>
        )}

        {!loading && visibleDocuments.length === 0 && (
          <div className="px-4 py-14 text-center">
            <FileText className="mx-auto h-6 w-6 text-muted-foreground" aria-hidden="true" />
            <p className="mt-3 text-sm font-medium">No documents found</p>
            <p className="mt-1 text-xs text-muted-foreground">
              {documents.length === 0 ? "Orders and invoices will appear here when they are received." : "Try a different search term."}
            </p>
          </div>
        )}

        {hasNext && (
          <div className="border-t px-4 py-3 text-center">
            <Button variant="outline" size="sm" onClick={() => void loadNextPage()} disabled={loadingMore}>
              {loadingMore ? "Loading…" : "Load more documents"}
            </Button>
          </div>
        )}
      </section>
    </LayoutBody>
  )
}

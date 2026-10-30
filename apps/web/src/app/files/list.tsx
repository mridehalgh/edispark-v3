'use client'

import { useMemo, useState } from "react"
import {
  ArrowRight,
  Download,
  FileStack,
  RefreshCw,
  Search,
} from "lucide-react"
import { Link, useSearchParams } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { type DocumentSetSummary, useDocumentSets } from "@/lib/use-document-sets"

function documentTypeLabel(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ")
}

function formatDate(value?: string) {
  if (!value) return "Not recorded"
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value))
}

function setName(documentSet: DocumentSetSummary) {
  return documentSet.metadata?.name || `File set ${documentSet.id?.slice(0, 8) ?? "unknown"}`
}

function csvCell(value: unknown) {
  return `"${String(value ?? "").replace(/"/g, '""')}"`
}

export function FilesList() {
  const [searchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get("q") ?? "")
  const [documentType, setDocumentType] = useState("all")
  const {
    documentSets,
    error,
    hasNext,
    loading,
    loadingMore,
    loadNextPage,
    reload,
  } = useDocumentSets()

  const types = useMemo(() => {
    return Array.from(new Set(
      documentSets.flatMap((documentSet) =>
        (documentSet.documents ?? []).flatMap((document) => document.type ? [document.type] : []),
      ),
    )).sort()
  }, [documentSets])

  const filteredDocumentSets = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    return documentSets.filter((documentSet) => {
      const documents = documentSet.documents ?? []
      const matchesType = documentType === "all"
        || documents.some((document) => document.type === documentType)
      const searchableValues = [
        documentSet.id,
        documentSet.createdBy,
        ...Object.values(documentSet.metadata ?? {}),
        ...documents.flatMap((document) => [document.id, document.type]),
      ]
      const matchesQuery = !normalizedQuery
        || searchableValues.some((value) => value?.toLowerCase().includes(normalizedQuery))
      return matchesType && matchesQuery
    })
  }, [documentSets, documentType, query])

  const exportDocumentSets = () => {
    const header = ["File set", "Set ID", "File types", "Files", "Versions", "Created", "Created by"]
    const rows = filteredDocumentSets.map((documentSet) => {
      const documents = documentSet.documents ?? []
      return [
        setName(documentSet),
        documentSet.id,
        documents.flatMap((document) => document.type ? [documentTypeLabel(document.type)] : []).join("; "),
        documents.length,
        documents.reduce((total, document) => total + (document.versionCount ?? 0), 0),
        documentSet.createdAt,
        documentSet.createdBy,
      ]
    })
    const csv = [header, ...rows].map((row) => row.map(csvCell).join(",")).join("\n")
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }))
    const link = window.document.createElement("a")
    link.href = url
    link.download = "edi-spark-file-sets.csv"
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
      <header className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Files</h1>
          <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">
            Inspect source files, immutable versions, and generated derivatives for technical traceability.
          </p>
        </div>
        <Button
          variant="outline"
          onClick={exportDocumentSets}
          disabled={filteredDocumentSets.length === 0}
          className="gap-2 self-start sm:self-auto"
        >
          <Download className="h-4 w-4" aria-hidden="true" />
          Export current view
        </Button>
      </header>

      <section aria-label="File filters" className="mb-4 flex flex-col gap-2 sm:flex-row">
        <label className="relative flex-1 sm:max-w-lg">
          <span className="sr-only">Search file sets</span>
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search file name, identifier, creator, or type…"
            className="pl-9"
          />
        </label>
        <label>
          <span className="sr-only">Filter by file type</span>
          <select
            value={documentType}
            onChange={(event) => setDocumentType(event.target.value)}
            className="h-10 w-full rounded-md border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:w-56"
          >
            <option value="all">All file types</option>
            {types.map((type) => (
              <option key={type} value={type}>{documentTypeLabel(type)}</option>
            ))}
          </select>
        </label>
      </section>

      {error && (
        <div className="mb-4 flex items-center justify-between gap-4 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">
          <span>{error}</span>
          <Button variant="outline" size="sm" onClick={reload} className="shrink-0 gap-2">
            <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
            Retry
          </Button>
        </div>
      )}

      <section aria-labelledby="results-title" className="overflow-hidden rounded-lg border bg-card">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <div>
            <h2 id="results-title" className="text-sm font-semibold">File sets</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">A set keeps related source files, responses, and transformations correlated.</p>
          </div>
          <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
            {filteredDocumentSets.length} shown
          </span>
        </div>

        {loading ? (
          <div className="divide-y" aria-label="Loading file sets">
            {Array.from({ length: 6 }, (_, index) => (
              <div key={index} className="grid animate-pulse gap-3 px-4 py-4 md:grid-cols-[minmax(16rem,1fr)_12rem_7rem_10rem]">
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
              <table className="w-full min-w-[62rem] text-left text-sm">
                <thead className="border-b bg-muted/55 text-xs text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-4 py-2.5 font-medium">File set</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">File types</th>
                    <th scope="col" className="px-4 py-2.5 text-right font-medium">Files</th>
                    <th scope="col" className="px-4 py-2.5 text-right font-medium">Versions</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Created</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Created by</th>
                    <th scope="col" className="px-4 py-2.5"><span className="sr-only">Action</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {filteredDocumentSets.map((documentSet) => {
                    const documents = documentSet.documents ?? []
                    const documentTypes = Array.from(new Set(
                      documents.flatMap((document) => document.type ? [documentTypeLabel(document.type)] : []),
                    ))
                    const versions = documents.reduce((total, document) => total + (document.versionCount ?? 0), 0)

                    return (
                      <tr key={documentSet.id} className="group transition-colors hover:bg-muted/45">
                        <td className="px-4 py-3">
                          <span className="font-medium">{setName(documentSet)}</span>
                          <span className="mt-0.5 block font-mono text-xs tabular-nums text-muted-foreground">
                            {documentSet.id}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          {documentTypes.join(", ") || "No files"}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">{documents.length}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{versions}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-muted-foreground">{formatDate(documentSet.createdAt)}</td>
                        <td className="px-4 py-3 text-muted-foreground">{documentSet.createdBy || "Not recorded"}</td>
                        <td className="px-4 py-3 text-right">
                          {documentSet.id && (
                            <Link
                              to={`/file/${documentSet.id}`}
                              className="inline-flex min-h-11 items-center gap-1 text-xs font-medium text-primary hover:underline"
                            >
                              Open set
                              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                            </Link>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            <div className="divide-y md:hidden">
              {filteredDocumentSets.map((documentSet) => {
                const documents = documentSet.documents ?? []
                const versions = documents.reduce((total, document) => total + (document.versionCount ?? 0), 0)

                return documentSet.id ? (
                  <Link key={documentSet.id} to={`/file/${documentSet.id}`} className="block px-4 py-4 transition-colors hover:bg-muted/45">
                    <div className="flex items-start gap-3">
                      <FileStack className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{setName(documentSet)}</p>
                        <p className="mt-1 truncate font-mono text-xs text-muted-foreground">{documentSet.id}</p>
                        <p className="mt-3 text-xs text-muted-foreground">
                          {documents.length} {documents.length === 1 ? "file" : "files"} · {versions} {versions === 1 ? "version" : "versions"}
                        </p>
                        <time className="mt-1 block text-xs text-muted-foreground">{formatDate(documentSet.createdAt)}</time>
                      </div>
                      <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                    </div>
                  </Link>
                ) : null
              })}
            </div>
          </>
        )}

        {!loading && filteredDocumentSets.length === 0 && (
          <div className="px-4 py-14 text-center">
            <FileStack className="mx-auto h-6 w-6 text-muted-foreground" aria-hidden="true" />
            <p className="mt-3 text-sm font-medium">No file sets found</p>
            <p className="mt-1 text-xs text-muted-foreground">
              {documentSets.length === 0
                ? "File sets will appear here when the Documents API receives them."
                : "Try a different file type or search term."}
            </p>
          </div>
        )}

        {hasNext && (
          <div className="border-t px-4 py-3 text-center">
            <Button variant="outline" size="sm" onClick={() => void loadNextPage()} disabled={loadingMore}>
              {loadingMore ? "Loading…" : "Load more file sets"}
            </Button>
          </div>
        )}
      </section>
    </LayoutBody>
  )
}

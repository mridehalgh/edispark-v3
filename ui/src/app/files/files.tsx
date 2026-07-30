'use client'

import { useEffect, useMemo, useState } from "react"
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Clock3,
  Copy,
  Download,
  FileCode2,
  FileStack,
  RefreshCw,
} from "lucide-react"
import { Link, useParams } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  getDocumentSetDetail,
  getDerivativeContent,
  getDocumentVersionContent,
  type DocumentSetDetail,
} from "@/lib/documents-api"
import { cn } from "@/lib/utils"

function documentTypeLabel(value?: string) {
  if (!value) return "Unknown document"
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

function parseState(status?: string, errors = 0) {
  const normalized = status?.toLowerCase() ?? ""
  if (errors > 0 || normalized.includes("fail") || normalized.includes("error") || normalized.includes("invalid")) {
    return {
      label: status || "Parse failed",
      icon: AlertCircle,
      className: "bg-destructive/10 text-destructive",
    }
  }
  if (normalized.includes("success") || normalized.includes("parsed") || normalized.includes("complete")) {
    return {
      label: status || "Parsed",
      icon: CheckCircle2,
      className: "bg-success/45 text-success-foreground",
    }
  }
  return {
    label: status || "Parse status not recorded",
    icon: Clock3,
    className: "bg-muted text-muted-foreground",
  }
}

function readableContent(value: string, format?: string) {
  if (format !== "JSON") return value

  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function Definition({ label, value, mono = false }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className={cn("mt-1 text-sm", mono && "break-all font-mono text-xs tabular-nums")}>{value}</dd>
    </div>
  )
}

export function FilesPage() {
  const { id } = useParams<{ id: string }>()
  const [detail, setDetail] = useState<DocumentSetDetail | null>(null)
  const [selectedDocumentId, setSelectedDocumentId] = useState<string | null>(null)
  const [selectedDerivativeId, setSelectedDerivativeId] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState("overview")
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [content, setContent] = useState<string | null>(null)
  const [contentBlob, setContentBlob] = useState<Blob | null>(null)
  const [contentLoading, setContentLoading] = useState(false)
  const [contentError, setContentError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!id) {
      setError("The document set identifier is missing.")
      setLoading(false)
      return
    }

    const controller = new AbortController()
    setLoading(true)
    setError(null)

    void getDocumentSetDetail(id, controller.signal)
      .then((response) => {
        setDetail(response)
        setSelectedDocumentId((current) => current ?? response.documents[0]?.id ?? null)
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return
        setError(requestError instanceof Error ? requestError.message : "The document set could not be loaded.")
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [id, reloadKey])

  const selectedDocument = useMemo(() => {
    return detail?.documents.find((document) => document.id === selectedDocumentId)
      ?? detail?.documents[0]
      ?? null
  }, [detail?.documents, selectedDocumentId])

  const selectedDerivative = useMemo(() => {
    return selectedDocument?.derivatives?.find((derivative) => derivative.id === selectedDerivativeId) ?? null
  }, [selectedDerivativeId, selectedDocument?.derivatives])

  useEffect(() => {
    setSelectedDerivativeId(null)
    setActiveTab("overview")
  }, [selectedDocument?.id])

  useEffect(() => {
    setContent(null)
    setContentBlob(null)
    setContentError(null)
    setCopied(false)
  }, [selectedDerivativeId, selectedDocument?.id, selectedDocument?.currentVersion?.versionNumber])

  const loadContent = async () => {
    const documentId = selectedDocument?.id
    const versionNumber = selectedDocument?.currentVersion?.versionNumber
    if (!id || !documentId || contentLoading || (!selectedDerivative && !versionNumber)) return

    setContentLoading(true)
    setContentError(null)
    try {
      const blob = selectedDerivative?.id
        ? await getDerivativeContent(id, documentId, selectedDerivative.id)
        : await getDocumentVersionContent(id, documentId, versionNumber!)
      const format = selectedDerivative?.targetFormat ?? selectedDocument.currentVersion?.format
      setContentBlob(blob)
      setContent(format === "PDF" ? null : readableContent(await blob.text(), format))
    } catch (requestError) {
      setContentError(requestError instanceof Error ? requestError.message : "Content could not be loaded.")
    } finally {
      setContentLoading(false)
    }
  }

  const downloadContent = () => {
    if (!contentBlob || !selectedDocument) return
    const format = selectedDerivative?.targetFormat ?? selectedDocument.currentVersion?.format
    const extension = format?.toLowerCase() || "bin"
    const suffix = selectedDerivative?.id
      ? `derivative-${selectedDerivative.id}`
      : `v${selectedDocument.currentVersion?.versionNumber ?? 1}`
    const url = URL.createObjectURL(contentBlob)
    const link = window.document.createElement("a")
    link.href = url
    link.download = `${selectedDocument.id ?? "document"}-${suffix}.${extension}`
    link.click()
    URL.revokeObjectURL(url)
  }

  const copyContent = async () => {
    if (!content) return
    await navigator.clipboard.writeText(content)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }

  if (loading) {
    return (
      <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8" aria-label="Loading document set">
        <div className="animate-pulse">
          <div className="h-4 w-32 rounded bg-muted" />
          <div className="mt-6 h-8 w-72 rounded bg-muted" />
          <div className="mt-3 h-4 w-full max-w-xl rounded bg-muted" />
          <div className="mt-8 grid gap-5 lg:grid-cols-[17rem_minmax(0,1fr)]">
            <div className="h-80 rounded-lg bg-muted" />
            <div className="h-[32rem] rounded-lg bg-muted" />
          </div>
        </div>
      </LayoutBody>
    )
  }

  if (error || !detail) {
    return (
      <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
        <Link to="/file" className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary hover:underline">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          Back to documents
        </Link>
        <section className="mt-8 max-w-xl rounded-lg border bg-card p-6">
          <AlertCircle className="h-6 w-6 text-destructive" aria-hidden="true" />
          <h1 className="mt-4 text-lg font-semibold">Document set unavailable</h1>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">{error ?? "This document set could not be found."}</p>
          <Button className="mt-5 gap-2" onClick={() => setReloadKey((value) => value + 1)}>
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            Try again
          </Button>
        </section>
      </LayoutBody>
    )
  }

  const { documentSet } = detail
  const title = documentSet.metadata?.name || `Document set ${documentSet.id?.slice(0, 8) ?? ""}`
  const metadata = Object.entries(documentSet.metadata ?? {})
    .filter(([key]) => key !== "name" && key !== "description")
  const currentVersion = selectedDocument?.currentVersion
  const parseErrors = currentVersion?.parseErrors ?? []
  const currentParseState = parseState(currentVersion?.parseStatus, parseErrors.length)
  const ParseIcon = currentParseState.icon
  const activeFormat = selectedDerivative?.targetFormat ?? currentVersion?.format
  const representationLabel = selectedDerivative
    ? `${selectedDerivative.targetFormat ?? "Generated"} derivative`
    : "Source version"

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
      <Link to="/file" className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Back to documents
      </Link>

      <header className="mt-3 border-b pb-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            <p className="mt-1 max-w-3xl text-sm leading-6 text-muted-foreground">
              {documentSet.metadata?.description || "A correlated set of business documents and their immutable versions."}
            </p>
            <p className="mt-3 break-all font-mono text-xs tabular-nums text-muted-foreground">{documentSet.id}</p>
          </div>
          <div className="flex shrink-0 items-center gap-2 text-sm text-muted-foreground">
            <FileStack className="h-4 w-4 text-primary" aria-hidden="true" />
            {detail.documents.length} {detail.documents.length === 1 ? "document" : "documents"}
          </div>
        </div>
      </header>

      <dl className="grid gap-x-8 gap-y-5 border-b py-5 sm:grid-cols-2 lg:grid-cols-4">
        <Definition label="Created" value={formatDate(documentSet.createdAt)} />
        <Definition label="Created by" value={documentSet.createdBy || "Not recorded"} />
        {metadata.map(([key, value]) => (
          <Definition key={key} label={documentTypeLabel(key)} value={value} />
        ))}
      </dl>

      <div className="mt-6 grid items-start gap-5 lg:grid-cols-[17rem_minmax(0,1fr)]">
        <aside aria-labelledby="set-documents-title" className="overflow-hidden rounded-lg border bg-card">
          <div className="border-b px-4 py-3">
            <h2 id="set-documents-title" className="text-sm font-semibold">Documents in this set</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">Select a document to inspect its current version.</p>
          </div>
          <div className="divide-y">
            {detail.documents.map((document) => (
              <button
                key={document.id}
                type="button"
                onClick={() => setSelectedDocumentId(document.id ?? null)}
                aria-pressed={document.id === selectedDocument?.id}
                className={cn(
                  "flex min-h-16 w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/55",
                  document.id === selectedDocument?.id && "bg-accent text-accent-foreground",
                )}
              >
                <FileCode2 className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                <span className="min-w-0">
                  <span className="block text-sm font-medium">{documentTypeLabel(document.type)}</span>
                  <span className="mt-1 block truncate font-mono text-xs text-muted-foreground">{document.id}</span>
                  <span className="mt-1 block text-xs text-muted-foreground">
                    {document.versionCount ?? 0} {(document.versionCount ?? 0) === 1 ? "version" : "versions"}
                  </span>
                </span>
              </button>
            ))}
            {detail.documents.length === 0 && (
              <p className="px-4 py-8 text-center text-sm text-muted-foreground">This set has no documents.</p>
            )}
          </div>
        </aside>

        {selectedDocument ? (
          <section aria-labelledby="document-title" className="min-w-0 overflow-hidden rounded-lg border bg-card">
            <div className="flex flex-col gap-3 border-b px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <h2 id="document-title" className="text-base font-semibold">{documentTypeLabel(selectedDocument.type)}</h2>
                <p className="mt-1 break-all font-mono text-xs text-muted-foreground">{selectedDocument.id}</p>
              </div>
              <span className={cn("inline-flex w-fit items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium", currentParseState.className)}>
                <ParseIcon className="h-3.5 w-3.5" aria-hidden="true" />
                {currentParseState.label}
              </span>
            </div>

            <Tabs value={activeTab} onValueChange={setActiveTab}>
              <div className="border-b px-5">
                <TabsList className="h-auto justify-start gap-5 rounded-none bg-transparent p-0">
                  {["overview", "content", "derivatives"].map((tab) => (
                    <TabsTrigger
                      key={tab}
                      value={tab}
                      className="rounded-none px-2 py-3 text-sm capitalize shadow-none data-[state=active]:bg-accent data-[state=active]:text-primary data-[state=active]:shadow-none"
                    >
                      {tab}
                    </TabsTrigger>
                  ))}
                </TabsList>
              </div>

              <TabsContent value="overview" className="m-0 p-5">
                <dl className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
                  <Definition label="Schema" value={selectedDocument.schemaRef?.schemaId || "Not recorded"} mono />
                  <Definition label="Schema version" value={selectedDocument.schemaRef?.version || "Not recorded"} />
                  <Definition label="Version" value={currentVersion?.versionNumber ?? "Not recorded"} />
                  <Definition label="Format" value={currentVersion?.format || "Not recorded"} />
                  <Definition label="Message type" value={currentVersion?.messageType || "Not recorded"} />
                  <Definition label="Version created" value={formatDate(currentVersion?.createdAt)} />
                  <Definition label="Version created by" value={currentVersion?.createdBy || "Not recorded"} />
                  <Definition label="Content hash" value={currentVersion?.contentHash || "Not recorded"} mono />
                </dl>

                {parseErrors.length > 0 && (
                  <section aria-labelledby="parse-errors-title" className="mt-6 rounded-md bg-destructive/10 p-4">
                    <div className="flex items-center gap-2 text-destructive">
                      <AlertCircle className="h-4 w-4" aria-hidden="true" />
                      <h3 id="parse-errors-title" className="text-sm font-semibold">
                        {parseErrors.length} {parseErrors.length === 1 ? "parse issue" : "parse issues"}
                      </h3>
                    </div>
                    <ul className="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-destructive">
                      {parseErrors.map((parseError, index) => <li key={`${parseError}-${index}`}>{parseError}</li>)}
                    </ul>
                  </section>
                )}
              </TabsContent>

              <TabsContent value="content" className="m-0">
                <div className="border-b px-5 py-4">
                  <h3 className="text-sm font-semibold">Available representations</h3>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    Inspect the original source or a generated derivative without leaving this document.
                  </p>
                  <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="Document representations">
                    <Button
                      type="button"
                      size="sm"
                      variant={selectedDerivativeId === null ? "default" : "outline"}
                      aria-pressed={selectedDerivativeId === null}
                      onClick={() => setSelectedDerivativeId(null)}
                    >
                      Source · {currentVersion?.format ?? "Unknown"}
                    </Button>
                    {(selectedDocument.derivatives ?? []).map((derivative) => (
                      <Button
                        key={derivative.id}
                        type="button"
                        size="sm"
                        variant={derivative.id === selectedDerivativeId ? "default" : "outline"}
                        aria-pressed={derivative.id === selectedDerivativeId}
                        onClick={() => setSelectedDerivativeId(derivative.id ?? null)}
                      >
                        Derivative · {derivative.targetFormat ?? "Unknown"}
                      </Button>
                    ))}
                  </div>
                </div>
                <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3">
                  <div>
                    <h3 className="text-sm font-semibold">{representationLabel}</h3>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {selectedDerivative
                        ? `${selectedDerivative.transformationMethod ?? "Generated"} · ${activeFormat ?? "Unknown format"}`
                        : `Version ${currentVersion?.versionNumber ?? "—"} · ${activeFormat ?? "Unknown format"}`}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    {content && (
                      <Button variant="outline" size="sm" onClick={() => void copyContent()} className="gap-2">
                        <Copy className="h-3.5 w-3.5" aria-hidden="true" />
                        {copied ? "Copied" : "Copy"}
                      </Button>
                    )}
                    {contentBlob && (
                      <Button variant="outline" size="sm" onClick={downloadContent} className="gap-2">
                        <Download className="h-3.5 w-3.5" aria-hidden="true" />
                        Download
                      </Button>
                    )}
                  </div>
                </div>
                {contentError && <p className="m-5 rounded-md bg-destructive/10 p-3 text-sm text-destructive" role="alert">{contentError}</p>}
                {!contentBlob ? (
                  <div className="px-5 py-12 text-center">
                    <FileCode2 className="mx-auto h-6 w-6 text-muted-foreground" aria-hidden="true" />
                    <p className="mt-3 text-sm font-medium">Content is loaded only when requested</p>
                    <p className="mt-1 text-xs text-muted-foreground">The file remains behind the authenticated document boundary.</p>
                    <Button className="mt-5" onClick={() => void loadContent()} disabled={contentLoading || (!selectedDerivative && !currentVersion?.versionNumber)}>
                      {contentLoading ? "Loading content…" : `Load ${selectedDerivative ? "derivative" : "source"} content`}
                    </Button>
                  </div>
                ) : content !== null ? (
                  <pre className="max-h-[38rem] overflow-auto bg-muted/35 p-5 font-mono text-xs leading-6 text-foreground">{content}</pre>
                ) : (
                  <div className="px-5 py-12 text-center">
                    <p className="text-sm font-medium">Preview is not available for this format</p>
                    <p className="mt-1 text-xs text-muted-foreground">Download this representation to inspect it.</p>
                  </div>
                )}
              </TabsContent>

              <TabsContent value="derivatives" className="m-0">
                <div className="border-b px-5 py-3">
                  <h3 className="text-sm font-semibold">Generated derivatives</h3>
                  <p className="mt-0.5 text-xs text-muted-foreground">Transformations created from an immutable source version.</p>
                </div>
                <div className="divide-y">
                  {(selectedDocument.derivatives ?? []).map((derivative) => (
                    <div key={derivative.id} className="flex flex-col gap-4 px-5 py-4 xl:flex-row xl:items-center xl:justify-between">
                      <dl className="grid flex-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
                        <Definition label="Target format" value={derivative.targetFormat || "Not recorded"} />
                        <Definition label="Method" value={derivative.transformationMethod || "Not recorded"} />
                        <Definition label="Created" value={formatDate(derivative.createdAt)} />
                        <Definition label="Derivative ID" value={derivative.id || "Not recorded"} mono />
                      </dl>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="w-fit shrink-0"
                        onClick={() => {
                          setSelectedDerivativeId(derivative.id ?? null)
                          setActiveTab("content")
                        }}
                      >
                        View content
                      </Button>
                    </div>
                  ))}
                  {(selectedDocument.derivatives ?? []).length === 0 && (
                    <div className="px-5 py-12 text-center">
                      <p className="text-sm font-medium">No derivatives yet</p>
                      <p className="mt-1 text-xs text-muted-foreground">Generated XML, JSON, PDF, or EDI outputs will appear here.</p>
                    </div>
                  )}
                </div>
              </TabsContent>
            </Tabs>
          </section>
        ) : (
          <section className="rounded-lg border bg-card px-5 py-14 text-center">
            <p className="text-sm font-medium">No document selected</p>
            <p className="mt-1 text-xs text-muted-foreground">Choose a document from this set to inspect it.</p>
          </section>
        )}
      </div>
    </LayoutBody>
  )
}

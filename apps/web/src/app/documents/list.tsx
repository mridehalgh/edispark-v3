"use client"

import { Fragment, useEffect, useMemo, useRef, useState } from "react"
import type {
  ExchangeResponse,
  TradingPartner,
} from "@edispark/integration-client"
import {
  ArrowDownLeft,
  ArrowUpRight,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Clock3,
  FileText,
  GitBranch,
  Plus,
  Search,
  X,
} from "lucide-react"
import { Link, useSearchParams } from "react-router-dom"
import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ExchangeTimeline } from "@/components/documents/exchange-timeline"
import { documentExchanges } from "@/lib/exchange-timeline"
import { useDocumentSets } from "@/lib/use-document-sets"
import {
  documentLabels,
  groupDocuments,
  lineageRows,
  relationTargetKey,
  needsAttention,
  workspaceDocuments,
  type WorkspaceDocument,
} from "@/lib/document-workspace"
import { cn } from "@/lib/utils"

const statusLabels: Record<string, string> = {
  DRAFT: "Draft",
  CANCELLED: "Cancelled",
  STORED: "Stored",
  DELIVERED: "Delivered",
  FAILED: "Delivery failed",
  NEEDS_REVIEW: "Needs review",
  RECEIVED: "Received",
  AWAITING_RECEIPT: "Awaiting receipt",
}
function date(value?: string) {
  if (!value || !Number.isFinite(Date.parse(value))) return "Not recorded"
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value))
}
function href(document: WorkspaceDocument) {
  if (document.type === "CREDIT_NOTE") return `/file/${document.setId}`
  return `/documents/${document.setId}/${document.id}`
}
function Status({ document }: { document: WorkspaceDocument }) {
  const attention = needsAttention(document)
  const success = document.status === "DELIVERED"
  const Icon = attention
    ? CircleAlert
    : success
      ? CheckCircle2
      : document.status === "DRAFT" || document.status === "STORED"
        ? FileText
        : Clock3
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 text-xs font-medium",
        attention
          ? "text-destructive"
          : success
            ? "text-emerald-800"
            : "text-muted-foreground",
      )}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      {statusLabels[document.status] ?? "Processing"}
    </span>
  )
}

export function DocumentsList() {
  const [params] = useSearchParams()
  const [query, setQuery] = useState(params.get("q") ?? "")
  const [tab, setTab] = useState("all")
  const [grouped, setGrouped] = useState(true)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const selectionTrigger = useRef<HTMLButtonElement | null>(null)
  const [partner, setPartner] = useState("")
  const [type, setType] = useState("")
  const [direction, setDirection] = useState("")
  const [period, setPeriod] = useState("")
  const [exchanges, setExchanges] = useState<ExchangeResponse[]>([])
  const [partners, setPartners] = useState<TradingPartner[]>([])
  const [contextError, setContextError] = useState(false)
  const [contextRetry, setContextRetry] = useState(0)
  const {
    documentSets,
    error,
    hasNext,
    loading,
    loadingMore,
    loadNextPage,
    reload,
  } = useDocumentSets(true)

  useEffect(() => {
    const controller = new AbortController()
    setContextError(false)
    async function context() {
      try {
        const responses = await Promise.all(
          ["exchanges", "partners"].map((path) =>
            fetch(`/api/integration/${path}`, {
              signal: controller.signal,
              cache: "no-store",
            }),
          ),
        )
        if (responses.some((response) => !response.ok))
          throw new Error("Context unavailable")
        const [exchangeData, partnerData] = await Promise.all(
          responses.map((response) => response.json()),
        )
        if (!controller.signal.aborted) {
          setExchanges(exchangeData)
          setPartners(partnerData)
        }
      } catch {
        if (!controller.signal.aborted) setContextError(true)
      }
    }
    void context()
    return () => controller.abort()
  }, [contextRetry])

  const documents = useMemo(
    () =>
      workspaceDocuments(
        documentSets,
        exchanges,
        new Map(
          partners.map((item) => [
            item.id ?? "",
            item.displayName ?? "Unnamed partner",
          ]),
        ),
      ),
    [documentSets, exchanges, partners],
  )
  const groups = useMemo(() => groupDocuments(documents), [documents])
  // Reveal matches and their context when a filter changes. Users can still
  // collapse any section afterwards without losing their filter selection.
  useEffect(() => {
    if (query || partner || type || direction || period || tab !== "all") {
      setExpanded(new Set(["unlinked", ...groups.map((group) => group.key)]))
    }
  }, [query, partner, type, direction, period, tab, groups])
  const visible = useMemo(
    () =>
      documents.filter((document) => {
        if (tab === "attention" && !needsAttention(document)) return false
        if (tab === "drafts" && document.status !== "DRAFT") return false
        if (partner && document.partner !== partner) return false
        if (type && document.type !== type) return false
        if (direction && document.direction !== direction) return false
        if (
          period &&
          (!document.updatedAt ||
            Date.parse(document.updatedAt) <
              Date.now() - Number(period) * 86400000)
        )
          return false
        return `${document.reference} ${document.partner} ${documentLabels[document.type]} ${document.reason ?? ""}`
          .toLowerCase()
          .includes(query.trim().toLowerCase())
      }),
    [documents, tab, partner, type, direction, period, query],
  )
  const visibleKeys = new Set(visible.map((document) => document.key))
  const visibleGroups = groups.filter((group) =>
    group.documents.some((document) => visibleKeys.has(document.key)),
  )
  const selected = documents.find((document) => document.key === selectedKey)
  const selectedGroup = groups.find((group) =>
    group.documents.some((document) => document.key === selectedKey),
  )
  function toggle(key: string) {
    setExpanded((current) => {
      const next = new Set(current)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }
  function clearFilters() {
    setQuery("")
    setPartner("")
    setType("")
    setDirection("")
    setPeriod("")
    setTab("all")
  }
  const counts = {
    all: documents.length,
    attention: documents.filter(needsAttention).length,
    drafts: documents.filter((document) => document.status === "DRAFT").length,
  }

  function documentRow(document: WorkspaceDocument, depth = 0, child = false) {
    return (
      <tr
        key={document.key}
        className={cn(
          "border-b transition-colors hover:bg-muted/60",
          selectedKey === document.key && "bg-accent",
        )}
      >
        <td className="py-1 pl-3 pr-2">
          <button
            onClick={(event) => {
              selectionTrigger.current = event.currentTarget
              setSelectedKey(document.key)
            }}
            aria-pressed={selectedKey === document.key}
            className="flex min-h-11 w-full items-center gap-2 text-left"
            style={{
              paddingLeft: child ? `${Math.min(depth + 1, 4) * 16}px` : 0,
            }}
          >
            {child && (
              <span
                className="h-5 w-3 shrink-0 border-b border-l"
                aria-hidden="true"
              />
            )}
            <FileText
              className="h-4 w-4 shrink-0 text-muted-foreground"
              aria-hidden="true"
            />
            <span className="min-w-0">
              <span className="block break-words font-medium">
                {document.reference}
              </span>
              <span
                className={cn(
                  "block text-xs text-muted-foreground",
                  !child && "lg:hidden",
                )}
              >
                {child ? documentLabels[document.type] : document.partner}
              </span>
              <span className="block break-words text-xs text-muted-foreground lg:hidden">
                {document.direction === "Not recorded" ? "Direction not recorded" : document.direction.toLowerCase()}
                <span className="tabular-nums sm:hidden"> · {date(document.updatedAt)}</span>
              </span>
              {child && !visibleKeys.has(document.key) && (
                <span className="block text-xs text-muted-foreground">
                  Related context · outside filters
                </span>
              )}
            </span>
          </button>
        </td>
        <td className="hidden px-3 py-2 text-xs text-muted-foreground lg:table-cell">
          {child ? documentLabels[document.type] : document.partner}
          <span className="mt-1 flex items-center gap-1">
            {document.direction === "OUTBOUND" ? (
              <ArrowUpRight className="h-3 w-3" />
            ) : document.direction === "INBOUND" ? (
              <ArrowDownLeft className="h-3 w-3" />
            ) : null}
            {document.direction === "Not recorded"
              ? "Direction not recorded"
              : document.direction.toLowerCase()}
          </span>
        </td>
        <td className="px-3 py-2">
          <Status document={document} />
        </td>
        <td className="hidden whitespace-nowrap px-3 py-2 text-xs tabular-nums text-muted-foreground sm:table-cell">
          {date(document.updatedAt)}
        </td>
      </tr>
    )
  }

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-6 sm:py-7">
      <header className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">Documents</h1>
        <Button asChild className="motion-safe:transition-[background-color,transform] motion-safe:duration-150 motion-safe:ease-out motion-safe:active:scale-[0.97]">
          <Link to="/documents/new">
            <Plus className="mr-2 h-4 w-4" />
            Create document
          </Link>
        </Button>
      </header>
      <nav
        aria-label="Document views"
        className="mb-4 flex flex-wrap gap-x-4 border-b sm:gap-x-6"
      >
        {(
          [
            ["all", "All documents"],
            ["attention", "Needs attention"],
            ["drafts", "Drafts"],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            aria-current={tab === key ? "page" : undefined}
            onClick={() => setTab(key)}
            className={cn(
              "flex min-h-11 shrink-0 items-center gap-2 border-b-2 text-sm",
              tab === key
                ? "border-primary font-semibold text-primary"
                : "border-transparent text-muted-foreground hover:text-foreground",
            )}
          >
            {label}
            <span
              className={cn(
                "rounded-full px-1.5 py-0.5 text-xs tabular-nums",
                key === "attention" && counts[key]
                  ? "bg-destructive/10 text-destructive"
                  : "bg-muted",
              )}
            >
              {counts[key]}
            </span>
          </button>
        ))}
      </nav>
      <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg bg-muted/60 p-3">
        <label className="relative w-full min-w-0 xl:w-auto xl:min-w-48 xl:flex-1">
          <span className="sr-only">Search references or partners</span>
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search references or partners"
            className="min-h-11 bg-background pl-9"
          />
        </label>
        <select
          aria-label="Filter by partner"
          value={partner}
          onChange={(event) => setPartner(event.target.value)}
          className="min-h-11 w-[calc((100%-0.5rem)/2)] min-w-0 rounded-md border border-input bg-background px-2 text-sm sm:w-auto sm:max-w-52"
        >
          <option value="">All partners</option>
          {[...new Set(documents.map((document) => document.partner))]
            .sort()
            .map((name) => (
              <option key={name}>{name}</option>
            ))}
        </select>
        <select
          aria-label="Filter by document type"
          value={type}
          onChange={(event) => setType(event.target.value)}
          className="min-h-11 w-[calc((100%-0.5rem)/2)] min-w-0 rounded-md border border-input bg-background px-2 text-sm sm:w-auto sm:max-w-48"
        >
          <option value="">All types</option>
          {Object.entries(documentLabels).map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
        <select
          aria-label="Filter by direction"
          value={direction}
          onChange={(event) => setDirection(event.target.value)}
          className="min-h-11 w-[calc((100%-0.5rem)/2)] min-w-0 rounded-md border border-input bg-background px-2 text-sm sm:w-auto"
        >
          <option value="">All directions</option>
          <option value="INBOUND">Inbound (received)</option>
          <option value="OUTBOUND">Outbound (sending / sent)</option>
          <option value="Not recorded">Direction not recorded</option>
        </select>
        <select
          aria-label="Filter by date"
          value={period}
          onChange={(event) => setPeriod(event.target.value)}
          className="min-h-11 w-[calc((100%-0.5rem)/2)] min-w-0 rounded-md border border-input bg-background px-2 text-sm sm:w-auto"
        >
          <option value="">All dates</option>
          <option value="7">Last 7 days</option>
          <option value="30">Last 30 days</option>
        </select>
        <div
          className="flex rounded-md border border-input bg-background p-0.5"
          role="group"
          aria-label="Document grouping"
        >
          {[false, true].map((value) => (
            <button
              key={String(value)}
              aria-pressed={grouped === value}
              onClick={() => setGrouped(value)}
              className={cn(
                "min-h-10 rounded px-3 text-sm font-medium transition-colors",
                grouped === value
                  ? "bg-accent text-primary"
                  : "text-muted-foreground hover:bg-muted",
              )}
            >
              {value ? "Group related" : "Documents"}
            </button>
          ))}
        </div>
        {grouped && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setExpanded(new Set())}
          >
            Collapse all
          </Button>
        )}
      </div>
      {error && (
        <div
          role="alert"
          className="mb-4 flex items-center justify-between gap-3 rounded-md bg-destructive/10 p-3 text-sm text-destructive"
        >
          {error}
          <Button variant="outline" onClick={reload}>
            Retry
          </Button>
        </div>
      )}
      {contextError && (
        <div
          role="status"
          className="mb-4 flex items-center justify-between gap-3 rounded-md bg-muted p-3 text-sm"
        >
          Partner and delivery information could not be loaded. Document
          relationships are still available.
          <Button
            variant="outline"
            size="sm"
            onClick={() => setContextRetry((value) => value + 1)}
          >
            Retry
          </Button>
        </div>
      )}
      <div className={cn("flex items-start", selected && "gap-6")}>
        <section
          aria-label="Document stream"
          className={cn("min-w-0 flex-1", selected && "hidden xl:block")}
        >
          {hasNext && (
            <p className="mb-2 text-sm text-muted-foreground">
              Search and filters cover loaded documents. Load more to include older records.
            </p>
          )}
          {loading ? (
            <div
              aria-label="Loading documents"
              aria-busy="true"
              className="divide-y border-y"
            >
              {Array.from({ length: 7 }, (_, index) => (
                <div
                  key={index}
                  className="flex h-16 items-center gap-6 px-4 motion-safe:animate-pulse"
                >
                  <span className="h-3 w-2/5 rounded bg-muted" />
                  <span className="h-3 w-1/5 rounded bg-muted" />
                  <span className="h-3 w-1/5 rounded bg-muted" />
                </div>
              ))}
            </div>
          ) : (
            <>
              <table className="w-full table-fixed text-left text-sm">
                <thead className="border-y bg-muted/60 text-xs text-muted-foreground">
                  <tr>
                    <th className="w-[60%] px-3 sm:w-[46%] py-3 font-medium">
                      {grouped ? "Exchange / document" : "Document"}
                    </th>
                    <th className="hidden px-3 py-3 font-medium lg:table-cell">
                      Partner / type
                    </th>
                    <th className="w-[40%] px-3 py-3 font-medium sm:w-[27%] lg:w-auto">Status</th>
                    <th className="hidden px-3 py-3 font-medium sm:table-cell sm:w-[27%] lg:w-auto">
                      Updated
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {!grouped
                    ? visible.map((document) => documentRow(document))
                    : [true, false].map((attention) => {
                        const section = visibleGroups.filter(
                          (group) =>
                            (group.documents.length > 1 || attention) &&
                            group.documents.some(needsAttention) === attention,
                        )
                        if (!section.length) return null
                        return (
                          <Fragment key={String(attention)}>
                            <tr className="border-b bg-muted/40">
                              <th
                                colSpan={4}
                                className="px-3 py-2.5 text-xs font-semibold"
                              >
                                {attention
                                  ? "Needs attention"
                                  : "Recent exchanges"}
                                <span className="ml-2 font-normal text-muted-foreground">
                                  {section.length}
                                </span>
                              </th>
                            </tr>
                            {section.map((group) => {
                              if (group.documents.length === 1)
                                return documentRow(group.root)
                              const status =
                                group.documents.find(needsAttention) ??
                                group.documents[0]
                              const open = expanded.has(group.key)
                              return (
                                <Fragment key={group.key}>
                                  <tr className="border-b hover:bg-muted/40">
                                    <td className="px-3 py-2">
                                      <button
                                        aria-expanded={open}
                                        aria-controls={`group-${group.root.id}`}
                                        onClick={() => toggle(group.key)}
                                        className="flex min-h-11 w-full items-center gap-2 text-left"
                                      >
                                        <ChevronRight className={cn("h-4 w-4 shrink-0 motion-safe:transition-transform motion-safe:duration-150 motion-safe:ease-out", open && "rotate-90")} aria-hidden="true" />
                                        <GitBranch className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                                        <span className="min-w-0">
                                          <span className="block break-words font-semibold">
                                            {group.root.reference}
                                          </span>
                                          <span className="text-xs text-muted-foreground">
                                            {group.documents.length} related
                                            documents
                                          </span>
                                        </span>
                                      </button>
                                    </td>
                                    <td className="hidden px-3 text-xs text-muted-foreground lg:table-cell">
                                      {group.root.partner}
                                    </td>
                                    <td className="px-3">
                                      <Status document={status} />
                                    </td>
                                    <td className="hidden px-3 text-xs text-muted-foreground sm:table-cell">
                                      {date(group.documents[0].updatedAt)}
                                    </td>
                                  </tr>
                                  {open && (
                                    <tr id={`group-${group.root.id}`}>
                                      <td colSpan={4} className="p-0">
                                        <table
                                          aria-label={`Documents related to ${group.root.reference}`}
                                          className="w-full table-fixed"
                                        >
                                          <thead className="text-xs text-muted-foreground">
                                            <tr>
                                              <th className="w-[60%] px-3 sm:w-[46%] py-2 font-medium">
                                                Document
                                              </th>
                                              <th className="hidden px-3 py-2 font-medium lg:table-cell">
                                                Type / direction
                                              </th>
                                              <th className="w-[40%] px-3 py-2 font-medium sm:w-[27%] lg:w-auto">
                                                Status
                                              </th>
                                              <th className="hidden px-3 py-2 font-medium sm:table-cell sm:w-[27%] lg:w-auto">
                                                Updated
                                              </th>
                                            </tr>
                                          </thead>
                                          <tbody>
                                            {lineageRows(group).map(
                                              ({ document, depth }) =>
                                                documentRow(
                                                  document,
                                                  depth,
                                                  true,
                                                ),
                                            )}
                                          </tbody>
                                        </table>
                                      </td>
                                    </tr>
                                  )}
                                </Fragment>
                              )
                            })}
                          </Fragment>
                        )
                      })}
                  {grouped &&
                    visibleGroups.some(
                      (group) =>
                        group.documents.length === 1 &&
                        !needsAttention(group.root),
                    ) && (
                      <>
                        <tr className="border-b bg-muted/40">
                          <th colSpan={4} className="px-3 py-2">
                            <button
                              onClick={() => toggle("unlinked")}
                              aria-expanded={expanded.has("unlinked")}
                              className="flex min-h-11 w-full items-center gap-2 text-left text-sm font-medium"
                            >
                              <ChevronRight className={cn("h-4 w-4 shrink-0 motion-safe:transition-transform motion-safe:duration-150 motion-safe:ease-out", expanded.has("unlinked") && "rotate-90")} aria-hidden="true" />
                              Unlinked documents
                              <span className="text-xs text-muted-foreground">
                                {
                                  visibleGroups.filter(
                                    (group) =>
                                      group.documents.length === 1 &&
                                      !needsAttention(group.root),
                                  ).length
                                }
                              </span>
                            </button>
                          </th>
                        </tr>
                        {expanded.has("unlinked") &&
                          visibleGroups
                            .filter(
                              (group) =>
                                group.documents.length === 1 &&
                                !needsAttention(group.root),
                            )
                            .map((group) => documentRow(group.root))}
                      </>
                    )}
                </tbody>
              </table>
              {!visible.length && (
                <div className="py-16 text-center">
                  <FileText className="mx-auto h-6 w-6 text-muted-foreground" />
                  <h2 className="mt-3 text-sm font-semibold">
                    {documents.length
                      ? "No matching documents"
                      : "Your documents will appear here"}
                  </h2>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {documents.length
                      ? "Try another reference or adjust your filters."
                      : "Receive an order or create a business document to get started."}
                  </p>
                  {documents.length > 0 && (
                    <Button
                      variant="ghost"
                      onClick={clearFilters}
                      className="mt-3"
                    >
                      Clear filters
                    </Button>
                  )}
                </div>
              )}
            </>
          )}
          <footer className="mt-4 flex items-center justify-between gap-3 text-xs text-muted-foreground">
            <span aria-live="polite">
              {visible.length} documents
              {grouped ? ` in ${visibleGroups.length} exchanges` : ""} ·{" "}
              {documents.length} loaded
            </span>
            {hasNext && (
              <Button
                variant="outline"
                size="sm"
                disabled={loadingMore}
                onClick={() => void loadNextPage()}
              >
                {loadingMore ? "Loading…" : "Load more"}
              </Button>
            )}
          </footer>
        </section>
        {selected && (
          <DocumentInspector
            key={selected.key}
            document={selected}
            exchanges={documentExchanges(
              exchanges,
              selected.setId,
              selected.id,
            )}
            related={selectedGroup?.documents ?? []}
            onSelect={setSelectedKey}
            onClose={() => {
              setSelectedKey(null)
              requestAnimationFrame(() => selectionTrigger.current?.focus())
            }}
            documents={visible}
          />
        )}
      </div>
    </LayoutBody>
  )
}

function DocumentInspector({
  document,
  exchanges,
  related,
  onSelect,
  onClose,
  documents,
}: {
  document: WorkspaceDocument
  exchanges: ExchangeResponse[]
  related: WorkspaceDocument[]
  documents: WorkspaceDocument[]
  onSelect: (key: string) => void
  onClose: () => void
}) {
  const [tab, setTab] = useState("overview")
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    heading.current?.focus({ preventScroll: true })
  }, [])
  const index = documents.findIndex((item) => item.key === document.key)
  useEffect(() => {
    function escape(event: KeyboardEvent) {
      if (event.key === "Escape") onClose()
    }
    window.addEventListener("keydown", escape)
    return () => window.removeEventListener("keydown", escape)
  }, [onClose])
  return (
    <aside
      aria-label={`Details for ${document.reference}`}
      className="w-full min-w-0 xl:sticky xl:top-6 xl:w-[22rem] xl:shrink-0 xl:border-l xl:pl-6 2xl:w-[25rem]"
    >
      <div className="mb-4 flex items-center justify-between">
        <div className="flex gap-1">
          <Button
            variant="outline"
            size="icon"
            aria-label="Previous document"
            disabled={index <= 0}
            onClick={() => onSelect(documents[index - 1].key)}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label="Next document"
            disabled={index < 0 || index === documents.length - 1}
            onClick={() => onSelect(documents[index + 1].key)}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
        <Button
          variant="ghost"
          size="icon"
          onClick={onClose}
          aria-label="Close document details"
        >
          <X className="h-4 w-4" />
        </Button>
      </div>
      <h2
        ref={heading}
        tabIndex={-1}
        className="break-words text-lg font-semibold"
      >
        {document.reference}
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        {document.partner} · {documentLabels[document.type]}
      </p>
      <Button asChild variant="outline" className="my-4">
        <Link to={href(document)}>
          {document.type === "CREDIT_NOTE"
            ? "Open source file"
            : "Open document"}
          <ArrowUpRight className="ml-2 h-4 w-4" />
        </Link>
      </Button>
      <nav
        aria-label="Document detail views"
        className="mb-4 flex flex-wrap gap-x-4 border-b"
      >
        {["overview", "relationships", "activity"].map((key) => (
          <button
            key={key}
            aria-current={key === tab ? "page" : undefined}
            onClick={() => setTab(key)}
            className={cn(
              "min-h-11 border-b-2 text-sm capitalize",
              tab === key
                ? "border-primary font-semibold text-primary"
                : "border-transparent text-muted-foreground",
            )}
          >
            {key}
          </button>
        ))}
      </nav>
      {tab === "overview" && (
        <>
          <Status document={document} />
          {needsAttention(document) && (
            <div className="mt-3 rounded-md bg-destructive/10 p-3 text-sm text-destructive">
              <p>
                {document.reason ??
                  "This exchange needs review. Open the document to inspect its delivery details."}
              </p>
              <Link
                to={href(document)}
                className="mt-3 inline-flex min-h-10 items-center font-semibold underline"
              >
                Review document
                <ChevronRight className="ml-1 h-4 w-4" />
              </Link>
            </div>
          )}
          <dl className="mt-5 divide-y text-sm">
            {[
              ["Direction", document.direction.toLowerCase()],
              ["Delivery", statusLabels[document.status] ?? "Processing"],
              ["Business outcome", "Not recorded"],
              ["Created", date(document.createdAt)],
              ["Last updated", date(document.updatedAt)],
            ].map(([label, value]) => (
              <div key={label} className="flex justify-between gap-4 py-3">
                <dt className="text-muted-foreground">{label}</dt>
                <dd className="text-right">{value}</dd>
              </div>
            ))}
          </dl>
          <h3 className="mb-2 mt-6 text-sm font-semibold">Related documents</h3>
        </>
      )}
      {tab !== "activity" && (
        <div className="space-y-1">
          {related
            .filter((item) => item.key !== document.key)
            .map((item) => (
              <button
                key={item.key}
                onClick={() => onSelect(item.key)}
                className="flex min-h-11 w-full items-center gap-2 rounded px-2 text-left text-sm text-primary hover:bg-muted"
              >
                <FileText className="h-4 w-4 shrink-0" />
                {item.reference}
              </button>
            ))}
          {related.length <= 1 && (
            <p className="py-3 text-sm text-muted-foreground">
              No recorded document relationships. Related follow-ups created
              from this document will appear here.
            </p>
          )}
          {tab === "relationships" &&
            document.relations.map((relation, index) => (
              <p key={index} className="py-2 text-xs text-muted-foreground">
                {relation.type?.toLowerCase().replaceAll("_", " ")} ·{" "}
                {related.find(
                  (item) => item.key === relationTargetKey(document, relation),
                )?.reference ?? "Document outside the loaded view"}
              </p>
            ))}
        </div>
      )}
      {tab === "activity" && (
        <>
          <h3 className="mb-3 text-sm font-semibold">Exchange activity</h3>
          <ExchangeTimeline exchanges={exchanges} />
        </>
      )}
      <Link
        to={`/file/${document.setId}`}
        className="mt-6 flex min-h-11 items-center gap-2 border-t pt-4 text-sm text-primary hover:underline"
      >
        Source file and technical history
        <ArrowUpRight className="h-4 w-4" />
      </Link>
    </aside>
  )
}

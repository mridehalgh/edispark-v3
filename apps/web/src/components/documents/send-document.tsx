"use client"

import type {
  EligibleRoute,
  ExchangeResponse,
  ReadinessReport,
} from "@edispark/integration-client"
import {
  CheckCircle2,
  CircleAlert,
  FileCheck2,
  Send,
} from "lucide-react"
import { useState } from "react"
import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet"
import { attentionReason } from "@/lib/attention-reason"
import { DocumentFindings } from "@/components/documents/document-findings"
import { DOCUMENT_EXCHANGE_UPDATED } from "@/components/documents/exchange-timeline"

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: "no-store" })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) {
    const findings = body?.error?.details?.errors
    const detail = Array.isArray(findings)
      ? findings
          .slice(0, 5)
          .map(
            (issue: { path?: string; message?: string }) =>
              `${issue.message ?? "Check this field"} ${issue.path ?? ""}`,
          )
          .join(" · ")
      : ""
    throw new Error(
      [
        body?.error?.message ?? "The document request could not be completed.",
        detail,
      ]
        .filter(Boolean)
        .join(" "),
    )
  }
  return body as T
}

function routeLabel(route: EligibleRoute) {
  const capability = route.capability
  return [
    route.agreementName,
    capability?.standard,
    capability?.release,
    capability?.messageType,
  ]
    .filter(Boolean)
    .join(" · ")
}

export function SendDocument({
  documentSetId,
  documentId,
  documentType,
  lifecycleStatus = "FINAL",
}: {
  documentSetId: string
  documentId: string
  documentType?: string
  lifecycleStatus?: string
}) {
  const [routes, setRoutes] = useState<EligibleRoute[]>()
  const [selected, setSelected] = useState("")
  const [readiness, setReadiness] = useState<ReadinessReport>()
  const [loading, setLoading] = useState(false)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string>()
  const [exchange, setExchange] = useState<ExchangeResponse>()
  const [currentLifecycle, setCurrentLifecycle] = useState(lifecycleStatus)
  const correctionLink = documentType
    ? `/documents/new?mode=correct&type=${documentType}&fromSet=${documentSetId}&fromDocument=${documentId}`
    : undefined

  async function finalize() {
    setLoading(true)
    setError(undefined)
    try {
      await requestJson(
        `/api/documents/${documentSetId}/documents/${documentId}/finalize`,
        { method: "POST" },
      )
      setCurrentLifecycle("FINAL")
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "The document could not be finalized.",
      )
    } finally {
      setLoading(false)
    }
  }

  async function openRoutes() {
    setLoading(true)
    setError(undefined)
    try {
      const query = new URLSearchParams({ documentSetId, documentId })
      const available = await requestJson<EligibleRoute[]>(
        `/api/integration/exchanges/eligible-routes?${query}`,
      )
      setRoutes(available)
      setSelected((current) => current || available[0]?.capability?.id || "")
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Available routes could not be loaded.",
      )
    } finally {
      setLoading(false)
    }
  }

  async function checkReadiness() {
    const route = routes?.find((item) => item.capability?.id === selected)
    if (!route?.agreementId || !route.capability?.id) return
    setLoading(true)
    setReadiness(undefined)
    setError(undefined)
    try {
      setReadiness(
        await requestJson<ReadinessReport>(
          "/api/integration/exchanges/readiness",
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              agreementId: route.agreementId,
              capabilityId: route.capability.id,
              documentSetId,
              documentId,
            }),
          },
        ),
      )
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Readiness could not be checked.",
      )
    } finally {
      setLoading(false)
    }
  }

  async function send() {
    const route = routes?.find((item) => item.capability?.id === selected)
    if (!route?.agreementId || !route.capability?.id || !readiness?.ready)
      return
    setSending(true)
    setError(undefined)
    try {
      const result = await requestJson<ExchangeResponse>(
        "/api/integration/exchanges",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            agreementId: route.agreementId,
            capabilityId: route.capability.id,
            documentSetId,
            documentId,
            idempotencyKey: crypto.randomUUID(),
          }),
        },
      )
      setExchange(result)
      window.dispatchEvent(new Event(DOCUMENT_EXCHANGE_UPDATED))
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "The document could not be sent.",
      )
    } finally {
      setSending(false)
    }
  }

  if (exchange) {
    const needsAttention =
      exchange.status === "FAILED" || exchange.status === "NEEDS_REVIEW"
    const reason = attentionReason(exchange)
    return (
      <div
        className={`mt-3 flex max-w-md items-start gap-2 rounded-md px-3 py-2 text-left text-xs ${needsAttention ? "bg-destructive/10 text-destructive" : "bg-success/25 text-success-foreground"}`}
        role="status"
      >
        {needsAttention ? (
          <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        ) : (
          <CheckCircle2
            className="mt-0.5 h-4 w-4 shrink-0"
            aria-hidden="true"
          />
        )}
        <span>
          <span className="block font-medium">
            {needsAttention
              ? "The exchange needs attention."
              : "Queued for delivery."}
          </span>
          {reason ? (
            <span className="mt-1 block leading-5">{reason}</span>
          ) : null}
        </span>
      </div>
    )
  }

  if (currentLifecycle === "DRAFT") {
    return (
      <div className="mt-3 flex max-w-xl flex-col items-start gap-2 text-left">
        <Button
          type="button"
          className="gap-2"
          onClick={() => void finalize()}
          disabled={loading}
        >
          <FileCheck2 className="h-4 w-4" />
          {loading ? "Validating…" : "Validate and finalize"}
        </Button>
        <p className="text-xs text-muted-foreground">
          Drafts cannot be sent. Finalizing checks the UBL document; partner
          readiness is checked next.
        </p>
        {error && (
          <p className="text-xs leading-5 text-destructive" role="alert">
            {error}
          </p>
        )}
        {correctionLink && (
          <Link
            to={correctionLink}
            className="inline-flex min-h-10 items-center text-xs font-medium text-primary"
          >
            Revise details in a linked draft →
          </Link>
        )}
      </div>
    )
  }

  if (currentLifecycle === "CANCELLED")
    return (
      <p className="mt-3 text-xs text-muted-foreground">
        This document was cancelled and cannot be sent.
      </p>
    )

  const route = routes?.find((item) => item.capability?.id === selected)
  return (
    <Sheet
      open={routes !== undefined}
      onOpenChange={(open) => {
        if (!open && !sending) {
          setRoutes(undefined)
          setReadiness(undefined)
          setError(undefined)
        }
      }}
    >
      <SheetTrigger asChild>
        <Button
          type="button"
          className="mt-3 gap-2"
          onClick={() => void openRoutes()}
          disabled={loading || sending}
        >
          <Send className="h-4 w-4" aria-hidden="true" />
          {loading ? "Finding routes…" : "Send to partner"}
        </Button>
      </SheetTrigger>
      {routes !== undefined && (
        <SheetContent className="w-full overflow-y-auto sm:max-w-xl">
          {routes.length === 0 ? (
            <div>
              <SheetHeader className="pr-6">
                <SheetTitle>No qualified route is active</SheetTitle>
                <SheetDescription>
                  Ask a workspace administrator to finish this partner
                  connection.
                </SheetDescription>
              </SheetHeader>
              <Link
                to="/partners"
                className="mt-4 flex min-h-10 items-center text-sm font-medium text-primary"
              >
                Review partner settings →
              </Link>
              {correctionLink && (
                <Link
                  to={correctionLink}
                  className="mt-2 flex min-h-10 items-center text-sm font-medium text-primary"
                >
                  Create a corrected draft and choose its partner →
                </Link>
              )}
            </div>
          ) : (
            <section className="text-left" aria-label="Partner readiness">
              <SheetHeader className="pr-6">
                <SheetTitle>Check before sending</SheetTitle>
                <SheetDescription className="text-sm leading-5">
                  Checks the document, partner requirements and mapping
                  coverage. Sending repeats these checks against the current
                  version.
                </SheetDescription>
              </SheetHeader>
              <label className="mt-3 grid gap-1 text-xs font-medium">
                Partner route
                <select
                  value={selected}
                  disabled={loading || sending}
                  onChange={(event) => {
                    setSelected(event.target.value)
                    setReadiness(undefined)
                  }}
                  className="h-10 min-w-0 rounded-md border bg-background px-2 text-sm"
                >
                  {routes!.map((item) => (
                    <option
                      key={item.capability?.id}
                      value={item.capability?.id}
                    >
                      {routeLabel(item)}
                    </option>
                  ))}
                </select>
              </label>
              {readiness && (
                <div className="mt-4 border-t pt-3" role="status">
                  <p className="flex items-center gap-2 text-sm font-medium">
                    {readiness.ready ? (
                      <CheckCircle2 className="h-4 w-4 text-success-foreground" />
                    ) : (
                      <CircleAlert className="h-4 w-4 text-destructive" />
                    )}
                    {readiness.ready
                      ? "Ready for this partner route"
                      : "Resolve these checks before sending"}
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Agreement revision {readiness.agreementRevision} ·{" "}
                    {readiness.checkedAt
                      ? new Date(readiness.checkedAt).toLocaleString()
                      : ""}
                  </p>
                  {(["DOCUMENT", "MAPPING", "CONFIGURATION"] as const).map(
                    (owner) => {
                      const issues =
                        readiness.issues?.filter(
                          (issue) => issue.owner === owner,
                        ) ?? []
                      if (!issues.length) return null
                      return (
                        <div className="mt-3 border-t pt-3" key={owner}>
                          <h4 className="text-sm font-semibold">
                            {owner === "DOCUMENT"
                              ? "Document details"
                              : owner === "MAPPING"
                                ? "Mapping administrator"
                                : "Partner configuration"}{" "}
                            · {issues.length}
                          </h4>
                          <DocumentFindings findings={issues} />
                          {owner === "DOCUMENT" && (
                            <Link
                              className="mt-2 inline-flex min-h-10 items-center text-xs font-medium text-primary hover:underline"
                              to={`/documents/new?mode=correct&type=${route?.capability?.documentType}&fromSet=${documentSetId}&fromDocument=${documentId}`}
                            >
                              Create a corrected draft →
                            </Link>
                          )}
                          {owner === "MAPPING" &&
                            readiness.mappingRevisionId && (
                              <Link
                                className="mt-2 inline-flex min-h-10 items-center text-xs font-medium text-primary hover:underline"
                                to={`/organisation/document-rules/${readiness.mappingRevisionId}?${new URLSearchParams({
                                  sourceSet: documentSetId, sourceDocument: documentId,
                                  sourceRepresentation: readiness.sourceDocument?.representationId ?? "",
                                  sourceHash: readiness.sourceDocument?.contentHash ?? "",
                                })}`}
                              >
                                Review mapping and create a revision →
                              </Link>
                            )}
                          {owner === "CONFIGURATION" && (
                            <Link
                              className="mt-2 inline-flex min-h-10 items-center text-xs font-medium text-primary hover:underline"
                              to="/partners"
                            >
                              Review partner settings →
                            </Link>
                          )}
                        </div>
                      )
                    },
                  )}
                </div>
              )}
              <div className="mt-4 flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  onClick={() => void checkReadiness()}
                  disabled={loading || sending || !selected}
                >
                  {loading
                    ? "Checking…"
                    : readiness
                      ? "Check again"
                      : "Check readiness"}
                </Button>
                <Button
                  onClick={() => void send()}
                  disabled={!readiness?.ready || loading || sending}
                  className="gap-2"
                >
                  <Send className="h-4 w-4" />
                  {sending ? "Queuing…" : "Send securely"}
                </Button>
              </div>
              {error && (
                <p className="mt-2 text-xs text-destructive" role="alert">
                  {error}
                </p>
              )}
            </section>
          )}
        </SheetContent>
      )}
      {routes === undefined && error && (
        <p className="mt-2 max-w-sm text-xs text-destructive" role="alert">
          {error}
        </p>
      )}
    </Sheet>
  )
}

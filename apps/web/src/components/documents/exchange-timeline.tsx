"use client"

import type { ExchangeResponse } from "@edispark/integration-client"
import { CheckCircle2, CircleAlert, Clock3, RefreshCw } from "lucide-react"
import { useEffect, useId, useState } from "react"
import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import {
  documentExchanges,
  duration,
  exchangeStageDescriptions,
  exchangeStageLabels,
  exchangeTimeline,
  timestamp,
} from "@/lib/exchange-timeline"

export const DOCUMENT_EXCHANGE_UPDATED = "edispark:document-exchange-updated"

function eventDate(value?: string) {
  if (timestamp(value) === undefined) return "Time not recorded"
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    fractionalSecondDigits: 3,
    timeZoneName: "short",
  }).format(new Date(value!))
}

function MappingFinding({
  warning,
}: {
  warning: NonNullable<ExchangeResponse["warnings"]>[number]
}) {
  return (
    <li className="text-sm">
      <p className="max-w-[70ch] break-words leading-5">
        {warning.message ?? "A mapping finding was recorded."}
      </p>
      {warning.sourcePath && (
        <p className="mt-1 break-all font-mono text-xs">{warning.sourcePath}</p>
      )}
      <p className="mt-1 break-words text-xs text-muted-foreground">
        {[warning.code, warning.disposition].filter(Boolean).join(" · ")}
      </p>
    </li>
  )
}

function ExchangeEvidence({ exchange }: { exchange: ExchangeResponse }) {
  const fields = [
    ["Exchange ID", exchange.id],
    [
      "Direction",
      exchange.direction === "INBOUND"
        ? "Inbound"
        : exchange.direction === "OUTBOUND"
          ? "Outbound"
          : undefined,
    ],
    ["Agreement ID", exchange.agreementId],
    ["Agreement revision", exchange.agreementRevision],
    ["Capability ID", exchange.capabilityId],
    ["Transfer ID", exchange.mftTransferId],
    ["Source message", exchange.sourceMessageReference],
    ["Message position", exchange.messageOrdinal],
  ].filter(([, value]) => value !== undefined && value !== "")
  return (
    <div className="mt-3 space-y-3">
      {Boolean(exchange.warnings?.length) && (
        <div className="rounded-md bg-muted p-3">
          <h4 className="text-sm font-semibold">
            Mapping findings · {exchange.warnings!.length}
          </h4>
          <ul className="mt-2 space-y-3">
            {exchange.warnings!.slice(0, 3).map((warning, index) => (
              <MappingFinding key={index} warning={warning} />
            ))}
          </ul>
          {exchange.warnings!.length > 3 && (
            <details className="mt-3">
              <summary className="min-h-10 cursor-pointer py-2 text-sm font-medium text-primary">
                Show {exchange.warnings!.length - 3} more findings
              </summary>
              <ul className="mt-2 space-y-3">
                {exchange.warnings!.slice(3).map((warning, index) => (
                  <MappingFinding key={index} warning={warning} />
                ))}
              </ul>
            </details>
          )}
        </div>
      )}
      {exchange.mappingReport?.documentSetId && (
        <Link
          to={`/files/${exchange.mappingReport.documentSetId}`}
          className="inline-flex min-h-10 items-center text-sm font-medium text-primary hover:underline"
        >
          Open full mapping report ↗
        </Link>
      )}
      {fields.length > 0 && (
        <details className="text-xs">
          <summary className="min-h-9 cursor-pointer py-2 font-medium text-primary">
            Exchange identifiers and routing
          </summary>
          <dl className="mt-1 divide-y">
            {fields.map(([label, value]) => (
              <div key={label} className="py-2">
                <dt className="text-muted-foreground">{label}</dt>
                <dd className="mt-1 break-all font-mono">{value}</dd>
              </div>
            ))}
          </dl>
        </details>
      )}
    </div>
  )
}

export function ExchangeTimeline({
  exchanges,
}: {
  exchanges: ExchangeResponse[]
}) {
  const events = exchangeTimeline(exchanges)
  if (!events.length)
    return (
      <p className="py-4 text-sm leading-6 text-muted-foreground">
        No exchange activity is recorded for this document yet. Processing and
        delivery events will appear here when an exchange starts.
      </p>
    )
  return (
    <div>
      <p className="mb-5 text-xs text-muted-foreground">
        First event to latest · {events.length} recorded steps ·{" "}
        {exchanges.length} {exchanges.length === 1 ? "exchange" : "exchanges"}
      </p>
      <ol
        aria-label="Chronological exchange activity"
        className="m-0 list-none p-0"
        role="list"
      >
        {events.map((event, index) => {
          const status = event.stage.status ?? ""
          const failed = status === "FAILED" || status === "NEEDS_REVIEW"
          const delivered = status === "DELIVERED"
          const Icon = failed ? CircleAlert : delivered ? CheckCircle2 : Clock3
          const attemptStart =
            index === 0 || events[index - 1].attempt !== event.attempt
          return (
            <li
              key={`${event.exchange.id ?? event.attempt}:${event.sequence}`}
              className="relative grid grid-cols-[2rem_minmax(0,1fr)] gap-3 pb-6 last:pb-0"
            >
              {index < events.length - 1 && (
                <span
                  aria-hidden="true"
                  className="absolute bottom-0 left-4 top-8 w-px bg-border"
                />
              )}
              <span
                aria-hidden="true"
                className={cn(
                  "relative flex h-8 w-8 items-center justify-center rounded-full border bg-background text-xs font-semibold tabular-nums",
                  failed &&
                    "border-destructive/40 bg-destructive/10 text-destructive",
                  delivered &&
                    "border-emerald-700/30 bg-emerald-50 text-emerald-800",
                )}
              >
                {event.sequence}
              </span>
              <div className="min-w-0 pb-1">
                {attemptStart && exchanges.length > 1 && (
                  <p className="mb-2 text-xs font-medium text-muted-foreground">
                    Exchange {event.attempt}
                    {event.exchange.direction
                      ? ` · ${event.exchange.direction === "INBOUND" ? "Inbound" : "Outbound"}`
                      : ""}
                  </p>
                )}
                <div
                  className={cn(
                    "flex items-start gap-2 text-sm font-semibold",
                    failed && "text-destructive",
                    delivered && "text-emerald-800",
                  )}
                >
                  <Icon
                    className="mt-0.5 h-4 w-4 shrink-0"
                    aria-hidden="true"
                  />
                  <h3 className="leading-5">
                    <span className="sr-only">Step {event.sequence}: </span>
                    {event.recordedStatusOnly ? "Recorded status: " : ""}
                    {delivered && event.exchange.direction === "INBOUND"
                      ? "Received and stored"
                      : (exchangeStageLabels[status] ?? "Processing event")}
                  </h3>
                </div>
                <p className="mt-1 text-xs leading-5 tabular-nums text-muted-foreground">
                  {timestamp(event.stage.occurredAt) !== undefined ? (
                    <time dateTime={event.stage.occurredAt}>
                      {eventDate(event.stage.occurredAt)}
                    </time>
                  ) : (
                    "Time not recorded"
                  )}
                </p>
                {(event.elapsed !== undefined ||
                  event.sincePrevious !== undefined) && (
                  <p className="mt-0.5 text-xs tabular-nums text-muted-foreground">
                    {event.elapsed !== undefined
                      ? `${duration(event.elapsed)} from exchange start`
                      : ""}
                    {event.sincePrevious !== undefined
                      ? `${event.elapsed !== undefined ? " · " : ""}+${duration(event.sincePrevious)} since previous step`
                      : ""}
                  </p>
                )}
                <p className="mt-2 max-w-[70ch] break-words text-sm leading-6">
                  {event.stage.detail ||
                    (event.recordedStatusOnly
                      ? "Only the current status was retained; individual processing steps are unavailable."
                      : delivered && event.exchange.direction === "INBOUND"
                        ? "Inbound document processing completed and the document was retained."
                        : (exchangeStageDescriptions[status] ??
                          "An event was recorded during document processing."))}
                </p>
                {failed && event.attempt === exchanges.length && (
                  <p className="mt-2 text-sm leading-6 text-destructive">
                    Review the recorded findings and source document before
                    starting another exchange.
                  </p>
                )}
                {event.lastInAttempt && (
                  <ExchangeEvidence exchange={event.exchange} />
                )}
              </div>
            </li>
          )
        })}
      </ol>
    </div>
  )
}

export function DocumentActivity({
  documentSetId,
  documentId,
}: {
  documentSetId: string
  documentId: string
}) {
  const titleId = useId()
  const [exchanges, setExchanges] = useState<ExchangeResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const refresh = () => setRevision((current) => current + 1)
    window.addEventListener(DOCUMENT_EXCHANGE_UPDATED, refresh)
    return () => window.removeEventListener(DOCUMENT_EXCHANGE_UPDATED, refresh)
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(false)
    void fetch("/api/integration/exchanges", {
      cache: "no-store",
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) throw new Error("Activity unavailable")
        return response.json() as Promise<ExchangeResponse[]>
      })
      .then((records) => {
        if (!controller.signal.aborted)
          setExchanges(documentExchanges(records, documentSetId, documentId))
      })
      .catch(() => {
        if (!controller.signal.aborted) setError(true)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [documentId, documentSetId, revision])
  return (
    <section
      id="document-activity"
      aria-labelledby={titleId}
      className="my-6 scroll-mt-24 border-t pt-6"
    >
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 id={titleId} className="text-base font-semibold">
          Document activity
        </h2>
        <Button
          variant="ghost"
          size="sm"
          disabled={loading}
          onClick={() => setRevision((current) => current + 1)}
        >
          <RefreshCw className="mr-2 h-3.5 w-3.5" aria-hidden="true" />
          {loading ? "Refreshing…" : "Refresh activity"}
        </Button>
      </div>
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          Activity could not be loaded. Refresh to try again.
        </p>
      ) : loading ? (
        <div
          aria-label="Loading activity"
          aria-busy="true"
          className="space-y-4 py-3 motion-safe:animate-pulse"
        >
          {[1, 2, 3].map((item) => (
            <div key={item} className="flex gap-3">
              <span className="h-8 w-8 rounded-full bg-muted" />
              <span className="h-12 w-3/4 rounded bg-muted" />
            </div>
          ))}
        </div>
      ) : (
        <ExchangeTimeline exchanges={exchanges} />
      )}
    </section>
  )
}

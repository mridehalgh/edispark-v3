import type {
  ExchangeResponse,
  ExchangeStage,
} from "@edispark/integration-client"

export const exchangeStageLabels: Record<string, string> = {
  RECEIVED: "Exchange accepted",
  RESOLVING: "Resolving partner route",
  VALIDATING: "Validating document",
  SPLITTING: "Separating messages",
  TRANSFORMING: "Mapping document",
  STORING: "Retaining generated file",
  QUEUING_TRANSPORT: "Queued for delivery",
  AWAITING_RECEIPT: "Awaiting delivery receipt",
  DELIVERED: "Delivery confirmed",
  NEEDS_REVIEW: "Review required",
  FAILED: "Exchange failed",
}

export const exchangeStageDescriptions: Record<string, string> = {
  RECEIVED: "The document was accepted for processing.",
  RESOLVING: "The exchange selected the partner agreement and document route.",
  VALIDATING:
    "Validation started against the configured document requirements.",
  SPLITTING:
    "The source batch was separated into individual business messages.",
  TRANSFORMING:
    "The published mapping started converting the document to the required representation.",
  STORING:
    "The generated representation was being saved with its source-document provenance.",
  QUEUING_TRANSPORT: "The generated file was handed to transport for delivery.",
  AWAITING_RECEIPT:
    "Transport is waiting for the partner's delivery acknowledgement.",
  DELIVERED:
    "Delivery was confirmed. This does not by itself confirm business acceptance.",
  NEEDS_REVIEW:
    "Processing stopped for review. Inspect the recorded reason and mapping evidence before sending again.",
  FAILED:
    "This exchange failed. Review the recorded reason before deciding whether to retry.",
}

export function documentExchanges(
  exchanges: ExchangeResponse[],
  setId: string,
  documentId: string,
) {
  return exchanges.filter((exchange) =>
    [exchange.sourceDocument, exchange.generatedDocument].some(
      (reference) =>
        reference?.documentSetId === setId &&
        reference.documentId === documentId,
    ),
  )
}

export function timestamp(value?: string) {
  const parsed = value ? Date.parse(value) : NaN
  return Number.isFinite(parsed) ? parsed : undefined
}

export function duration(milliseconds: number) {
  if (milliseconds < 1000) return `${Math.round(milliseconds)} ms`
  if (milliseconds < 60000) return `${(milliseconds / 1000).toFixed(1)} s`
  return `${Math.floor(milliseconds / 60000)} min ${Math.floor((milliseconds % 60000) / 1000)} s`
}

export interface TimelineEvent {
  exchange: ExchangeResponse
  stage: ExchangeStage
  attempt: number
  sequence: number
  elapsed?: number
  sincePrevious?: number
  recordedStatusOnly: boolean
  lastInAttempt: boolean
}

export function exchangeTimeline(
  exchanges: ExchangeResponse[],
): TimelineEvent[] {
  const attempts = [...exchanges].sort(
    (a, b) =>
      (timestamp(a.createdAt) ?? Infinity) -
      (timestamp(b.createdAt) ?? Infinity),
  )
  const events = attempts.flatMap((exchange, attempt) => {
    const recordedStatusOnly = !exchange.stages?.length
    const stages = recordedStatusOnly
      ? [
          {
            status: exchange.status,
            occurredAt: exchange.updatedAt,
            detail: undefined,
          },
        ]
      : [...exchange.stages!]
    stages.sort(
      (a, b) =>
        (timestamp(a.occurredAt) ?? Infinity) -
        (timestamp(b.occurredAt) ?? Infinity),
    )
    const start =
      timestamp(exchange.createdAt) ?? timestamp(stages[0]?.occurredAt)
    return stages.map((stage, index) => {
      const current = timestamp(stage.occurredAt)
      const previous = timestamp(stages[index - 1]?.occurredAt)
      return {
        exchange,
        stage,
        attempt: attempt + 1,
        sequence: 0,
        recordedStatusOnly,
        lastInAttempt: index === stages.length - 1,
        elapsed:
          current !== undefined && start !== undefined && current >= start
            ? current - start
            : undefined,
        sincePrevious:
          current !== undefined && previous !== undefined && current >= previous
            ? current - previous
            : undefined,
      }
    })
  })
  return events
    .sort(
      (a, b) =>
        (timestamp(a.stage.occurredAt) ?? Infinity) -
        (timestamp(b.stage.occurredAt) ?? Infinity),
    )
    .map((event, index) => ({ ...event, sequence: index + 1 }))
}

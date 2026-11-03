import type { TransferResponse } from "@edispark/mft-client"
import { AlertCircle, CheckCircle2, Clock3, RefreshCw } from "lucide-react"

import { cn } from "@/lib/utils"

const labels: Record<NonNullable<TransferResponse["status"]>, string> = {
  RECEIVED: "Received",
  QUEUED: "Queued",
  PROCESSING: "Processing",
  AWAITING_MDN: "Waiting for receipt",
  DELIVERED: "Delivered",
  RETRY_SCHEDULED: "Retry scheduled",
  NEEDS_REVIEW: "Needs attention",
  FAILED: "Failed",
}

export function transferStatusLabel(status?: TransferResponse["status"]) {
  return status ? labels[status] : "Unknown"
}

export function TransferStatus({ status, className }: { status?: TransferResponse["status"]; className?: string }) {
  const failed = status === "FAILED" || status === "NEEDS_REVIEW"
  const delivered = status === "DELIVERED" || status === "RECEIVED"
  const retrying = status === "RETRY_SCHEDULED"
  const Icon = failed ? AlertCircle : delivered ? CheckCircle2 : retrying ? RefreshCw : Clock3
  return (
    <span className={cn(
      "inline-flex items-center gap-1.5 text-xs font-medium",
      failed ? "text-destructive" : delivered ? "text-success-foreground" : "text-muted-foreground",
      className,
    )}>
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {transferStatusLabel(status)}
    </span>
  )
}

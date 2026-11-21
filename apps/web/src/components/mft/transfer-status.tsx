import type { TransferResponse } from "@edispark/mft-client"
import { AlertCircle, CheckCircle2, Clock3, RefreshCw } from "lucide-react"

import { transferStatusLabel } from "@/lib/transfer-status"
import { cn } from "@/lib/utils"

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

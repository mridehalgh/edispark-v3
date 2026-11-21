import type { TransferResponse } from "@edispark/mft-client"

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

"use client"

import type { ExchangeResponse } from "@edispark/integration-client"
import { CircleAlert } from "lucide-react"
import { useEffect, useState } from "react"

import { latestDocumentAttentionReason } from "@/lib/attention-reason"

export function DocumentAttention({ documentSetId, documentId }: { documentSetId: string; documentId: string }) {
  const [reason, setReason] = useState<string>()

  useEffect(() => {
    const controller = new AbortController()
    void fetch("/api/integration/exchanges", { cache: "no-store", signal: controller.signal })
      .then((response) => response.ok ? response.json() as Promise<ExchangeResponse[]> : [])
      .then((exchanges) => setReason(latestDocumentAttentionReason(exchanges, documentSetId, documentId)))
      .catch((error) => {
        if (!(error instanceof DOMException && error.name === "AbortError")) setReason(undefined)
      })
    return () => controller.abort()
  }, [documentId, documentSetId])

  if (!reason) return null
  return <div className="mt-4 flex max-w-2xl items-start gap-2 rounded-md border border-destructive/25 bg-destructive/5 px-3 py-2.5 text-left text-sm text-destructive" role="alert"><CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /><span><span className="block font-medium">Needs attention</span><span className="mt-1 block leading-5">{reason}</span></span></div>
}

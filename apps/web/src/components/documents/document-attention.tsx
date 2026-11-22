"use client"

import type { ExchangeResponse } from "@edispark/integration-client"
import { CircleAlert, Sparkles } from "lucide-react"
import { useContext, useEffect, useState } from "react"

import { DocumentTriage } from "@/app/overview/question-composer"
import { Button } from "@/components/ui/button"
import { latestDocumentAttentionReason } from "@/lib/attention-reason"

export function DocumentAttention({ documentSetId, documentId }: { documentSetId: string; documentId: string }) {
  const triage = useContext(DocumentTriage)
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
  return <section aria-label="Needs attention" className="mt-4 flex max-w-2xl flex-wrap items-start gap-3 rounded-md border border-destructive/25 bg-destructive/5 px-3 py-2.5 text-left text-sm">
    <div className="flex min-w-0 flex-1 basis-56 gap-2 text-destructive" role="alert">
      <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <div><p className="font-medium">Needs attention</p><p className="mt-1 leading-5">{reason}</p></div>
    </div>
    {triage && <Button type="button" size="sm" variant="outline" onClick={triage}>
      <Sparkles size={16} aria-hidden="true" /> Triage with AI
    </Button>}
  </section>
}

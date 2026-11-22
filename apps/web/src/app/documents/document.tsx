"use client"

import { useEffect, useState, type ReactNode } from "react"
import { ArrowLeft, ChevronRight } from "lucide-react"
import { Link, useParams } from "react-router-dom"
import type { DocumentResponse } from "@edispark/documents-client"

import { InvoiceDocumentPage } from "@/app/documents/invoice"
import { OrderDocumentPage } from "@/app/documents/order"
import { OperationalDocumentPage } from "@/app/documents/operational-document"
import { LayoutBody } from "@/components/layout/layout"
import { DispatchReturnLink, DocumentTriage, RecordQuestion } from "@/app/overview/question-composer"
import { authClient } from "@/lib/auth-client"
import { documentLabels } from "@/lib/document-workspace"
import { getDocumentSetDetail } from "@/lib/documents-api"
import { smartCopyTransitions } from "@/lib/ubl-smart-copy"

function DocumentNavigation({ children }: { children?: ReactNode }) {
  return <LayoutBody className="mx-auto w-full max-w-[96rem] flex-none pb-0 pt-3">
    <div className="flex flex-wrap items-center justify-between gap-x-4 border-b">
      <nav aria-label="Document navigation" className="flex flex-wrap items-center gap-x-4">
        <Link to="/documents" className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary underline-offset-4 hover:underline">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          Back to documents
        </Link>
        <DispatchReturnLink />
      </nav>
      {children}
    </div>
  </LayoutBody>
}

type DocumentKind = "ORDER" | "ORDER_RESPONSE" | "DESPATCH_ADVICE" | "INVOICE" | "REMITTANCE_ADVICE" | "RECEIPT_ADVICE"

const supportedKinds = new Set<DocumentKind>([
  "ORDER",
  "ORDER_RESPONSE",
  "DESPATCH_ADVICE",
  "INVOICE",
  "REMITTANCE_ADVICE",
  "RECEIPT_ADVICE",
])

export function BusinessDocumentPage() {
  const { setId, documentId } = useParams<{ setId: string; documentId: string }>()
  const { data: organisation, isPending } = authClient.useActiveOrganization()
  const [result, setResult] = useState<{ key: string; document?: DocumentResponse; reference?: string }>()
  const key = `${organisation?.id}/${setId}/${documentId}`
  const [triage, setTriage] = useState<{ key: string; count: number }>()

  useEffect(() => {
    if (isPending || !setId || !documentId) return
    const controller = new AbortController()
    void getDocumentSetDetail(setId, controller.signal)
      .then(({ documentSet, documents }) => {
        if (!controller.signal.aborted) setResult({ key, document: documents.find((item) => item.id === documentId), reference: documentSet.metadata?.businessDocumentNumber && documents.length === 1 ? documentSet.metadata.businessDocumentNumber : undefined })
      })
      .catch(() => { if (!controller.signal.aborted) setResult({ key }) })
    return () => controller.abort()
  }, [documentId, isPending, key, setId])

  const document = !isPending && result?.key === key ? result.document : undefined
  const kind = document?.type as DocumentKind | undefined
  if (setId && document && kind && supportedKinds.has(kind)) {
    const page = kind === "ORDER" ? <OrderDocumentPage /> : kind === "INVOICE" ? <InvoiceDocumentPage /> : <OperationalDocumentPage kind={kind} />
    const label = result?.reference ?? `${documentLabels[kind]} · ${documentId?.slice(0, 8)}`
    const prompts = kind === "INVOICE"
      ? ["Find the order linked to this invoice.", "What issues are recorded for this invoice?"]
      : ["What has happened to this document?", "Which documents are linked to this one?"]
    if (smartCopyTransitions[kind].includes("INVOICE")) prompts.push("Create an invoice from this document.")
    return <DocumentTriage.Provider value={() => setTriage(current => ({ key, count: (current?.count ?? 0) + 1 }))}>
      <DocumentNavigation>
        <RecordQuestion key={key} label={label} prompts={prompts} triageRequest={triage?.key === key ? triage.count : 0} />
      </DocumentNavigation>
      {page}<DocumentExplanation setId={setId} document={document} />
    </DocumentTriage.Provider>
  }
  const unavailable = result?.key === key || !setId || !documentId
  return <>
    <DocumentNavigation />
    <LayoutBody className="mx-auto w-full max-w-[96rem]">
      {unavailable
        ? <h1 className="text-2xl font-semibold tracking-tight">Document unavailable</h1>
        : <p role="status" className="text-sm text-muted-foreground">Loading document…</p>}
    </LayoutBody>
  </>
}

function DocumentExplanation({ setId, document }: { setId: string; document: DocumentResponse }) {
  const relations = document.relations ?? []
  const lifecycle = document.lifecycleStatus === "DRAFT" ? "A draft is available for review."
    : document.lifecycleStatus === "FINAL" ? "This document is finalized. Delivery evidence is recorded on its exchange."
      : document.lifecycleStatus === "CANCELLED" ? "This document was cancelled." : "Lifecycle status is unavailable."
  return <LayoutBody className="mx-auto w-full max-w-[96rem] pt-0">
    <details className="group rounded-lg border bg-card">
      <summary className="flex min-h-11 cursor-pointer list-none items-center gap-2 rounded-lg px-4 text-sm font-semibold transition-colors hover:bg-muted/60 [&::-webkit-details-marker]:hidden">
        <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground motion-safe:transition-transform motion-safe:duration-150 motion-safe:ease-out group-open:rotate-90" aria-hidden="true" />
        Explain this document
      </summary>
      <div className="space-y-3 border-t px-4 py-4 text-sm text-muted-foreground">
        <p>{lifecycle} {document.currentVersion?.versionNumber ? `The current version is ${document.currentVersion.versionNumber}.` : ""}</p>
        {relations.length ? <ul className="space-y-2">{relations.map((relation, index) => relation.targetDocumentId && <li key={index}>
          <Link className="text-primary underline-offset-4 hover:underline" to={`/documents/${relation.targetDocumentSetId ?? setId}/${relation.targetDocumentId}`}>
            {relationLabel(relation.type)}
          </Link>
        </li>)}</ul> : <p>This document records no links to earlier documents.</p>}
      </div>
    </details>
  </LayoutBody>
}

function relationLabel(type: NonNullable<DocumentResponse["relations"]>[number]["type"]) {
  const labels = { RESPONDS_TO: "Open source order", DESPATCHES: "Open despatch source", RECEIVES: "Open source despatch", INVOICES: "Open invoice source", REMITS: "Open source invoice", CORRECTS: "Open corrected document", SUPERSEDES: "Open superseded document", RELATED_TO: "Open related document" }
  return type ? labels[type] : "Open related document"
}

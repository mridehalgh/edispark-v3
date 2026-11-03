"use client"

import { useEffect, useState } from "react"
import { useParams } from "react-router-dom"

import { InvoiceDocumentPage } from "@/app/documents/invoice"
import { OrderDocumentPage } from "@/app/documents/order"
import { LayoutBody } from "@/components/layout/layout"
import { getDocumentSetDetail } from "@/lib/documents-api"

type DocumentKind = "ORDER" | "INVOICE"

export function BusinessDocumentPage() {
  const { setId, documentId } = useParams<{ setId: string; documentId: string }>()
  const [kind, setKind] = useState<DocumentKind>()
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (!setId || !documentId) {
      setFailed(true)
      return
    }
    const controller = new AbortController()
    void getDocumentSetDetail(setId, controller.signal)
      .then(({ documents }) => {
        const type = documents.find((item) => item.id === documentId)?.type
        if (type === "ORDER" || type === "INVOICE") setKind(type)
        else setFailed(true)
      })
      .catch((error) => {
        if (!(error instanceof DOMException && error.name === "AbortError")) setFailed(true)
      })
    return () => controller.abort()
  }, [documentId, setId])

  if (kind === "ORDER") return <OrderDocumentPage />
  if (kind === "INVOICE") return <InvoiceDocumentPage />
  return <LayoutBody className="mx-auto w-full max-w-[96rem] py-8"><p className="text-sm text-muted-foreground">{failed ? "Document unavailable." : "Loading document…"}</p></LayoutBody>
}

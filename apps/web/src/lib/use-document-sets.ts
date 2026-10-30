"use client"

import { useCallback, useEffect, useState } from "react"

import { getDocumentSets, type DocumentSetsPage } from "@/lib/documents-api"

export type DocumentSetSummary = NonNullable<DocumentSetsPage["items"]>[number]

const PAGE_SIZE = 20

export function useDocumentSets() {
  const [page, setPage] = useState<DocumentSetsPage | null>(null)
  const [documentSets, setDocumentSets] = useState<DocumentSetSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)

    void getDocumentSets({ limit: PAGE_SIZE, signal: controller.signal })
      .then((response) => {
        setPage(response)
        setDocumentSets(response.items ?? [])
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return
        setError(requestError instanceof Error ? requestError.message : "Document data could not be loaded.")
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [reloadKey])

  const loadNextPage = useCallback(async () => {
    if (!page?.nextToken || loadingMore) return
    setLoadingMore(true)
    setError(null)

    try {
      const response = await getDocumentSets({
        limit: PAGE_SIZE,
        nextToken: page.nextToken,
      })
      setPage(response)
      setDocumentSets((current) => [...current, ...(response.items ?? [])])
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "The next page could not be loaded.")
    } finally {
      setLoadingMore(false)
    }
  }, [loadingMore, page?.nextToken])

  return {
    documentSets,
    error,
    hasNext: Boolean(page?.hasNext),
    loading,
    loadingMore,
    loadNextPage,
    reload: () => setReloadKey((value) => value + 1),
  }
}

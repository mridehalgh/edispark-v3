"use client"

import { useMemo, useState } from "react"
import { ArrowRight, FileText, RefreshCw, Search } from "lucide-react"
import { Link, useSearchParams } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useDocumentSets } from "@/lib/use-document-sets"

interface OrderSummary {
  setId: string
  documentId: string
  reference: string
  name: string
  direction?: string
  standard?: string
  createdAt?: string
}

function formatDate(value?: string) {
  if (!value) return "Not recorded"
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value))
}

export function DocumentsList() {
  const [searchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get("q") ?? "")
  const {
    documentSets,
    error,
    hasNext,
    loading,
    loadingMore,
    loadNextPage,
    reload,
  } = useDocumentSets()

  const orders = useMemo(() => documentSets.flatMap((documentSet): OrderSummary[] => {
    if (!documentSet.id) return []
    return (documentSet.documents ?? []).flatMap((document) => {
      if (document.type !== "ORDER" || !document.id) return []
      const metadata = documentSet.metadata ?? {}
      return [{
        setId: documentSet.id!,
        documentId: document.id,
        reference: metadata.businessDocumentNumber ?? metadata.name ?? document.id,
        name: metadata.name ?? `Order ${metadata.businessDocumentNumber ?? document.id.slice(0, 8)}`,
        direction: metadata.direction,
        standard: metadata.standard ?? metadata.sourceStandard,
        createdAt: documentSet.createdAt,
      }]
    })
  }), [documentSets])

  const visibleOrders = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return orders
    return orders.filter((order) => [
      order.reference,
      order.name,
      order.direction,
      order.standard,
    ].some((value) => value?.toLowerCase().includes(normalized)))
  }, [orders, query])

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
      <header className="mb-7">
        <h1 className="text-2xl font-semibold tracking-tight">Documents</h1>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">
          Review business documents in the same familiar form used by your operations team.
        </p>
      </header>

      <label className="relative mb-4 block w-full sm:max-w-lg">
        <span className="sr-only">Search orders</span>
        <Search
          className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search order reference, name, or standard…"
          className="pl-9"
        />
      </label>

      {error && (
        <div className="mb-4 flex items-center justify-between gap-4 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">
          <span>{error}</span>
          <Button variant="outline" size="sm" onClick={reload} className="shrink-0 gap-2">
            <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
            Retry
          </Button>
        </div>
      )}

      <section aria-labelledby="orders-title" className="overflow-hidden rounded-lg border bg-card">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <div>
            <h2 id="orders-title" className="text-sm font-semibold">Orders</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">Purchase orders available as business documents.</p>
          </div>
          <span className="text-xs tabular-nums text-muted-foreground">{visibleOrders.length} shown</span>
        </div>

        {loading ? (
          <div className="divide-y" aria-label="Loading orders">
            {Array.from({ length: 5 }, (_, index) => (
              <div key={index} className="grid animate-pulse gap-3 px-4 py-4 md:grid-cols-[minmax(16rem,1fr)_10rem_10rem_8rem]">
                <span className="h-4 rounded bg-muted" />
                <span className="h-4 rounded bg-muted" />
                <span className="h-4 rounded bg-muted" />
                <span className="h-4 rounded bg-muted" />
              </div>
            ))}
          </div>
        ) : (
          <>
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full min-w-[48rem] text-left text-sm">
                <thead className="border-b bg-muted/55 text-xs text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-4 py-2.5 font-medium">Order</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Direction</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Source</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Received</th>
                    <th scope="col" className="px-4 py-2.5"><span className="sr-only">Action</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {visibleOrders.map((order) => (
                    <tr key={`${order.setId}-${order.documentId}`} className="transition-colors hover:bg-muted/45">
                      <td className="px-4 py-3">
                        <span className="block font-medium">{order.name}</span>
                        <span className="mt-0.5 block font-mono text-xs tabular-nums text-muted-foreground">{order.reference}</span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {order.direction ? order.direction.toLowerCase().replace(/^./, (letter) => letter.toUpperCase()) : "Not recorded"}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{order.standard ?? "Not recorded"}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-muted-foreground">{formatDate(order.createdAt)}</td>
                      <td className="px-4 py-3 text-right">
                        <Link
                          to={`/documents/${order.setId}/${order.documentId}`}
                          className="inline-flex min-h-11 items-center gap-1 text-xs font-medium text-primary hover:underline"
                        >
                          View order
                          <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="divide-y md:hidden">
              {visibleOrders.map((order) => (
                <Link
                  key={`${order.setId}-${order.documentId}`}
                  to={`/documents/${order.setId}/${order.documentId}`}
                  className="flex items-start gap-3 px-4 py-4 transition-colors hover:bg-muted/45"
                >
                  <FileText className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{order.name}</span>
                    <span className="mt-1 block font-mono text-xs text-muted-foreground">{order.reference}</span>
                    <span className="mt-2 block text-xs text-muted-foreground">
                      {order.direction ?? "Direction not recorded"} · {order.standard ?? "Standard not recorded"}
                    </span>
                    <span className="mt-1 block text-xs text-muted-foreground">{formatDate(order.createdAt)}</span>
                  </span>
                  <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                </Link>
              ))}
            </div>
          </>
        )}

        {!loading && visibleOrders.length === 0 && (
          <div className="px-4 py-14 text-center">
            <FileText className="mx-auto h-6 w-6 text-muted-foreground" aria-hidden="true" />
            <p className="mt-3 text-sm font-medium">No orders found</p>
            <p className="mt-1 text-xs text-muted-foreground">
              {orders.length === 0 ? "Orders will appear here when they are received." : "Try a different search term."}
            </p>
          </div>
        )}

        {hasNext && (
          <div className="border-t px-4 py-3 text-center">
            <Button variant="outline" size="sm" onClick={() => void loadNextPage()} disabled={loadingMore}>
              {loadingMore ? "Loading…" : "Load more orders"}
            </Button>
          </div>
        )}
      </section>
    </LayoutBody>
  )
}


"use client"

import React, { Component, lazy, Suspense, useEffect, useState } from "react"

const ReactRouterComponent = lazy(() => import("@/app/router"))
const LocalDevelopmentDock = lazy(() => import("@/components/local-development/local-development-dock"))

const RECOVERY_KEY = "edispark:stale-assets-recovery"
const RECOVERY_WINDOW_MS = 10_000

function isStaleAssetError(error: unknown) {
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error)
  return /ChunkLoadError|Loading (?:CSS )?chunk|dynamically imported module|module script failed|CSS_CHUNK_LOAD_FAILED/i.test(message)
}

function recoverFromStaleAssets() {
  const lastRecovery = Number.parseInt(window.sessionStorage.getItem(RECOVERY_KEY) ?? "0", 10)
  if (Date.now() - lastRecovery < RECOVERY_WINDOW_MS) return false
  window.sessionStorage.setItem(RECOVERY_KEY, String(Date.now()))
  window.location.reload()
  return true
}

class ApplicationErrorBoundary extends Component<React.PropsWithChildren, { error?: Error }> {
  state: { error?: Error } = {}

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  componentDidCatch(error: Error) {
    if (isStaleAssetError(error)) recoverFromStaleAssets()
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <main className="grid min-h-screen place-items-center p-6">
        <div className="text-center">
          <h1 className="text-lg font-semibold">The app needs to reload</h1>
          <button className="mt-4 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground" onClick={() => window.location.reload()}>
            Reload
          </button>
        </div>
      </main>
    )
  }
}

function ApplicationLoading() {
  return <main className="grid min-h-screen place-items-center" aria-label="Loading EDI Spark"><span className="h-5 w-32 animate-pulse rounded bg-muted" /></main>
}

export function ClientApplication({ localTools }: { localTools: boolean }) {
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
    const clearRecovery = window.setTimeout(() => window.sessionStorage.removeItem(RECOVERY_KEY), 2_000)
    const handleError = (event: ErrorEvent) => {
      const target = event.target
      const assetUrl = target instanceof HTMLScriptElement
        ? target.src
        : target instanceof HTMLLinkElement
          ? target.href
          : ""
      if (assetUrl.includes("/_next/static/") || isStaleAssetError(event.error ?? event.message)) {
        recoverFromStaleAssets()
      }
    }
    const handleRejection = (event: PromiseRejectionEvent) => {
      if (isStaleAssetError(event.reason)) recoverFromStaleAssets()
    }
    window.addEventListener("error", handleError, true)
    window.addEventListener("unhandledrejection", handleRejection)
    return () => {
      window.clearTimeout(clearRecovery)
      window.removeEventListener("error", handleError, true)
      window.removeEventListener("unhandledrejection", handleRejection)
    }
  }, [])

  if (!mounted) return <ApplicationLoading />
  return (
    <ApplicationErrorBoundary>
      <Suspense fallback={<ApplicationLoading />}>
        <ReactRouterComponent />
        {localTools && <LocalDevelopmentDock />}
      </Suspense>
    </ApplicationErrorBoundary>
  )
}

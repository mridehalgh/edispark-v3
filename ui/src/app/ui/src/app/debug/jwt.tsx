"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { Check, Clipboard, RefreshCw, ShieldCheck, TriangleAlert } from "lucide-react"

import JwtDebugTable from "@/app/debug/components/JwtDebugTable"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { LayoutBody } from "@/components/layout/layout"

interface DecodedJwt {
  header: Record<string, unknown>
  payload: Record<string, unknown>
}

function decodePart(value: string): Record<string, unknown> {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/")
  const decoded = decodeURIComponent(atob(base64).split("").map((character) =>
    `%${character.charCodeAt(0).toString(16).padStart(2, "0")}`
  ).join(""))
  const parsed: unknown = JSON.parse(decoded)
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("JWT part is not an object")
  }
  return parsed as Record<string, unknown>
}

function decodeJwt(token: string): DecodedJwt {
  const parts = token.split(".")
  if (parts.length !== 3) throw new Error("The server returned a malformed JWT")
  return { header: decodePart(parts[0]), payload: decodePart(parts[1]) }
}

function formatTimestamp(value: unknown): string | null {
  return typeof value === "number" ? new Date(value * 1000).toLocaleString() : null
}

export function JwtDebugPage() {
  const [token, setToken] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [copied, setCopied] = useState(false)
  const [jwksKids, setJwksKids] = useState<string[]>([])

  const decoded = useMemo(() => {
    if (!token) return null
    try {
      return decodeJwt(token)
    } catch {
      return null
    }
  }, [token])

  const issueToken = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const [tokenResponse, jwksResponse] = await Promise.all([
        fetch("/api/auth/token", { credentials: "include", headers: { Accept: "application/json" } }),
        fetch("/api/auth/jwks", { credentials: "include", headers: { Accept: "application/json" } }),
      ])
      if (!tokenResponse.ok) throw new Error(tokenResponse.status === 401
        ? "Sign in before requesting a JWT."
        : `Token request failed (${tokenResponse.status}).`)

      const tokenBody = await tokenResponse.json() as { token?: unknown }
      if (typeof tokenBody.token !== "string") throw new Error("Token response did not contain a JWT.")
      setToken(tokenBody.token)

      if (jwksResponse.ok) {
        const jwks = await jwksResponse.json() as { keys?: Array<{ kid?: unknown }> }
        setJwksKids(jwks.keys?.flatMap((key) => typeof key.kid === "string" ? [key.kid] : []) ?? [])
      }
    } catch (cause) {
      setToken("")
      setError(cause instanceof Error ? cause.message : "Unable to request a JWT.")
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void issueToken()
  }, [issueToken])

  const kid = typeof decoded?.header.kid === "string" ? decoded.header.kid : null
  const keyPublished = kid ? jwksKids.includes(kid) : false
  const expiresAt = formatTimestamp(decoded?.payload.exp)
  const issuedAt = formatTimestamp(decoded?.payload.iat)

  async function copyToken() {
    await navigator.clipboard.writeText(token)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }

  return (
    <LayoutBody className="mx-auto w-full max-w-6xl py-8">
      <header className="mb-6">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">JWT diagnostics</h1>
          <Badge variant="secondary">Local development only</Badge>
        </div>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
          Issue and inspect the current user&apos;s Better Auth JWT. Treat copied tokens as credentials.
        </p>
      </header>

      <Card>
        <CardHeader className="gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <CardTitle className="text-lg">Current token</CardTitle>
            <CardDescription className="mt-1">
              {issuedAt ? `Issued ${issuedAt}` : "Request a token from the authenticated session."}
              {expiresAt ? ` · Expires ${expiresAt}` : ""}
            </CardDescription>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => void issueToken()} disabled={isLoading}>
              <RefreshCw className={`mr-2 h-4 w-4 ${isLoading ? "animate-spin" : ""}`} aria-hidden="true" />
              Refresh
            </Button>
            <Button size="sm" onClick={() => void copyToken()} disabled={!token}>
              {copied ? <Check className="mr-2 h-4 w-4" aria-hidden="true" /> : <Clipboard className="mr-2 h-4 w-4" aria-hidden="true" />}
              {copied ? "Copied" : "Copy"}
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {error ? (
            <div role="alert" className="flex gap-3 rounded-md border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
              <TriangleAlert className="h-5 w-5 shrink-0" aria-hidden="true" />
              <span>{error}</span>
            </div>
          ) : (
            <>
              <label htmlFor="jwt-token" className="text-sm font-medium">Encoded JWT</label>
              <textarea
                id="jwt-token"
                readOnly
                value={token}
                spellCheck={false}
                className="mt-2 min-h-32 w-full resize-y rounded-md border bg-muted/30 p-3 font-mono text-xs leading-5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
              <div className="mt-3 flex items-center gap-2 text-sm">
                <ShieldCheck className={`h-4 w-4 ${keyPublished ? "text-success-foreground" : "text-muted-foreground"}`} aria-hidden="true" />
                {keyPublished ? `Signing key ${kid} is present in local JWKS.` : "Signing key was not found in local JWKS."}
              </div>
            </>
          )}
        </CardContent>
      </Card>

      {decoded && (
        <div className="mt-6 grid gap-5 lg:grid-cols-2">
          <JwtDebugTable title="Header" values={decoded.header} />
          <JwtDebugTable title="Payload" values={decoded.payload} />
        </div>
      )}
    </LayoutBody>
  )
}

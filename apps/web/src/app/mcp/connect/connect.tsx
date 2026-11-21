"use client"

import { useEffect, useState, type FormEvent } from "react"
import { authClient } from "@/lib/auth-client"

export default function McpConnect({ signingIn = false }: { signingIn?: boolean }) {
  const { data: organization, isPending } = authClient.useActiveOrganization()
  const [clientName, setClientName] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  useEffect(() => {
    if (signingIn) return
    const query = new URLSearchParams(window.location.search)
    const clientId = query.get("client_id")
    if (!clientId) return
    const controller = new AbortController()
    const clientQuery = new URLSearchParams({ client_id: clientId })
    void fetch(`/api/auth/oauth2/public-client?${clientQuery}`, { signal: controller.signal })
      .then(async response => {
        if (!response.ok) return
        const client = await response.json()
        setClientName(typeof client.client_name === "string" ? client.client_name : clientId)
      }).catch(() => {})
    return () => controller.abort()
  }, [signingIn])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const submitter = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null
    const form = new FormData(event.currentTarget)
    const authorize = submitter?.value === "allow"
    const signedQuery = window.location.search.slice(1)
    setBusy(true)
    setError("")
    try {
      const response = await fetch(signingIn ? "/api/auth/sign-in/email" : "/api/auth/oauth2/consent", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(signingIn
          ? { email: form.get("email"), password: form.get("password"), oauth_query: signedQuery }
          : { accept: authorize, oauth_query: signedQuery }),
      })
      const result = await response.json()
      if (!response.ok || typeof result.url !== "string") throw new Error("OAuth could not continue")
      window.location.assign(result.url)
    } catch {
      setError("The connection could not continue. Activate an eligible workspace in EdiSpark and start again from your MCP client.")
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="mx-auto max-w-lg px-6 py-16">
      <h1 className="mb-4 text-xl font-semibold">{signingIn ? "Sign in to EdiSpark" : "Connect your MCP client"}</h1>
      {!signingIn && <p className="mb-4 text-sm">Allow {clientName || "the requesting client"} to use the requested EDI evidence and preview tools for {organization?.name ?? "your active workspace"}.</p>}
      {error && <p role="alert" className="mb-4 text-sm">{error}</p>}
      <form className="grid gap-4" onSubmit={submit}>
        {signingIn && <>
          <label className="grid gap-1 text-sm">Email<input className="rounded border px-3 py-2" name="email" type="email" autoComplete="username" required /></label>
          <label className="grid gap-1 text-sm">Password<input className="rounded border px-3 py-2" name="password" type="password" autoComplete="current-password" required /></label>
        </>}
        <div className="flex flex-wrap gap-3">
          <button className="rounded border px-4 py-2 text-sm" disabled={busy || (!signingIn && (isPending || !organization || !clientName))} value="allow">
            {signingIn ? "Sign in" : "Allow access"}
          </button>
          {!signingIn && <button className="rounded border px-4 py-2 text-sm" disabled={busy} value="deny">Deny</button>}
          {signingIn && <a className="px-4 py-2 text-sm underline" href="/sign-in">Open EdiSpark sign-in</a>}
        </div>
      </form>
    </main>
  )
}

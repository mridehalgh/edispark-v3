"use client"

import { ArrowDownToLine, ArrowUpFromLine, Radio, RefreshCw } from "lucide-react"
import { useCallback, useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import { LOCAL_MFT_SIMULATOR_EVENT, type LocalSimulationDirection } from "@/lib/local-mft-simulator"

type SimulatorMessage = {
  direction: "INBOUND" | "OUTBOUND"
  messageId: string
  filename?: string
  size: number
}

type SimulatorState = { messages: SimulatorMessage[] }

export function MftSimulatorModule() {
  const [state, setState] = useState<SimulatorState>({ messages: [] })
  const [busy, setBusy] = useState<LocalSimulationDirection>()
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  const refresh = useCallback(async () => {
    try {
      const response = await fetch("/api/mft/local-simulator", { cache: "no-store" })
      const body = await response.json().catch(() => undefined)
      if (!response.ok) throw new Error(body?.error?.message ?? "Simulator status could not be loaded.")
      setState(body as SimulatorState)
      setError(undefined)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Simulator status could not be loaded.")
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  async function simulate(direction: LocalSimulationDirection) {
    setBusy(direction); setError(undefined); setNotice(undefined)
    try {
      const response = await fetch("/api/mft/local-simulator", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ direction }),
      })
      const body = await response.json().catch(() => undefined)
      if (!response.ok) throw new Error(body?.error?.message ?? "The simulation could not be started.")
      setNotice(direction === "INBOUND"
        ? `${body.filename} arrived through the simulator and returned a signed MDN.`
        : `${body.filename} was queued for delivery to the simulator.`)
      window.dispatchEvent(new CustomEvent(LOCAL_MFT_SIMULATOR_EVENT))
      await new Promise((resolve) => setTimeout(resolve, direction === "OUTBOUND" ? 1_500 : 250))
      await refresh()
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "The simulation could not be started.")
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <section className="border-t pt-5" aria-labelledby="local-mft-simulator-title">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Radio className="h-4 w-4 text-primary" aria-hidden="true" />
            <h3 id="local-mft-simulator-title" className="font-semibold">MFT / AS2</h3>
          </div>
          <p className="mt-1 text-xs leading-5 text-muted-foreground">Exchange deterministic EDI samples through the real local AS2 peer.</p>
        </div>
        <Button size="icon" variant="ghost" aria-label="Refresh simulator activity" onClick={() => void refresh()}>
          <RefreshCw className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2">
        <Button variant="outline" className="h-auto justify-start gap-2 py-3" disabled={Boolean(busy)} onClick={() => void simulate("INBOUND")}>
          <ArrowDownToLine className="h-4 w-4 text-primary" aria-hidden="true" />
          <span className="text-left"><span className="block text-sm">Receive order</span><span className="block text-[11px] font-normal text-muted-foreground">Simulator → MFT</span></span>
        </Button>
        <Button variant="outline" className="h-auto justify-start gap-2 py-3" disabled={Boolean(busy)} onClick={() => void simulate("OUTBOUND")}>
          <ArrowUpFromLine className="h-4 w-4 text-primary" aria-hidden="true" />
          <span className="text-left"><span className="block text-sm">Send invoice</span><span className="block text-[11px] font-normal text-muted-foreground">MFT → simulator</span></span>
        </Button>
      </div>

      {error && <p role="alert" className="mt-3 rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">{error}</p>}
      {notice && <p role="status" className="mt-3 rounded-md bg-success/20 px-3 py-2 text-xs text-success-foreground">{notice}</p>}

      <div className="mt-4 border-t pt-3">
        <div className="flex items-center justify-between text-xs"><span className="font-medium">Peer activity</span><span className="tabular-nums text-muted-foreground">{state.messages.length} messages</span></div>
        <ol className="mt-2 max-h-48 space-y-1.5 overflow-y-auto" aria-label="Recent simulator messages">
          {state.messages.slice(0, 6).map((message) => (
            <li key={`${message.direction}:${message.messageId}`} className="flex items-center justify-between gap-3 rounded-md bg-muted/50 px-2.5 py-2 text-xs">
              <span className="min-w-0"><span className="block truncate font-medium">{message.filename ?? "EDI payload"}</span><span className="text-muted-foreground">{message.direction === "INBOUND" ? "Received by simulator" : "Sent by simulator"}</span></span>
              <span className="shrink-0 tabular-nums text-muted-foreground">{message.size} B</span>
            </li>
          ))}
          {state.messages.length === 0 && <li className="py-4 text-center text-xs text-muted-foreground">No live exchanges yet.</li>}
        </ol>
      </div>
    </section>
  )
}

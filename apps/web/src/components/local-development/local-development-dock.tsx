"use client"

import { FlaskConical, LogIn, Wrench } from "lucide-react"
import { useState, type ReactNode } from "react"

import { MftSimulatorModule } from "@/components/local-development/mft-simulator-module"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet"
import { authClient } from "@/lib/auth-client"

type LocalToolModule = {
  id: string
  render: () => ReactNode
}

const authenticatedModules: LocalToolModule[] = [
  { id: "mft-as2", render: () => <MftSimulatorModule /> },
]

export default function LocalDevelopmentDock() {
  const { data: session, isPending } = authClient.useSession()
  const [signingIn, setSigningIn] = useState(false)
  const [error, setError] = useState<string>()

  async function signInAsLocalTester(persona: "member" | "admin") {
    setSigningIn(true); setError(undefined)
    try {
      const response = await fetch("/api/local-test-login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ persona }) })
      if (!response.ok) throw new Error("Local test login is unavailable.")
      window.location.assign("/documents")
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : "Local test login failed.")
      setSigningIn(false)
    }
  }

  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button
          type="button"
          className="fixed bottom-4 right-4 z-40 min-h-11 gap-2 bg-foreground px-4 text-background shadow-lg transition-transform duration-200 hover:-translate-y-0.5 hover:bg-foreground/90 sm:bottom-6 sm:right-6"
          aria-label="Open local development tools"
        >
          <FlaskConical className="h-4 w-4" aria-hidden="true" />
          Local tools
        </Button>
      </SheetTrigger>
      <SheetContent className="w-[min(92vw,28rem)] overflow-y-auto sm:max-w-md">
        <SheetHeader className="text-left">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.14em] text-primary"><Wrench className="h-4 w-4" aria-hidden="true" />Development only</div>
          <SheetTitle>Local tools</SheetTitle>
          <SheetDescription>Sign in to the deterministic workspace and run local service simulations.</SheetDescription>
        </SheetHeader>

        <section className="mt-6" aria-labelledby="local-workspace-title">
          <h3 id="local-workspace-title" className="font-semibold">Demo workspace</h3>
          {isPending ? (
            <p className="mt-2 text-sm text-muted-foreground">Checking local session…</p>
          ) : session ? (
            <div className="mt-2 space-y-2">
              <div className="flex items-center justify-between gap-3 rounded-md bg-muted/55 px-3 py-3">
                <div className="min-w-0"><p className="truncate text-sm font-medium">EdiSpark Local</p><p className="truncate text-xs text-muted-foreground">{session.user.email}</p></div>
                <span className="h-2 w-2 shrink-0 rounded-full bg-success-foreground" aria-label="Signed in" />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <Button size="sm" variant="outline" disabled={signingIn} onClick={() => void signInAsLocalTester("member")}>Operations</Button>
                <Button size="sm" variant="outline" disabled={signingIn} onClick={() => void signInAsLocalTester("admin")}>Administrator</Button>
              </div>
            </div>
          ) : (
            <div className="mt-3 grid grid-cols-2 gap-2">
              <Button className="gap-2" disabled={signingIn} onClick={() => void signInAsLocalTester("member")}>
                <LogIn className="h-4 w-4" aria-hidden="true" />
                Operations
              </Button>
              <Button variant="outline" className="gap-2" disabled={signingIn} onClick={() => void signInAsLocalTester("admin")}>
                <LogIn className="h-4 w-4" aria-hidden="true" />
                Administrator
              </Button>
            </div>
          )}
          {error && <p role="alert" className="mt-3 rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">{error}</p>}
        </section>

        {session && authenticatedModules.map((module) => <div key={module.id}>{module.render()}</div>)}
      </SheetContent>
    </Sheet>
  )
}

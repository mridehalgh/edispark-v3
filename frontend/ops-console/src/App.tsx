import { Activity, Cable, FileStack } from 'lucide-react'
import { Link, Route, Routes } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { runtimeConfig } from '@/lib/runtime-config'

function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-5xl flex-col gap-8 px-6 py-10">
      <header className="space-y-3">
        <p className="text-sm font-medium uppercase tracking-[0.24em] text-muted-foreground">
          EDI operations console scaffold
        </p>
        <div className="space-y-2">
          <h1 className="text-4xl font-semibold tracking-tight text-foreground">
            EdiSpark local frontend foundation
          </h1>
          <p className="max-w-3xl text-base text-muted-foreground">
            React, TypeScript, routing, TanStack Query, Vitest, Testing Library, fast-check,
            and shadcn/ui are configured and ready for the EDI operator experience.
          </p>
        </div>
      </header>

      <section className="grid gap-4 md:grid-cols-3">
        <article className="rounded-lg border bg-card p-5 text-card-foreground shadow-sm">
          <Cable className="mb-3 size-5 text-primary" />
          <h2 className="font-semibold">Backend origin</h2>
          <p className="mt-2 break-all text-sm text-muted-foreground">
            {runtimeConfig.backendOrigin}
          </p>
        </article>
        <article className="rounded-lg border bg-card p-5 text-card-foreground shadow-sm">
          <Activity className="mb-3 size-5 text-primary" />
          <h2 className="font-semibold">OpenAPI endpoint</h2>
          <p className="mt-2 text-sm text-muted-foreground">{runtimeConfig.openApiUrl}</p>
        </article>
        <article className="rounded-lg border bg-card p-5 text-card-foreground shadow-sm">
          <FileStack className="mb-3 size-5 text-primary" />
          <h2 className="font-semibold">Local workflow</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            Start Spring Boot on port 8080, then run the Vite frontend on port 5173.
          </p>
        </article>
      </section>

      <section className="rounded-lg border bg-card p-6 text-card-foreground shadow-sm">
        <h2 className="text-lg font-semibold">Next implementation step</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          This scaffold intentionally stops at runtime wiring and developer workflow setup.
        </p>
        <div className="mt-4 flex flex-wrap gap-3">
          <Button asChild>
            <a href={runtimeConfig.swaggerUiUrl} rel="noreferrer" target="_blank">
              Open Swagger UI
            </a>
          </Button>
          <Button asChild variant="outline">
            <a href={runtimeConfig.openApiUrl} rel="noreferrer" target="_blank">
              Open API docs
            </a>
          </Button>
        </div>
      </section>

      <footer className="text-sm text-muted-foreground">
        <Link className="underline underline-offset-4" to="/">
          Frontend scaffold home
        </Link>
      </footer>
    </main>
  )
}

export default function App() {
  return (
    <Routes>
      <Route element={<HomePage />} path="/" />
    </Routes>
  )
}

"use client"

export default function GlobalError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <html lang="en">
      <body>
        <main className="grid min-h-screen place-items-center p-6">
          <div className="text-center">
            <h1 className="text-lg font-semibold">EDI Spark could not start</h1>
            <button className="mt-4 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground" onClick={reset}>
              Try again
            </button>
          </div>
        </main>
      </body>
    </html>
  )
}

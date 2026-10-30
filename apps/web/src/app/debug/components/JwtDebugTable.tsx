"use client"

interface JwtDebugTableProps {
  title: string
  values: Record<string, unknown> | null
}

export default function JwtDebugTable({ title, values }: JwtDebugTableProps) {
  return (
    <section aria-labelledby={`${title.toLowerCase()}-title`} className="overflow-hidden rounded-lg border">
      <div className="border-b bg-muted/50 px-4 py-3">
        <h2 id={`${title.toLowerCase()}-title`} className="text-sm font-semibold">{title}</h2>
      </div>
      {values ? (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="sr-only">
              <tr><th>Claim</th><th>Value</th></tr>
            </thead>
            <tbody className="divide-y">
              {Object.entries(values).map(([key, value]) => (
                <tr key={key}>
                  <th scope="row" className="w-40 px-4 py-2.5 align-top font-mono text-xs font-medium">{key}</th>
                  <td className="break-all px-4 py-2.5 font-mono text-xs text-muted-foreground">
                    {JSON.stringify(value)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="px-4 py-6 text-sm text-muted-foreground">No values to display.</p>
      )}
    </section>
  )
}

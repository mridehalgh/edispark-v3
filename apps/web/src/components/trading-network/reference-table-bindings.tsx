"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import type { Publish, Summary, TableLookup } from "@edispark/integration-client"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { adminApi } from "@/lib/admin-api"
import { parseReferenceCsv } from "@/lib/reference-csv"

export const referenceTablesEndpoint = "/api/integration/managed-partners/reference-tables"

export function ReferenceTableBindings({ lookups, bindings, onSelect, editable = true, busy }: {
  lookups: TableLookup[]
  bindings: Record<string, string>
  onSelect: (alias: string, revisionId: string) => void
  editable?: boolean
  busy?: boolean
}) {
  const [tables, setTables] = useState<Summary[]>([])
  const [name, setName] = useState("")
  const [keyColumn, setKeyColumn] = useState("")
  const [previousRevisionId, setPreviousRevisionId] = useState("")
  const [file, setFile] = useState<File>()
  const fileInput = useRef<HTMLInputElement>(null)
  const previewRequest = useRef(0)
  const [preview, setPreview] = useState<Publish>()
  const [error, setError] = useState<string>()
  const [publishing, setPublishing] = useState(false)

  const load = useCallback(async (signal?: AbortSignal) => {
    try {
      setTables(await adminApi<Summary[]>(referenceTablesEndpoint, { signal }))
    } catch (cause) {
      if (!signal?.aborted) setError(cause instanceof Error ? cause.message : "Reference tables could not be loaded.")
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void load(controller.signal)
    return () => controller.abort()
  }, [load])

  async function previewCsv(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const request = ++previewRequest.current
    setError(undefined)
    try {
      if (!name.trim() || !file) throw new Error("Enter a table name and choose a CSV file.")
      if (file.size > 300_000) throw new Error("Use a CSV smaller than 300 KB.")
      const csv = await file.text()
      if (request !== previewRequest.current) return
      setPreview({ id: crypto.randomUUID(), previousRevisionId: previousRevisionId || undefined, name: name.trim(), keyColumn: keyColumn.trim(), rows: parseReferenceCsv(csv, keyColumn.trim()) })
    } catch (cause) {
      if (request === previewRequest.current) setError(cause instanceof Error ? cause.message : "Check the CSV file.")
    }
  }

  function invalidatePreview() {
    previewRequest.current += 1
    setPreview(undefined)
  }

  async function publish() {
    if (!preview) return
    setError(undefined)
    setPublishing(true)
    try {
      await adminApi(referenceTablesEndpoint, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(preview),
      })
      setPreview(undefined)
      setFile(undefined)
      if (fileInput.current) fileInput.current.value = ""
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The reference table could not be published.")
    } finally {
      setPublishing(false)
    }
  }

  const aliases = [...new Set(lookups.flatMap((lookup) => lookup.alias ? [lookup.alias] : []))]
  return <div className="grid gap-3 border-t p-4">
    {aliases.map((alias) => {
      const required = lookups.filter((lookup) => lookup.alias === alias).map((lookup) => lookup.resultColumn)
      const matching = tables.filter((table) => table.id && required.every((column) => column && table.columns?.includes(column)))
      const current = Object.hasOwn(bindings, alias) ? bindings[alias] : ""
      return <label key={alias} className="grid max-w-md gap-1 text-sm font-medium">
        Table for {alias}
        <select className="h-9 rounded-md border bg-background px-2" value={current}
          disabled={!editable || busy || publishing}
          onChange={(event) => { if (event.target.value) onSelect(alias, event.target.value) }}>
          <option value="" disabled>Choose a published revision</option>
          {current && !matching.some((table) => table.id === current) && <option value={current}>{current}</option>}
          {matching.map((table) => <option key={table.id} value={table.id}>{table.name} · {table.rowCount} rows · {table.id?.slice(0, 8)}</option>)}
        </select>
      </label>
    })}
    {editable && <details>
      <summary className="cursor-pointer text-sm font-medium">Publish a reference table</summary>
      <form className="mt-3 grid max-w-xl gap-3" onSubmit={previewCsv}>
        <label className="grid gap-1 text-sm">Table name<Input required maxLength={120} value={name}
          onChange={(event) => { setName(event.target.value); invalidatePreview() }} /></label>
        <label className="grid gap-1 text-sm">Key column<Input required maxLength={64} value={keyColumn}
          onChange={(event) => { setKeyColumn(event.target.value); invalidatePreview() }} /></label>
        {tables.length > 0 && <label className="grid gap-1 text-sm">Previous revision (optional)
          <select className="h-9 rounded-md border bg-background px-2" value={previousRevisionId}
            onChange={(event) => { setPreviousRevisionId(event.target.value); invalidatePreview() }}>
            <option value="">New table</option>
            {tables.map((table) => <option key={table.id} value={table.id}>{table.name} · {table.id?.slice(0, 8)}</option>)}
          </select>
        </label>}
        <label className="grid gap-1 text-sm">CSV file<Input ref={fileInput} type="file" accept=".csv,text/csv" required
          onChange={(event) => {
            setFile(event.target.files?.[0])
            invalidatePreview()
            setError(undefined)
          }} /></label>
        <div><Button size="sm" variant="outline" disabled={!file || publishing}>Preview rows</Button></div>
      </form>
      {preview && <div className="mt-3 grid gap-2">
        <p className="text-sm">{preview.rows.length} rows ready to publish. Showing up to five.</p>
        <div className="max-w-full overflow-x-auto">
          <table className="text-left text-sm">
            <thead><tr>{Object.keys(preview.rows[0]).map((column) => <th scope="col" className="px-2 py-1" key={column}>{column}</th>)}</tr></thead>
            <tbody>{preview.rows.slice(0, 5).map((row) => <tr key={row[preview.keyColumn]}>
              {Object.keys(preview.rows[0]).map((column) => <td className="px-2 py-1" key={column}>{row[column]}</td>)}
            </tr>)}</tbody>
          </table>
        </div>
        <div><Button size="sm" disabled={publishing} type="button" onClick={() => void publish()}>Publish revision</Button></div>
      </div>}
    </details>}
    {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
  </div>
}

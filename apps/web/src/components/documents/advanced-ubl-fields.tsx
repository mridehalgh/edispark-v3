"use client"

import { ChevronDown, Plus, Search, Trash2 } from "lucide-react"
import { useMemo, useState } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import type { AuthorableDocumentType, AuthoringAdvancedField } from "@/lib/ubl-authoring"
import { authoringCatalog } from "@/lib/ubl-authoring-catalog"

export function AdvancedUblFields({ documentType, values, onChange }: { documentType: AuthorableDocumentType; values: AuthoringAdvancedField[]; onChange: (values: AuthoringAdvancedField[]) => void }) {
  const [query, setQuery] = useState("")
  const candidates = useMemo(() => authoringCatalog[documentType].filter((field) => !values.some((value) => value.fieldKey === field.fieldKey) && `${field.label} ${field.description} ${field.fieldKey}`.toLowerCase().includes(query.toLowerCase())), [documentType, query, values])

  return <details className="group border-t py-5"><summary className="flex cursor-pointer list-none items-center gap-3 text-sm"><span className="font-semibold">Additional fields</span>{values.length ? <span className="rounded-full bg-muted px-2 py-0.5 text-xs tabular-nums text-muted-foreground">{values.length}</span> : <span className="text-muted-foreground">Optional</span>}<ChevronDown className="ml-auto h-4 w-4 text-muted-foreground transition-transform duration-200 group-open:rotate-180" aria-hidden="true" /></summary><div className="mt-4 grid gap-4 text-sm"><p className="max-w-3xl text-muted-foreground">Add a less common business field only when your partner asks for it.</p><label className="relative grid max-w-xl gap-1.5 font-medium">Find a field<span className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" /><Input className="pl-9" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search buyer reference, tax point…" /></span></label>
    {query ? <div className="max-w-2xl rounded-md border bg-background"><ul className="divide-y">{candidates.map((field) => <li key={field.fieldKey} className="flex items-center gap-3 px-3 py-2.5"><span className="min-w-0 flex-1"><strong className="block font-medium">{field.label}</strong><span className="block text-xs text-muted-foreground">{field.description}</span></span><Button type="button" size="sm" variant="ghost" onClick={() => { onChange([...values, { fieldKey: field.fieldKey, ublKey: field.ublKey, value: "" }]); setQuery("") }}><Plus className="mr-1.5 h-4 w-4" />Add</Button></li>)}{candidates.length === 0 ? <li className="px-3 py-6 text-center text-muted-foreground">No matching fields</li> : null}</ul></div> : null}
    {values.length ? <div className="max-w-3xl divide-y border-y">{values.map((value, index) => {
      const field = authoringCatalog[documentType].find((candidate) => candidate.fieldKey === value.fieldKey)
      return <div key={value.fieldKey} className="grid gap-3 py-3 sm:grid-cols-[minmax(12rem,1fr)_minmax(12rem,1fr)_2.5rem] sm:items-end"><div><p className="font-medium">{field?.label ?? value.fieldKey}</p><p className="text-xs text-muted-foreground">{field?.description}</p></div><Input aria-label={field?.label ?? value.fieldKey} type={field?.inputType ?? "text"} value={value.value} onChange={(event) => onChange(values.map((item, itemIndex) => itemIndex === index ? { ...item, value: event.target.value } : item))} /><Button type="button" size="icon" variant="ghost" onClick={() => onChange(values.filter((_, itemIndex) => itemIndex !== index))} aria-label={`Remove ${field?.label ?? value.fieldKey}`}><Trash2 className="h-4 w-4" /></Button></div>
    })}</div> : null}
  </div></details>
}

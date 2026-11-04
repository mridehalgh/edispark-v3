"use client"

import { useMemo, useState } from "react"
import { ArrowLeft, Check, FileCheck2, Plus, ShieldCheck, Trash2 } from "lucide-react"
import { Link, useNavigate, useSearchParams } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { authoringDefinitions, buildUblDocument, type AuthorableDocumentType, type AuthoringLine } from "@/lib/ubl-authoring"

const documentTypes = Object.keys(authoringDefinitions) as AuthorableDocumentType[]

function newLine(index: number): AuthoringLine {
  return { id: String(index + 1), itemName: "", itemIdentifier: "", quantity: "1", unitCode: "EA", unitPrice: "0.00" }
}

function Field({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return <label className="grid gap-1.5 text-sm font-medium">{label}{children}{hint ? <span className="text-xs font-normal text-muted-foreground">{hint}</span> : null}</label>
}

export function AuthorDocumentPage() {
  const navigate = useNavigate()
  const [search] = useSearchParams()
  const initialType = search.get("type") as AuthorableDocumentType | null
  const [documentType, setDocumentType] = useState<AuthorableDocumentType>(initialType && documentTypes.includes(initialType) ? initialType : "ORDER_RESPONSE")
  const [lines, setLines] = useState<AuthoringLine[]>([newLine(0)])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const definition = authoringDefinitions[documentType]
  const sourceSetId = search.get("fromSet")
  const sourceDocumentId = search.get("fromDocument")
  const related = Boolean(sourceSetId && sourceDocumentId)
  const chain = useMemo(() => ["Order", "Response", "Despatch", "Receipt", "Invoice", "Remittance"], [])

  function updateLine(index: number, field: keyof AuthoringLine, value: string) {
    setLines((current) => current.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line))
  }

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(undefined)
    const form = new FormData(event.currentTarget)
    const content = buildUblDocument({
      documentType,
      id: String(form.get("id")),
      issueDate: String(form.get("issueDate")),
      reference: String(form.get("reference") ?? ""),
      senderName: String(form.get("senderName")),
      senderEndpoint: String(form.get("senderEndpoint")),
      recipientName: String(form.get("recipientName")),
      recipientEndpoint: String(form.get("recipientEndpoint")),
      currency: String(form.get("currency")),
      note: String(form.get("note") ?? ""),
      lines,
    })
    try {
      const response = await fetch("/api/documents", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          documentType,
          content,
          metadata: { name: `${definition.label} ${String(form.get("id"))}`, direction: "OUTBOUND", standard: "UBL", ublVersion: "2.1" },
          related: related ? { setId: sourceSetId, documentId: sourceDocumentId, relationType: definition.relationType } : undefined,
        }),
      })
      const body = await response.json() as { setId?: string; document?: { id?: string }; error?: { message?: string } }
      if (!response.ok) throw new Error(body.error?.message ?? "The draft could not be saved.")
      const documentId = body.document?.id
      if (!body.setId || !documentId) throw new Error("The draft was saved without a document identifier.")
      navigate(`/documents/${body.setId}/${documentId}`)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The draft could not be saved.")
    } finally {
      setBusy(false)
    }
  }

  return <LayoutBody className="mx-auto w-full max-w-[96rem] py-7 sm:py-8">
    <Link to={related && sourceSetId && sourceDocumentId ? `/documents/${sourceSetId}/${sourceDocumentId}` : "/documents"} className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary hover:underline"><ArrowLeft className="h-4 w-4" aria-hidden="true" />{related ? "Back to source document" : "Back to documents"}</Link>

    <header className="mt-5 border-b pb-6">
      <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-sm font-medium text-primary">OASIS UBL 2.1 authoring</p><h1 className="mt-1 text-2xl font-semibold tracking-tight">Create a business document</h1><p className="mt-2 max-w-2xl text-sm text-muted-foreground">Complete the business fields first. EdiSpark keeps the canonical UBL document, then applies the active partner mapping when it is sent as EDIFACT or TRADACOMS.</p></div><Badge variant="secondary">Draft</Badge></div>
      <ol className="mt-6 grid grid-cols-3 gap-px overflow-hidden rounded-md border bg-border text-xs sm:grid-cols-6" aria-label="Order-to-cash document chain">{chain.map((step) => <li key={step} className="bg-background px-3 py-2 text-center text-muted-foreground">{step}</li>)}</ol>
    </header>

    <form onSubmit={save} className="grid gap-8 py-6 xl:grid-cols-[minmax(0,1fr)_19rem]">
      <div className="min-w-0 space-y-10">
        {related ? <section className="border-l-2 border-primary bg-muted/30 px-4 py-3"><p className="text-sm font-medium">Creating a linked response</p><p className="mt-1 text-xs text-muted-foreground">The new document remains in the source document set and will carry a typed {definition.relationType.toLowerCase().replaceAll("_", " ")} relation.</p></section> : null}
        {error ? <p className="rounded-md border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm text-destructive" role="alert">{error}</p> : null}

        <section aria-labelledby="document-heading"><h2 id="document-heading" className="text-base font-semibold">Document</h2><div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Field label="Document type"><select value={documentType} onChange={(event) => setDocumentType(event.target.value as AuthorableDocumentType)} className="h-10 rounded-md border bg-background px-3 text-sm">{documentTypes.map((type) => <option key={type} value={type}>{authoringDefinitions[type].label}</option>)}</select></Field><Field label="Document number"><Input name="id" required autoFocus placeholder="e.g. OR-2026-0043" /></Field><Field label="Issue date"><Input name="issueDate" type="date" required defaultValue={new Date().toISOString().slice(0, 10)} /></Field><Field label="Currency"><Input name="currency" required defaultValue="GBP" maxLength={3} className="uppercase" /></Field>{definition.referenceKey ? <Field label="Referenced document"><Input name="reference" required placeholder="e.g. PO-2026-0042" /></Field> : null}<Field label="Note"><Input name="note" placeholder="Optional business note" /></Field></div></section>

        <section aria-labelledby="parties-heading"><h2 id="parties-heading" className="text-base font-semibold">Parties</h2><div className="mt-4 grid gap-6 lg:grid-cols-2"><fieldset className="grid gap-4 border-t pt-4"><legend className="pr-3 text-sm font-semibold">{definition.senderLabel}</legend><Field label="Legal or trading name"><Input name="senderName" required placeholder="[name]" /></Field><Field label="Electronic address" hint="Usually a GLN for retail EDI."><Input name="senderEndpoint" required placeholder="[partner_identifier]" /></Field></fieldset><fieldset className="grid gap-4 border-t pt-4"><legend className="pr-3 text-sm font-semibold">{definition.recipientLabel}</legend><Field label="Legal or trading name"><Input name="recipientName" required placeholder="[name]" /></Field><Field label="Electronic address" hint="Usually a GLN for retail EDI."><Input name="recipientEndpoint" required placeholder="[tenant_identifier]" /></Field></fieldset></div></section>

        <section aria-labelledby="lines-heading"><div className="flex items-center justify-between gap-4"><div><h2 id="lines-heading" className="text-base font-semibold">Lines</h2><p className="mt-1 text-sm text-muted-foreground">Use one line for each item or referenced financial allocation.</p></div><Button type="button" variant="outline" size="sm" onClick={() => setLines((current) => [...current, newLine(current.length)])}><Plus className="mr-2 h-4 w-4" />Add line</Button></div><div className="mt-4 divide-y border-y">{lines.map((line, index) => <fieldset key={index} className="grid gap-3 py-4 md:grid-cols-[4rem_minmax(10rem,1.5fr)_minmax(8rem,1fr)_7rem_6rem_7rem_2.5rem] md:items-end"><legend className="sr-only">Line {index + 1}</legend><Field label="Line"><Input value={line.id} onChange={(event) => updateLine(index, "id", event.target.value)} required /></Field><Field label="Item name"><Input value={line.itemName} onChange={(event) => updateLine(index, "itemName", event.target.value)} required={documentType !== "REMITTANCE_ADVICE"} /></Field><Field label="Item ID"><Input value={line.itemIdentifier} onChange={(event) => updateLine(index, "itemIdentifier", event.target.value)} required={documentType !== "REMITTANCE_ADVICE"} /></Field><Field label="Quantity"><Input type="number" min="0" step="any" value={line.quantity} onChange={(event) => updateLine(index, "quantity", event.target.value)} required /></Field><Field label="Unit"><Input value={line.unitCode} onChange={(event) => updateLine(index, "unitCode", event.target.value)} required /></Field><Field label="Unit price"><Input type="number" min="0" step="0.01" value={line.unitPrice} onChange={(event) => updateLine(index, "unitPrice", event.target.value)} required /></Field><Button type="button" variant="ghost" size="icon" disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))} aria-label={`Remove line ${index + 1}`}><Trash2 className="h-4 w-4" /></Button></fieldset>)}</div></section>

        <details className="border-y py-4"><summary className="cursor-pointer text-sm font-semibold">Advanced OASIS fields and partner requirements</summary><p className="mt-3 max-w-3xl text-sm text-muted-foreground">The complete OASIS UBL 2.1 contract remains authoritative. Partner-specific required fields are promoted into this form when an active agreement is selected; fields not shown here remain preserved in later immutable versions and transformation evidence.</p></details>
      </div>

      <aside className="xl:sticky xl:top-20 xl:self-start" aria-label="Document readiness"><h2 className="text-sm font-semibold">Readiness</h2><ul className="mt-3 divide-y border-y text-sm"><li className="flex gap-3 py-3"><Check className="mt-0.5 h-4 w-4 text-primary" /><span><strong className="block font-medium">Canonical format</strong><span className="text-xs text-muted-foreground">OASIS UBL 2.1 JSON</span></span></li><li className="flex gap-3 py-3"><ShieldCheck className="mt-0.5 h-4 w-4 text-primary" /><span><strong className="block font-medium">Validation</strong><span className="text-xs text-muted-foreground">Required before finalization</span></span></li><li className="flex gap-3 py-3"><FileCheck2 className="mt-0.5 h-4 w-4 text-primary" /><span><strong className="block font-medium">Partner transform</strong><span className="text-xs text-muted-foreground">Applied only when sent</span></span></li></ul><Button className="mt-5 w-full" disabled={busy}>{busy ? "Saving draft…" : "Save document draft"}</Button><p className="mt-3 text-xs leading-relaxed text-muted-foreground">Saving creates a normal versioned document with lifecycle status Draft. It does not send anything.</p></aside>
    </form>
  </LayoutBody>
}

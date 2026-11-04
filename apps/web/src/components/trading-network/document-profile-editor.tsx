"use client"

import type { DocumentFieldRule, DocumentProfile, SsccIssuer } from "@edispark/integration-client"
import { ChevronDown, Plus, Trash2 } from "lucide-react"
import { useEffect, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

type Props = {
  profile: DocumentProfile
  ssccIssuers: SsccIssuer[]
  busy: boolean
  onSave: (profile: DocumentProfile, rules: DocumentFieldRule[], mode: "MANUAL" | "ASSISTED" | "AUTOMATIC", issuerId?: string) => Promise<void>
  onPublish: (profile: DocumentProfile, rules: DocumentFieldRule[], mode: "MANUAL" | "ASSISTED" | "AUTOMATIC", issuerId?: string) => Promise<void>
  onRevise: (profile: DocumentProfile) => Promise<void>
}

const emptyRule = (): DocumentFieldRule => ({ fieldKey: "", requirement: "OPTIONAL", allowedValues: [] })

export function DocumentProfileEditor({ profile, ssccIssuers, busy, onSave, onPublish, onRevise }: Props) {
  const [rules, setRules] = useState<DocumentFieldRule[]>(profile.fieldRules ?? [])
  const [ssccMode, setSsccMode] = useState<"MANUAL" | "ASSISTED" | "AUTOMATIC">(profile.ssccGenerationMode ?? "MANUAL")
  const [ssccIssuerId, setSsccIssuerId] = useState(profile.ssccIssuerId ?? "")
  useEffect(() => { setRules(profile.fieldRules ?? []); setSsccMode(profile.ssccGenerationMode ?? "MANUAL"); setSsccIssuerId(profile.ssccIssuerId ?? "") }, [profile])

  function updateRule(index: number, patch: Partial<DocumentFieldRule>) {
    setRules((current) => current.map((rule, ruleIndex) => ruleIndex === index ? { ...rule, ...patch } : rule))
  }

  return <details className="group border-b" open={profile.status === "DRAFT"}>
    <summary className="flex min-h-14 cursor-pointer list-none items-center gap-3 py-3 text-sm"><ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground transition-transform duration-200 group-open:rotate-180" aria-hidden="true" /><span className="min-w-0 flex-1"><span className="block truncate font-medium">{profile.name}</span><span className="block text-xs text-muted-foreground">{profile.direction === "OUTBOUND" ? "Send" : "Receive"} {profile.documentType?.toLowerCase().replaceAll("_", " ")} · version {profile.revision}</span></span><Badge variant="secondary">{profile.status?.toLowerCase()}</Badge></summary>
    <div className="border-t pb-5 pt-4">
      <p className="max-w-3xl text-sm text-muted-foreground">Choose only the extra information this partner asks for. Published versions stay unchanged so you can see what applied to earlier documents.</p>
      {profile.status === "DRAFT" ? <>
        {profile.direction === "OUTBOUND" && profile.documentType === "DESPATCH_ADVICE" ? <fieldset className="mt-4 grid gap-3 border-y bg-muted/20 px-3 py-4 md:grid-cols-2"><legend className="sr-only">SSCC generation</legend><label className="grid gap-1.5 text-sm font-medium">Pallet identifier generation<select value={ssccMode} onChange={(event) => setSsccMode(event.target.value as typeof ssccMode)} className="h-10 rounded-md border bg-background px-3 text-sm"><option value="MANUAL">Enter existing SSCCs manually</option><option value="ASSISTED">Let users generate SSCCs</option><option value="AUTOMATIC">Generate when handling units are added</option></select></label><label className="grid gap-1.5 text-sm font-medium">Number range<select value={ssccIssuerId} onChange={(event) => setSsccIssuerId(event.target.value)} disabled={ssccMode === "MANUAL"} className="h-10 rounded-md border bg-background px-3 text-sm disabled:opacity-60"><option value="">Organisation default</option>{ssccIssuers.filter((issuer) => issuer.status === "ACTIVE").map((issuer) => <option key={issuer.id} value={issuer.id}>{issuer.name}{issuer.defaultIssuer ? " · default" : ""}</option>)}</select><span className="text-xs font-normal text-muted-foreground">The published profile keeps this choice for repeatable workflows.</span></label></fieldset> : null}
        <div className="mt-4 divide-y border-y">{rules.map((rule, index) => <details key={index} className="group/rule" open={!rule.fieldKey}><summary className="flex min-h-12 cursor-pointer list-none items-center gap-3 py-2 text-sm"><ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted-foreground transition-transform duration-200 group-open/rule:rotate-180" aria-hidden="true" /><span className="min-w-0 flex-1 truncate font-medium">{rule.fieldKey || `New field rule ${index + 1}`}</span><span className="text-xs capitalize text-muted-foreground">{rule.requirement?.toLowerCase()}</span></summary><fieldset className="grid gap-3 border-t bg-muted/20 px-3 py-4 md:grid-cols-4 md:items-end"><legend className="sr-only">Field rule {index + 1}</legend>
          <label className="grid gap-1.5 text-sm font-medium md:col-span-2">Business field<Input value={rule.fieldKey ?? ""} onChange={(event) => updateRule(index, { fieldKey: event.target.value })} placeholder="invoice.payment.dueDate" required /></label>
          <label className="grid gap-1.5 text-sm font-medium">How it behaves<select value={rule.requirement} onChange={(event) => updateRule(index, { requirement: event.target.value as DocumentFieldRule["requirement"], ...(event.target.value === "HIDDEN" ? { fixedValue: undefined } : {}) })} className="h-10 rounded-md border bg-background px-3 text-sm"><option value="REQUIRED">Required</option><option value="OPTIONAL">Optional</option><option value="HIDDEN">Hide from authoring</option></select></label>
          <Button type="button" size="sm" variant="ghost" className="justify-self-end text-muted-foreground" onClick={() => setRules((current) => current.filter((_, ruleIndex) => ruleIndex !== index))}><Trash2 className="mr-2 h-4 w-4" />Remove</Button>
          <label className="grid gap-1.5 text-sm font-medium">Suggested value<Input value={rule.defaultValue ?? ""} onChange={(event) => updateRule(index, { defaultValue: event.target.value || undefined })} /></label>
          <label className="grid gap-1.5 text-sm font-medium md:col-span-3">Allowed choices<Input value={rule.allowedValues?.join(", ") ?? ""} onChange={(event) => updateRule(index, { allowedValues: event.target.value.split(",").map((value) => value.trim()).filter(Boolean) })} placeholder="Separate choices with commas" /></label>
          <details className="group/advanced md:col-span-4"><summary className="flex min-h-10 cursor-pointer list-none items-center gap-2 rounded-md bg-muted px-3 text-sm font-medium"><ChevronDown className="h-3.5 w-3.5 text-muted-foreground transition-transform duration-200 group-open/advanced:rotate-180" aria-hidden="true" />More checks</summary><div className="mt-3 grid gap-3 md:grid-cols-4 md:items-end"><label className="grid gap-1.5 text-sm font-medium">Only allow this value<Input value={rule.fixedValue ?? ""} disabled={rule.requirement === "HIDDEN"} onChange={(event) => updateRule(index, { fixedValue: event.target.value || undefined })} /></label><label className="grid gap-1.5 text-sm font-medium">Smallest value<Input type="number" step="any" value={rule.minimum ?? ""} onChange={(event) => updateRule(index, { minimum: event.target.value === "" ? undefined : Number(event.target.value) })} /></label><label className="grid gap-1.5 text-sm font-medium">Largest value<Input type="number" step="any" value={rule.maximum ?? ""} onChange={(event) => updateRule(index, { maximum: event.target.value === "" ? undefined : Number(event.target.value) })} /></label><label className="grid gap-1.5 text-sm font-medium">Text format rule<Input value={rule.pattern ?? ""} onChange={(event) => updateRule(index, { pattern: event.target.value || undefined })} placeholder="Optional" /></label><label className="grid gap-1.5 text-sm font-medium md:col-span-4">Message shown when it needs fixing<Input value={rule.message ?? ""} onChange={(event) => updateRule(index, { message: event.target.value || undefined })} placeholder="Tell the user what to change" /></label></div></details>
        </fieldset></details>)}</div>
        {rules.length === 0 ? <div className="mt-4 border-y py-6 text-center"><p className="text-sm font-medium">No extra requirements</p><p className="mt-1 text-sm text-muted-foreground">This partner accepts the usual document details.</p></div> : null}
        <div className="mt-4 flex flex-wrap justify-between gap-2"><Button type="button" size="sm" variant="ghost" onClick={() => setRules((current) => [...current, emptyRule()])}><Plus className="mr-2 h-4 w-4" />Add requirement</Button><div className="flex gap-2"><Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => void onSave(profile, rules, ssccMode, ssccIssuerId || undefined)}>Save draft</Button><Button type="button" size="sm" disabled={busy || rules.some((rule) => !rule.fieldKey)} onClick={() => void onPublish(profile, rules, ssccMode, ssccIssuerId || undefined)}>Publish</Button></div></div>
      </> : <div className="mt-3 flex items-center justify-between"><p className="text-xs text-muted-foreground">{rules.length} configured {rules.length === 1 ? "requirement" : "requirements"}</p>{profile.status === "PUBLISHED" ? <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => void onRevise(profile)}>Edit as new version</Button> : null}</div>}
    </div>
  </details>
}

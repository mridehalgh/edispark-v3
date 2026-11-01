'use client'

import type { TradingPartner } from '@edispark/integration-client'
import { CalendarClock, Save } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

export function PartnerRevisionEditor({ partner, busy, onSave, onActivate }: {
  partner: TradingPartner
  busy: boolean
  onSave: (partner: TradingPartner, form: FormData) => void
  onActivate: (partner: TradingPartner, effectiveAt?: string) => void
}) {
  if (partner.status !== 'DRAFT') return null
  return <section className="mt-3 rounded-md border bg-muted/20 p-3" aria-label={`Edit ${partner.displayName} revision ${partner.revision}`}>
    <form onSubmit={(event) => { event.preventDefault(); onSave(partner, new FormData(event.currentTarget)) }} className="grid gap-3 lg:grid-cols-[1.2fr_0.7fr_1fr_auto] lg:items-end">
      <label className="grid gap-1 text-xs font-medium">Display name<Input name="displayName" defaultValue={partner.displayName} required /></label>
      <label className="grid gap-1 text-xs font-medium">Identity scheme<Input name="scheme" defaultValue={partner.identities?.[0]?.scheme ?? 'GLN'} required /></label>
      <label className="grid gap-1 text-xs font-medium">Identity value<Input name="value" defaultValue={partner.identities?.[0]?.value} required /></label>
      <Button size="sm" disabled={busy}><Save className="mr-1.5 h-4 w-4" />Save draft</Button>
    </form>
    <form onSubmit={(event) => { event.preventDefault(); const form = new FormData(event.currentTarget); onActivate(partner, String(form.get('effectiveAt') || '') || undefined) }} className="mt-3 flex flex-wrap items-end justify-end gap-3 border-t pt-3">
      <label className="grid gap-1 text-xs font-medium">Activate later (optional)<Input type="datetime-local" name="effectiveAt" /></label>
      <Button size="sm" variant="outline" disabled={busy}><CalendarClock className="mr-1.5 h-4 w-4" />Activate revision</Button>
    </form>
  </section>
}

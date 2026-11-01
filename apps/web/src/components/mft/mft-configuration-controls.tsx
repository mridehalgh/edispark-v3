'use client'

import type { CertificateResponse, PartnerResponse } from '@edispark/mft-client'
import { CalendarClock, History, Save, ShieldCheck, Trash2 } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export function MftConfigurationControls({ section, partners, certificates, busy, onRevise, onDeleteDraft, onUpdateDraft, onActivateDraft, onDeleteCertificate }: {
  section: 'activity' | 'partners' | 'certificates'
  partners: PartnerResponse[]
  certificates: CertificateResponse[]
  busy: boolean
  onRevise: (partner: PartnerResponse) => void
  onDeleteDraft: (partner: PartnerResponse) => void
  onUpdateDraft: (partner: PartnerResponse, form: FormData) => void
  onActivateDraft: (partner: PartnerResponse, effectiveAt?: string) => void
  onDeleteCertificate: (certificate: CertificateResponse) => void
}) {
  if (section === 'partners') return <section className="overflow-hidden rounded-lg border bg-card" aria-labelledby="as2-versions-title">
    <div className="border-b px-4 py-3"><h2 id="as2-versions-title" className="text-sm font-semibold">AS2 configuration versions</h2><p className="mt-0.5 text-xs text-muted-foreground">Live profiles are immutable. Create a draft revision to change endpoints, receipt policy, or certificates.</p></div>
    <div className="divide-y">{partners.map((partner) => <div key={partner.id} className="px-4 py-3"><div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center"><div><div className="flex flex-wrap items-center gap-2"><span className="font-medium">{partner.name}</span><Badge variant="secondary">r{partner.revision ?? 1}</Badge><Badge className={cn('border-0', partner.status === 'ACTIVE' ? 'bg-success/30 text-success-foreground' : 'bg-muted text-muted-foreground')}>{partner.status?.toLowerCase().replaceAll('_', ' ')}</Badge></div><p className="mt-1 font-mono text-xs text-muted-foreground">{partner.localAs2Id} → {partner.remoteAs2Id}</p></div><div className="flex gap-2">{partner.status === 'ACTIVE' && <Button size="sm" variant="outline" disabled={busy} onClick={() => onRevise(partner)}><History className="mr-1.5 h-4 w-4" />Create revision</Button>}{partner.status === 'DRAFT' && <Button size="sm" variant="ghost" disabled={busy} onClick={() => onDeleteDraft(partner)}><Trash2 className="mr-1.5 h-4 w-4" />Delete draft</Button>}</div></div>{partner.status === 'DRAFT' && <form onSubmit={(event) => { event.preventDefault(); onUpdateDraft(partner, new FormData(event.currentTarget)) }} className="mt-3 grid gap-3 border-t pt-3 lg:grid-cols-[1fr_1.5fr_auto] lg:items-end"><label className="grid gap-1 text-xs font-medium">Profile name<input name="name" defaultValue={partner.name} required className="h-9 rounded-md border bg-background px-3 text-sm" /></label><label className="grid gap-1 text-xs font-medium">Partner endpoint<input name="endpoint" type="url" defaultValue={partner.endpoint} required className="h-9 rounded-md border bg-background px-3 text-sm" /></label><Button size="sm" disabled={busy}><Save className="mr-1.5 h-4 w-4" />Save draft</Button></form>}{partner.status === 'DRAFT' && <form onSubmit={(event) => { event.preventDefault(); const value = String(new FormData(event.currentTarget).get('effectiveAt') || ''); onActivateDraft(partner, value || undefined) }} className="mt-3 flex flex-wrap items-end justify-end gap-3"><label className="grid gap-1 text-xs font-medium">Activate later (optional)<input type="datetime-local" name="effectiveAt" className="h-9 rounded-md border bg-background px-3 text-sm" /></label><Button size="sm" variant="outline" disabled={busy}><CalendarClock className="mr-1.5 h-4 w-4" />Activate revision</Button></form>}</div>)}</div>
  </section>

  if (section === 'certificates') return <section className="overflow-hidden rounded-lg border bg-card" aria-labelledby="certificate-lifecycle-title">
    <div className="border-b px-4 py-3"><h2 id="certificate-lifecycle-title" className="text-sm font-semibold">Certificate lifecycle</h2><p className="mt-0.5 text-xs text-muted-foreground">Certificate material is immutable. Assigned certificates remain retained for verification and rollover evidence.</p></div>
    <div className="divide-y">{certificates.map((certificate) => <div key={certificate.id} className="flex flex-col justify-between gap-3 px-4 py-3 sm:flex-row sm:items-center"><div><div className="flex items-center gap-2"><ShieldCheck className="h-4 w-4 text-primary" /><span className="font-medium">{certificate.name}</span></div><p className="mt-1 text-xs text-muted-foreground">{certificate.kind === 'LOCAL_IDENTITY' ? 'Local identity' : 'Partner public'} · expires {certificate.notAfter ? new Date(certificate.notAfter).toLocaleDateString('en-GB') : 'unknown'}</p></div><Button size="sm" variant="ghost" disabled={busy} onClick={() => onDeleteCertificate(certificate)}><Trash2 className="mr-1.5 h-4 w-4" />Delete if unused</Button></div>)}</div>
  </section>

  return null
}

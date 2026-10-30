"use client"

import { AlertTriangle, ArrowLeft, CheckCircle2, CircleAlert, Clock3, FileCheck2, KeyRound, Send, ShieldCheck, UsersRound } from "lucide-react"
import { Link } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

const navigation = [
  ["getting-started", "Getting started"],
  ["partner-details", "Partner details"],
  ["activity", "Activity and receipts"],
  ["certificates", "Certificates"],
  ["troubleshooting", "Troubleshooting"],
] as const

function DocNav({ compact = false }: { compact?: boolean }) {
  return <nav aria-label="Setup guide sections" className={cn(compact ? "flex gap-1 overflow-x-auto pb-2" : "space-y-1")}>{navigation.map(([id, label]) => <a key={id} href={`#${id}`} className={cn("text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring", compact ? "shrink-0 rounded-md px-3 py-2" : "block rounded-md px-3 py-2")}>{label}</a>)}</nav>
}

function SectionHeading({ title, children }: { title: string; children: React.ReactNode }) {
  return <header className="mb-6"><h2 className="text-xl font-semibold tracking-tight text-balance">{title}</h2><p className="mt-2 max-w-3xl text-sm leading-7 text-muted-foreground text-pretty">{children}</p></header>
}

function Definition({ term, children }: { term: string; children: React.ReactNode }) {
  return <div className="grid gap-1 border-t py-4 sm:grid-cols-[12rem_1fr] sm:gap-6"><dt className="text-sm font-medium">{term}</dt><dd className="text-sm leading-6 text-muted-foreground">{children}</dd></div>
}

export function MftDocsPage() {
  return <LayoutBody className="w-full px-0 py-0">
    <div className="border-b bg-card/50"><div className="mx-auto flex w-full max-w-[96rem] flex-col gap-5 px-4 py-7 sm:px-6 lg:flex-row lg:items-end lg:justify-between lg:px-8"><div><h1 className="text-2xl font-semibold tracking-tight">File exchange setup guide</h1><p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">Set up a trading partner, send a test file, and understand what each delivery status means.</p></div><Button asChild variant="outline" className="gap-2 self-start"><Link to="/mft"><ArrowLeft className="h-4 w-4" aria-hidden="true" />Back to file exchange</Link></Button></div></div>

    <div className="border-b px-4 pt-2 lg:hidden"><DocNav compact /></div>
    <div className="mx-auto grid w-full max-w-[96rem] lg:grid-cols-[14rem_minmax(0,1fr)] xl:grid-cols-[14rem_minmax(0,1fr)_16rem]">
      <aside className="hidden border-r px-4 py-8 lg:block"><div className="sticky top-24"><p className="mb-3 px-3 text-xs font-medium text-muted-foreground">On this page</p><DocNav /></div></aside>

      <article className="min-w-0 px-4 py-10 sm:px-8 xl:px-12">
        <section id="getting-started" className="scroll-mt-24 border-b pb-12">
          <SectionHeading title="Set up your first connection">You will need a few details from your trading partner before you can exchange files. Your partner’s AS2 contact or integration team can provide them.</SectionHeading>
          <ol className="divide-y border-y">
            {[
              [UsersRound, "Collect the partner details", "Ask for their AS2 identifier, receiving URL, public encryption certificate, and whether they expect an immediate or separate receipt."],
              [KeyRound, "Add the certificate", "Upload the public certificate supplied by the partner. A public certificate can be shared safely; never ask them for their private key."],
              [ShieldCheck, "Add the trading partner", "Enter both AS2 identifiers exactly as supplied, choose the certificates, and select their receipt preference."],
              [Send, "Share your connection information", "Open the partner and copy your AS2 details. Download the public certificate and send both to the partner using the agreed secure channel."],
              [FileCheck2, "Send a test file", "Use a non-production test file agreed with the partner, then watch Activity until the receipt is confirmed."],
            ].map(([Icon, title, copy], index) => <li key={String(title)} className="grid gap-3 py-5 sm:grid-cols-[2.5rem_1fr]"><div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary/10 text-primary"><Icon className="h-4 w-4" aria-hidden="true" /><span className="sr-only">Step {index + 1}</span></div><div><h3 className="text-sm font-semibold">{index + 1}. {String(title)}</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">{String(copy)}</p></div></li>)}
          </ol>
          <div className="mt-6 flex gap-3 rounded-md bg-primary/5 px-4 py-3"><CircleAlert className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" /><p className="text-sm leading-6"><span className="font-medium">AS2 identifiers are case-sensitive.</span> Copy them exactly. A small difference can send a valid file to the wrong connection or cause the partner to reject it.</p></div>
        </section>

        <section id="partner-details" className="scroll-mt-24 border-b py-12">
          <SectionHeading title="Understand the partner details">These values describe the two ends of the AS2 connection. They belong to your organisation’s relationship with the partner, not to the whole EdiSpark service.</SectionHeading>
          <dl><Definition term="Partner name">The familiar name your team will see in Activity and when sending a file.</Definition><Definition term="Your AS2 identifier">The name that identifies your organisation to this partner. Give this exact value to the partner.</Definition><Definition term="Partner AS2 identifier">The exact name the partner uses when they send or receive AS2 messages.</Definition><Definition term="Partner receiving URL">The secure HTTPS address where EdiSpark sends files to this partner.</Definition><Definition term="Your receiving URL">The address shown under “Share your AS2 details”. Give this to the partner so they can send files to you.</Definition><Definition term="Your signing certificate">The public certificate the partner uses to confirm a file genuinely came from your organisation.</Definition><Definition term="Partner encryption certificate">The partner’s public certificate, used to protect files so only they can open them.</Definition><Definition term="Receipt delivery">Choose immediate when the partner returns a receipt in the same connection; choose separate when they send it back later.</Definition></dl>
          <div className="mt-6 flex gap-3 rounded-md border border-warning/40 bg-warning/10 px-4 py-4" role="note"><AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning-foreground" aria-hidden="true" /><div><h3 className="text-sm font-semibold">Confirm encryption compatibility before go-live</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">Your partner must support RSA-OAEP and separate signing and encryption certificates. Legacy RSA PKCS#1 v1.5 decryption is not supported. If the partner cannot support this profile, the connection cannot be activated.</p></div></div>
        </section>

        <section id="activity" className="scroll-mt-24 border-b py-12">
          <SectionHeading title="Read activity and receipts">Activity keeps incoming and outgoing files together. A delivery is complete only when the file has been processed and its receipt has been checked.</SectionHeading>
          <div className="overflow-x-auto"><table className="w-full min-w-[38rem] text-left text-sm"><thead className="border-b bg-muted/55 text-xs text-muted-foreground"><tr><th className="px-4 py-3 font-medium">Status</th><th className="px-4 py-3 font-medium">What it means</th><th className="px-4 py-3 font-medium">What to do</th></tr></thead><tbody className="divide-y"><tr><td className="px-4 py-4"><Badge className="gap-1.5 border-0 bg-success/30 text-success-foreground"><CheckCircle2 className="h-3 w-3" />Delivered</Badge></td><td className="px-4 py-4 text-muted-foreground">The partner’s receipt was checked successfully.</td><td className="px-4 py-4 text-muted-foreground">No action is needed.</td></tr><tr><td className="px-4 py-4"><Badge className="gap-1.5 border-0 bg-warning/25 text-warning-foreground"><Clock3 className="h-3 w-3" />Waiting for receipt</Badge></td><td className="px-4 py-4 text-muted-foreground">The file was sent, but the partner has not returned a receipt yet.</td><td className="px-4 py-4 text-muted-foreground">Wait unless it remains here beyond the agreed response time.</td></tr><tr><td className="px-4 py-4"><Badge className="gap-1.5 border-0 bg-destructive/10 text-destructive"><CircleAlert className="h-3 w-3" />Needs attention</Badge></td><td className="px-4 py-4 text-muted-foreground">The exchange received a permanent error or an invalid receipt.</td><td className="px-4 py-4 text-muted-foreground">Read the reported problem and correct the connection or file before retrying.</td></tr><tr><td className="px-4 py-4"><Badge className="gap-1.5 border-0 bg-warning/25 text-warning-foreground"><Clock3 className="h-3 w-3" />Retry scheduled</Badge></td><td className="px-4 py-4 text-muted-foreground">A temporary network or service problem interrupted delivery.</td><td className="px-4 py-4 text-muted-foreground">No action is normally needed; EdiSpark will try again.</td></tr></tbody></table></div>
          <div className="mt-7 border-t pt-5"><h3 className="text-sm font-semibold">What is a receipt?</h3><p className="mt-2 max-w-3xl text-sm leading-7 text-muted-foreground">An AS2 receipt, also called an MDN, is the partner’s confirmation that they received the message. EdiSpark checks the signed receipt against the file that was sent. This proves transport delivery; it does not necessarily mean the partner’s business system accepted the order or invoice.</p></div>
        </section>

        <section id="certificates" className="scroll-mt-24 border-b py-12">
          <SectionHeading title="Keep certificates current">Certificates have an expiry date. Replace an expiring partner certificate before the agreed changeover date to avoid failed exchanges.</SectionHeading>
          <dl><Definition term="Partner public certificate">Safe to receive and store. It is used to encrypt outbound files or verify the partner’s signature.</Definition><Definition term="Your public certificate">Downloadable from the partner’s “Share your AS2 details” section. It contains no private key material.</Definition><Definition term="Private keys">Do not upload or exchange partner private keys. Production signing and decryption keys are managed securely by EdiSpark and are not available through this workspace.</Definition><Definition term="Fingerprint">A unique value used to confirm that a certificate is the exact file each side intended to send.</Definition><Definition term="Expiry">Agree a rollover date with the partner and add the replacement certificate before the current one expires.</Definition></dl>
          <div className="mt-6 flex gap-3 rounded-md bg-success/20 px-4 py-3"><FileCheck2 className="mt-0.5 h-4 w-4 shrink-0 text-success-foreground" aria-hidden="true" /><p className="text-sm leading-6">When a partner sends a certificate, compare its SHA-256 fingerprint using a separate trusted channel before using it for live files.</p></div>
        </section>

        <section id="troubleshooting" className="scroll-mt-24 pt-12">
          <SectionHeading title="Resolve common problems">Start with the status and receipt shown in Activity. Most connection issues can be traced to one of the checks below.</SectionHeading>
          <div className="divide-y border-y">{[
            ["A file stays on “Waiting for receipt”", "Confirm whether the partner uses immediate or separate receipts and check the agreed response time. Ask the partner whether they received the AS2 message ID."],
            ["The partner rejects the message", "Check both AS2 identifiers for exact spelling and case, confirm the current certificate fingerprints, and make sure the partner supports RSA-OAEP."],
            ["A certificate says “Renew soon”", "Contact the partner for their replacement public certificate and agree when both sides will switch to it."],
            ["The send button is unavailable", "Add a trading partner first. A partner connection needs your signing certificate and the partner’s public encryption certificate."],
            ["An exchange needs attention", "Read the receipt or failure reason in Activity. Correct permanent configuration or file errors before sending again; temporary failures retry automatically."],
          ].map(([title, copy]) => <details key={title} className="group py-4"><summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><span>{title}</span><span className="text-muted-foreground group-open:hidden" aria-hidden="true">+</span><span className="hidden text-muted-foreground group-open:inline" aria-hidden="true">−</span></summary><p className="mt-3 max-w-3xl text-sm leading-6 text-muted-foreground">{copy}</p></details>)}</div>
        </section>
      </article>

      <aside className="hidden border-l px-5 py-10 xl:block"><div className="sticky top-24 space-y-6"><div><h2 className="text-sm font-semibold">Before you start</h2><ul className="mt-3 space-y-3 text-xs leading-5 text-muted-foreground"><li className="flex gap-2"><CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success-foreground" aria-hidden="true" />Your agreed AS2 identifiers</li><li className="flex gap-2"><CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success-foreground" aria-hidden="true" />Partner receiving URL</li><li className="flex gap-2"><CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success-foreground" aria-hidden="true" />Partner public certificate</li><li className="flex gap-2"><CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success-foreground" aria-hidden="true" />Receipt preference</li><li className="flex gap-2"><CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success-foreground" aria-hidden="true" />Agreed test file</li></ul></div><div className="border-t pt-6"><h2 className="text-sm font-semibold">Who is this guide for?</h2><p className="mt-2 text-xs leading-5 text-muted-foreground">Organisation administrators who set up and monitor their own trading partner connections. EdiSpark platform operations are managed separately.</p></div></div></aside>
    </div>
  </LayoutBody>
}

"use client"

import { AlertTriangle, ArrowLeft, BookOpen, CheckCircle2, Database, ExternalLink, KeyRound, LockKeyhole, Server, ShieldCheck, Workflow, XCircle } from "lucide-react"
import { Link } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

const navigation = [
  ["overview", "Overview"],
  ["key-hierarchy", "KMS key hierarchy"],
  ["compatibility", "AS2 compatibility"],
  ["tenant-isolation", "Tenant isolation"],
  ["operations", "Operations"],
  ["sensitive-data", "Sensitive data"],
  ["implementation", "Implementation status"],
] as const

function DocNav({ compact = false }: { compact?: boolean }) {
  return <nav aria-label="MFT documentation sections" className={cn(compact ? "flex gap-1 overflow-x-auto pb-2" : "space-y-1")}>{navigation.map(([id, label]) => <a key={id} href={`#${id}`} className={cn("text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring", compact ? "shrink-0 rounded-md px-3 py-2" : "block rounded-md px-3 py-2")}>{label}</a>)}</nav>
}

function SectionHeading({ eyebrow, title, children }: { eyebrow: string; title: string; children: React.ReactNode }) {
  return <header className="mb-6"><p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">{eyebrow}</p><h2 className="mt-2 text-2xl font-semibold tracking-tight">{title}</h2><p className="mt-2 max-w-3xl text-sm leading-7 text-muted-foreground">{children}</p></header>
}

function Definition({ term, children }: { term: string; children: React.ReactNode }) {
  return <div className="grid gap-1 border-t py-4 sm:grid-cols-[11rem_1fr] sm:gap-6"><dt className="text-sm font-medium">{term}</dt><dd className="text-sm leading-6 text-muted-foreground">{children}</dd></div>
}

export function MftDocsPage() {
  return (
    <LayoutBody className="w-full px-0 py-0">
      <div className="border-b bg-card/50">
        <div className="mx-auto flex w-full max-w-[96rem] flex-col gap-5 px-4 py-7 sm:px-6 lg:flex-row lg:items-end lg:justify-between lg:px-8">
          <div><div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-primary"><BookOpen className="h-4 w-4" aria-hidden="true" />MFT documentation</div><h1 className="mt-2 text-3xl font-semibold tracking-tight">AS2 security and operations</h1><p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">Production architecture for tenant-isolated EDI exchange using AWS KMS, signed MDN evidence, and bounded deployment cells.</p></div>
          <Button asChild variant="outline" className="gap-2 self-start"><Link to="/mft"><ArrowLeft className="h-4 w-4" aria-hidden="true" />Back to operations</Link></Button>
        </div>
      </div>

      <div className="border-b px-4 pt-2 lg:hidden"><DocNav compact /></div>

      <div className="mx-auto grid w-full max-w-[96rem] lg:grid-cols-[14rem_minmax(0,1fr)] xl:grid-cols-[14rem_minmax(0,1fr)_15rem]">
        <aside className="hidden border-r px-4 py-8 lg:block"><div className="sticky top-24"><p className="mb-3 px-3 text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">On this page</p><DocNav /></div></aside>

        <article className="min-w-0 px-4 py-10 sm:px-8 xl:px-12">
          <section id="overview" className="scroll-mt-24 border-b pb-12">
            <SectionHeading eyebrow="Architecture" title="The production decision">Private AS2 keys never enter an application process, database, payload store, container filesystem, or log.</SectionHeading>
            <div className="grid border-y sm:grid-cols-3">
              <div className="border-b py-5 sm:border-b-0 sm:border-r sm:pr-5"><Database className="h-5 w-5 text-primary" aria-hidden="true" /><p className="mt-3 text-sm font-semibold">Tenant data</p><p className="mt-1 text-sm leading-6 text-muted-foreground">One symmetric key per tenant, reused by approved EdiSpark services.</p></div>
              <div className="border-b py-5 sm:border-b-0 sm:border-r sm:px-5"><KeyRound className="h-5 w-5 text-primary" aria-hidden="true" /><p className="mt-3 text-sm font-semibold">AS2 signing</p><p className="mt-1 text-sm leading-6 text-muted-foreground">One non-exportable RSA signing key per deployment cell.</p></div>
              <div className="py-5 sm:pl-5"><LockKeyhole className="h-5 w-5 text-primary" aria-hidden="true" /><p className="mt-3 text-sm font-semibold">AS2 decryption</p><p className="mt-1 text-sm leading-6 text-muted-foreground">One separate non-exportable RSA decryption key per cell.</p></div>
            </div>
            <p className="mt-6 text-sm leading-7 text-muted-foreground">The AWS cost-conscious pattern means one symmetric key <strong className="font-medium text-foreground">per tenant across services</strong>. It does not mean one data key shared by every tenant. Only the AS2 asymmetric key set is shared, and only inside a bounded cell.</p>
          </section>

          <section id="key-hierarchy" className="scroll-mt-24 border-b py-12">
            <SectionHeading eyebrow="AWS KMS" title="Key hierarchy">Aliases are stable routing names. Key IDs and certificate versions are immutable evidence.</SectionHeading>
            <div className="overflow-hidden rounded-lg border bg-muted/25 font-mono text-xs leading-6"><div className="border-b px-4 py-3 text-muted-foreground">Tenant data encryption</div><div className="px-4 py-4">alias/edispark/tenant/&#123;tenant-id&#125;/data</div><div className="border-y px-4 py-3 text-muted-foreground">Cell AS2 identity</div><div className="grid sm:grid-cols-2"><div className="border-b px-4 py-4 sm:border-b-0 sm:border-r">alias/edispark/&#123;cell&#125;/as2-signing</div><div className="px-4 py-4">alias/edispark/&#123;cell&#125;/as2-decryption</div></div></div>
            <dl className="mt-7">
              <Definition term="Tenant data key">A symmetric customer-managed key used by MFT, Documents, routing, and approved services. STS sessions are restricted to the exact tenant alias.</Definition>
              <Definition term="Encryption context"><code className="text-foreground">tenant_id</code>, <code className="text-foreground">service</code>, <code className="text-foreground">resource_type</code>, and immutable <code className="text-foreground">resource_id</code>. It contains identifiers, never PII or business content.</Definition>
              <Definition term="Signing key">RSA KMS key with <code className="text-foreground">SIGN_VERIFY</code>. The signing worker receives <code className="text-foreground">kms:Sign</code>, never decrypt or administration permission.</Definition>
              <Definition term="Decryption key">RSA KMS key with <code className="text-foreground">ENCRYPT_DECRYPT</code>. The inbound worker receives <code className="text-foreground">kms:Decrypt</code>, never sign or administration permission.</Definition>
            </dl>
          </section>

          <section id="compatibility" className="scroll-mt-24 border-b py-12">
            <SectionHeading eyebrow="Partner profile" title="KMS-native AS2 compatibility">Every partnership must pass this gate before production activation.</SectionHeading>
            <div className="border-y border-warning/40 bg-warning/10 px-4 py-4" role="note"><div className="flex gap-3"><AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning-foreground" aria-hidden="true" /><div><h3 className="font-semibold">RSA PKCS#1 v1.5 is not supported</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">AWS KMS RSA decryption supports RSA-OAEP only. Partners must also accept separate signing and encryption certificates because one KMS RSA key cannot perform both operations.</p></div></div></div>
            <div className="mt-7 overflow-x-auto"><table className="w-full min-w-[38rem] text-left text-sm"><thead className="border-b text-xs uppercase tracking-wide text-muted-foreground"><tr><th className="py-3 pr-5 font-medium">Capability</th><th className="py-3 pr-5 font-medium">Required profile</th><th className="py-3 font-medium">Decision</th></tr></thead><tbody className="divide-y"><tr><td className="py-4 pr-5 font-medium">Key transport</td><td className="py-4 pr-5 text-muted-foreground">RSA-OAEP SHA-256 preferred</td><td className="py-4"><Badge className="border-0 bg-success/30 text-success-foreground">Required</Badge></td></tr><tr><td className="py-4 pr-5 font-medium">Certificates</td><td className="py-4 pr-5 text-muted-foreground">Separate signing and encryption</td><td className="py-4"><Badge className="border-0 bg-success/30 text-success-foreground">Required</Badge></td></tr><tr><td className="py-4 pr-5 font-medium">RSA PKCS#1 v1.5</td><td className="py-4 pr-5 text-muted-foreground">Legacy decryption</td><td className="py-4"><Badge variant="destructive">Rejected</Badge></td></tr><tr><td className="py-4 pr-5 font-medium">Signed MDN</td><td className="py-4 pr-5 text-muted-foreground">SHA-256 MIC and verified receipt</td><td className="py-4"><Badge className="border-0 bg-success/30 text-success-foreground">Required</Badge></td></tr></tbody></table></div>
            <p className="mt-5 text-sm leading-6 text-muted-foreground">There is no production software-key fallback. An incompatible partnership remains inactive until the partner enables the required profile.</p>
          </section>

          <section id="tenant-isolation" className="scroll-mt-24 border-b py-12">
            <SectionHeading eyebrow="Security boundary" title="Tenant isolation">Tenant identity is established before any payload, certificate, or private-key operation.</SectionHeading>
            <dl><Definition term="Control plane">Spring Security validates issuer, audience, algorithm, signature, and the JWT <code className="text-foreground">tenant_id</code> claim.</Definition><Definition term="Inbound AS2">An atomically unique, exact, case-sensitive <code className="text-foreground">(AS2-To, AS2-From)</code> pair resolves one tenant and cell.</Definition><Definition term="Persistence">DynamoDB partition keys and S3 object prefixes include the trusted tenant boundary. Payload IDs are non-guessable.</Definition><Definition term="Background work">Workers obtain tenant and cell from the conditionally leased transfer—not ambient request state or a caller-supplied key ARN.</Definition></dl>
            <div className="mt-7 border-l-2 border-primary pl-5"><h3 className="font-semibold">Shared asymmetric-key tradeoff</h3><p className="mt-2 text-sm leading-7 text-muted-foreground">Asymmetric KMS operations cannot use encryption context. Shared AS2 keys therefore provide cell-level custody, not per-tenant cryptographic isolation. Small cells, separate signing/decryption roles, immutable tenant-to-cell assignment, per-tenant quotas, policy tests, and CloudTrail alerts contain that blast radius.</p></div>
          </section>

          <section id="operations" className="scroll-mt-24 border-b py-12">
            <SectionHeading eyebrow="Runbook" title="Operate without private-key fallback">KMS authorization and protocol failures are explicit operational states, never a reason to load a local key.</SectionHeading>
            <div className="grid gap-x-8 sm:grid-cols-2">{[["Provision", "Assign the tenant to a cell, create its symmetric data key, reserve the AS2 identity pair, and prove exact alias/context authorization."], ["Activate", "Verify partner fingerprints and complete bidirectional RSA-OAEP, signed MDN, replay, timeout, and rollover tests."], ["Rotate", "Create a new signing/decryption pair and two public certificates. Overlap versions until every partner completes its grace period."], ["Recover", "Retain ciphertext, Message-ID, transfer state, certificate fingerprint, key version, and KMS request ID. Never retain plaintext keys."]].map(([title, copy]) => <div key={title} className="border-t py-5"><h3 className="text-sm font-semibold">{title}</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">{copy}</p></div>)}</div>
            <details className="group border-y py-4"><summary className="cursor-pointer list-none text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">KMS incident response <span className="ml-2 text-muted-foreground group-open:hidden">+</span><span className="ml-2 hidden text-muted-foreground group-open:inline">−</span></summary><ul className="mt-4 space-y-2 pl-5 text-sm leading-6 text-muted-foreground"><li className="list-disc">Retry only throttles, timeouts, and documented transient failures with bounded jitter.</li><li className="list-disc">Do not retry access denial, disabled keys, incorrect keys, invalid ciphertext, or unsupported algorithms.</li><li className="list-disc">Treat shared-key failure as a cell incident and derive affected tenants from the trusted registry.</li><li className="list-disc">Preserve queued work and evidence; move exhausted work to operator review.</li></ul></details>
          </section>

          <section id="sensitive-data" className="scroll-mt-24 border-b py-12">
            <SectionHeading eyebrow="Data protection" title="What the service still stores">Removing application-held private keys does not remove commercially sensitive EDI data or operational evidence.</SectionHeading>
            <dl><Definition term="EDI payloads">Confidential and potentially personal. Store in S3 with Block Public Access, TLS, tenant authorization, SSE-KMS, retention, and audited deletion.</Definition><Definition term="Public certificates">Not secret, but integrity-critical. Store the chain, fingerprint, purpose, activation window, expiry, and rollover status.</Definition><Definition term="Transfer evidence">AS2 IDs, endpoints, filenames, MDNs, MICs, retries, errors, and timestamps are tenant-scoped confidential operational records.</Definition><Definition term="Logs">Record IDs, cell, key version, algorithm, latency, outcome, and KMS request ID. Never record payloads, tokens, passwords, plaintext data keys, or private material.</Definition></dl>
          </section>

          <section id="implementation" className="scroll-mt-24 pt-12">
            <SectionHeading eyebrow="Delivery status" title="What is implemented">The local AS2 vertical slice is working; production managed adapters remain intentionally fail-closed.</SectionHeading>
            <div className="grid gap-3 text-sm sm:grid-cols-2"><div className="flex gap-3 border-t py-4"><CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success-foreground" aria-hidden="true" /><span>Tenant-aware control plane, AS2 exchange, signed MDNs, retries, evidence, UI, and local simulator.</span></div><div className="flex gap-3 border-t py-4"><CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success-foreground" aria-hidden="true" /><span>Local PKCS#12 and encrypted file adapters restricted to local/OpenAPI profiles.</span></div><div className="flex gap-3 border-t py-4"><XCircle className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" /><span className="text-muted-foreground">KMS-aware CMS signer/decrypter and RSA-OAEP protocol tests remain to be implemented.</span></div><div className="flex gap-3 border-t py-4"><XCircle className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" /><span className="text-muted-foreground">Production DynamoDB, S3 evidence storage, leases, rotation, and alarms remain to be implemented.</span></div></div>
          </section>
        </article>

        <aside className="hidden border-l px-5 py-10 xl:block"><div className="sticky top-24 space-y-7"><div><p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Security posture</p><div className="mt-3 flex items-center gap-2 text-sm font-medium"><ShieldCheck className="h-4 w-4 text-success-foreground" aria-hidden="true" />KMS-only production</div><div className="mt-2 flex items-center gap-2 text-sm font-medium"><Server className="h-4 w-4 text-primary" aria-hidden="true" />Cell-bounded AS2 keys</div><div className="mt-2 flex items-center gap-2 text-sm font-medium"><Workflow className="h-4 w-4 text-primary" aria-hidden="true" />No private-key fallback</div></div><div className="border-t pt-6"><p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Primary source</p><a className="mt-3 inline-flex items-start gap-1.5 text-sm font-medium text-primary hover:underline" href="https://aws.amazon.com/blogs/architecture/simplify-multi-tenant-encryption-with-a-cost-conscious-aws-kms-key-strategy/" target="_blank" rel="noreferrer">AWS KMS tenant strategy <ExternalLink className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" /><span className="sr-only">opens in a new tab</span></a></div><div className="border-t pt-6"><p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Support boundary</p><p className="mt-2 text-xs leading-5 text-muted-foreground">RSA-OAEP and separate signing/encryption certificates are mandatory.</p></div></div></aside>
      </div>
    </LayoutBody>
  )
}

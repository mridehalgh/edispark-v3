"use client"

import { ArrowDown, ArrowRight, Check, CircleAlert, Copy, FileSearch, GitBranch, ShieldCheck } from "lucide-react"
import { useState } from "react"

import { clients, prompts, tools } from "./guide"
import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useAccess } from "@/lib/use-access"

const sections = [
  ["connect", "Connect your assistant"],
  ["prompts", "Try a prompt"],
  ["tools", "Tool reference"],
  ["troubleshooting", "Troubleshooting"],
] as const

function CopyButton({ text, label }: { text: string; label: string }) {
  const [feedback, setFeedback] = useState<"ready" | "copied" | "failed">("ready")

  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setFeedback("copied")
    } catch {
      setFeedback("failed")
    }
  }

  return (
    <div className="flex shrink-0 items-center gap-2">
      <Button type="button" size="sm" variant="outline" aria-label={`Copy ${label}`} onClick={copy}>
        {feedback === "copied" ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
        {feedback === "copied" ? "Copied" : "Copy"}
      </Button>
      <span className={feedback === "failed" ? "text-xs text-muted-foreground" : "sr-only"} role="status">
        {feedback === "copied" ? `Copied ${label}` : feedback === "failed" ? "Select and copy the text manually." : ""}
      </span>
    </div>
  )
}

function CodeExample({ text, label }: { text: string; label: string }) {
  return (
    <div className="my-4 rounded-lg border bg-muted/30">
      <div className="flex items-center justify-between gap-2 border-b px-3 py-2">
        <span className="text-xs font-medium text-muted-foreground">{label}</span>
        <CopyButton text={text} label={label} />
      </div>
      <pre tabIndex={0} aria-label={label} className="overflow-x-auto rounded-b-lg p-4 text-xs leading-6 focus-visible:outline focus-visible:outline-ring"><code>{text}</code></pre>
    </div>
  )
}

function SectionHeading({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <header className="mb-6">
      <h2 className="text-2xl font-semibold tracking-tight">{title}</h2>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">{children}</p>
    </header>
  )
}

export function AiPage() {
  const { access, loading } = useAccess()
  const endpoint = access?.mcpSetup?.endpoint

  return (
    <LayoutBody className="min-w-0 px-0 py-0">
      <section className="border-b bg-primary/[0.045]">
        <div className="mx-auto grid max-w-6xl items-center gap-8 px-4 py-10 sm:px-8 lg:grid-cols-[1fr_0.83fr] lg:gap-12 lg:py-14">
          <div>
            <p className="mb-3 text-sm font-semibold text-primary">EdiSpark AI</p>
            <h1 className="max-w-xl text-balance text-4xl font-semibold tracking-tight sm:text-5xl">Bring EDI evidence into your assistant</h1>
            <p className="mt-4 max-w-lg text-pretty leading-7 text-muted-foreground">Follow a failed exchange, test a mapping, or check an outbound document using the rules and revisions already in EdiSpark.</p>
            <Button asChild className="mt-6"><a href="#connect">Connect your assistant <ArrowDown aria-hidden="true" /></a></Button>
          </div>
          <figure className="rounded-xl border bg-card">
            <figcaption className="flex items-center justify-between gap-4 border-b px-5 py-4">
              <span className="text-sm font-semibold">B&Q acknowledgement</span>
              <span className="text-xs font-medium text-muted-foreground">Demo example</span>
            </figcaption>
            <ol className="divide-y px-5">
              <li className="flex gap-3 py-4"><FileSearch aria-hidden="true" className="mt-1 h-4 w-4 shrink-0 text-primary" /><div><p className="text-sm font-medium">Open the document and its outbound route</p><p className="mt-1 text-xs text-muted-foreground">B&Q ACK v1.4 · TRADACOMS</p></div></li>
              <li className="flex gap-3 py-4"><GitBranch aria-hidden="true" className="mt-1 h-4 w-4 shrink-0 text-primary" /><div><p className="text-sm font-medium">Check the pinned mapping and document</p><p className="mt-1 break-all font-mono text-xs text-muted-foreground">check_outbound_readiness</p></div></li>
              <li className="flex gap-3 py-4"><CircleAlert aria-hidden="true" className="mt-1 h-4 w-4 shrink-0 text-warning-foreground" /><div><p className="text-sm font-medium">Blocked · Vendor number required</p><p className="mt-1 break-all font-mono text-xs text-muted-foreground">BQ_VENDOR_NUMBER_REQUIRED</p></div></li>
            </ol>
          </figure>
        </div>
      </section>

      <div className="mx-auto grid max-w-6xl lg:grid-cols-[13rem_minmax(0,1fr)]">
        <aside className="min-w-0 border-b px-4 py-3 lg:border-b-0 lg:border-r lg:py-8">
          <nav aria-label="AI page sections" className="flex gap-1 overflow-x-auto lg:sticky lg:top-24 lg:block lg:space-y-1">
            {sections.map(([id, title]) => <a key={id} href={`#${id}`} className="block shrink-0 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-foreground focus-visible:outline focus-visible:outline-ring">{title}</a>)}
          </nav>
        </aside>

        <article className="min-w-0 px-4 sm:px-8 lg:px-10">
          <section id="connect" className="scroll-mt-24 border-b py-10">
            <SectionHeading title="Connect your assistant">Use Model Context Protocol (MCP) to let an approved client request evidence and run previews for your active organisation.</SectionHeading>
            <div className="mb-8 rounded-lg border px-4 py-4 sm:px-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="text-sm font-semibold">Workspace MCP endpoint</h3>
                  {endpoint
                    ? <code className="mt-2 block break-all text-sm text-muted-foreground">{endpoint}</code>
                    : <p className="mt-1 text-sm text-muted-foreground">{loading ? "Checking workspace setup…" : "MCP setup is unavailable for this organisation. Ask your EdiSpark operator to enable the pilot and register your client."}</p>}
                </div>
                {endpoint && <CopyButton text={endpoint} label="MCP endpoint" />}
              </div>
              <p className="mt-3 text-sm text-muted-foreground">Stateless Streamable HTTP · MCP <code>2026-07-28</code> · OAuth with S256 PKCE</p>
            </div>

            <dl className="mb-8 space-y-4 text-sm leading-6">
              <div><dt className="font-semibold">Ask the EdiSpark operator for a client registration</dt><dd className="text-muted-foreground">Share the client’s exact redirect URI. The operator preregisters the client, links it to the MCP resource, and grants <code>edi:evidence</code> for document and exchange evidence, <code>edi:preview</code> for mapping and readiness checks, and <code>mft:evidence</code> for AS2 operations.</dd></div>
              <div><dt className="font-semibold">Sign in and approve the connection</dt><dd className="text-muted-foreground">Activate the intended organisation in EdiSpark. Configure your client, complete browser sign-in, and check that it lists the tools. Use a reachable HTTPS endpoint for cloud clients. Use the registered loopback callback for a local CLI.</dd></div>
            </dl>

            <Tabs defaultValue="claude">
              <TabsList aria-label="MCP clients" className="h-auto w-full justify-start overflow-x-auto">
                {clients.map(client => <TabsTrigger key={client.id} value={client.id} className="min-h-9">{client.name}</TabsTrigger>)}
              </TabsList>
              {clients.map(client => (
                <TabsContent key={client.id} value={client.id} className="pt-4">
                  <h3 className="font-semibold">{client.name} <span className="ml-1 text-sm font-normal text-muted-foreground">· {client.description}</span></h3>
                  <p className="mt-1 text-xs text-muted-foreground">Based on vendor documentation. EdiSpark sign-in with this client is unverified.</p>
                  <ol className="mt-3 list-decimal space-y-2 pl-5 text-sm leading-6 text-muted-foreground">
                    {client.steps.map(step => <li key={step}>{step}</li>)}
                  </ol>
                  {"snippet" in client && <CodeExample text={client.snippet} label={`${client.name} setup example`} />}
                  <a className="mt-3 inline-flex min-h-9 items-center gap-1.5 text-sm font-medium text-primary underline-offset-4 hover:underline" href={client.source} target="_blank" rel="noreferrer">{client.name} documentation <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" /></a>
                </TabsContent>
              ))}
            </Tabs>
            <p className="mt-6 border-t pt-4 text-sm leading-6 text-muted-foreground"><strong className="text-foreground">Other clients.</strong> Check for <code>2026-07-28</code> discovery over Streamable HTTP, a preregistered OAuth client, the exact redirect URI, and an OAuth resource matching the MCP URL.</p>
          </section>

          <section id="prompts" className="scroll-mt-24 border-b py-10">
            <SectionHeading title="Try a prompt">Use a connected assistant and replace the bracketed values with records from your workspace.</SectionHeading>
            <div className="divide-y border-y">
              {prompts.map(({ title, prompt }) => <div key={title} className="grid gap-3 py-4 sm:grid-cols-[1fr_auto] sm:items-start"><div><h3 className="text-sm font-semibold">{title}</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">{prompt}</p></div><CopyButton text={prompt} label={`${title.toLowerCase()} prompt`} /></div>)}
            </div>
          </section>

          <section id="tools" className="scroll-mt-24 border-b py-10">
            <SectionHeading title="Tool reference">Use <code>tools/list</code> for the server’s JSON schemas. These examples show the argument objects for <code>tools/call</code>. Replace placeholder IDs, hashes, and fixture values before running a tool.</SectionHeading>
            <div className="divide-y border-y">
              {tools.map(tool => (
                <details key={tool.name} className="group">
                  <summary className="cursor-pointer break-words rounded-sm py-4 focus-visible:outline focus-visible:outline-ring">
                    <code className="text-sm font-semibold text-foreground">{tool.name}</code>
                    <span className="ml-2 whitespace-nowrap text-xs text-muted-foreground">{tool.scope}</span>
                    <span className="mt-1 block text-sm leading-6 text-muted-foreground">{tool.summary}</span>
                  </summary>
                  <div className="pb-4">
                    <p className="text-sm leading-6 text-muted-foreground">{tool.usage}</p>
                    {tool.examples.map((args, index) => <CodeExample key={index} text={JSON.stringify(args, null, 2)} label={tool.examples.length === 1 ? `${tool.name} arguments` : index === 0 ? "Synthetic fixture arguments" : "Retained document arguments"} />)}
                    <p className="break-words text-sm leading-6 text-muted-foreground"><strong className="text-foreground">Key result fields</strong><br />{tool.result}</p>
                  </div>
                </details>
              ))}
            </div>
            <p className="mt-5 flex gap-2 text-sm leading-6 text-muted-foreground"><ShieldCheck className="mt-1 h-4 w-4 shrink-0 text-primary" aria-hidden="true" /><span>Tools inspect evidence and preview changes. Use the EdiSpark workspace for raw files, publishing mappings, and sending documents.</span></p>
          </section>

          <section id="troubleshooting" className="scroll-mt-24 py-10">
            <SectionHeading title="Troubleshooting">Use the client’s error and the MCP tool results to narrow down the problem.</SectionHeading>
            <dl className="divide-y border-y text-sm leading-6">
              {[
                ["Sign-in fails", "Check the active EdiSpark organisation, pilot enrolment, preregistered client ID, exact redirect URI, and resource URL. Reconnect if consent has been revoked."],
                ["INSUFFICIENT_SCOPE", "Check the required scope in the tool reference. Reconnect and approve the updated grant."],
                ["Discovery or protocol mismatch", "Check that your client supports stateless Streamable HTTP with MCP 2026-07-28. Check network reachability from the client’s execution environment."],
                ["STALE_SOURCE", "Get the latest representation ID and content hash from the workspace or fresh evidence before repeating the preview."],
              ].map(([title, description]) => <div key={title} className="py-4"><dt className="font-semibold">{title}</dt><dd className="text-muted-foreground">{description}</dd></div>)}
            </dl>
          </section>
        </article>
      </div>
    </LayoutBody>
  )
}

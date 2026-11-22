"use client"

import type { Definition, ManagedTemplate, MappingOption } from "@edispark/integration-client"
import { useCallback, useEffect, useState } from "react"

import { AdminNotice, AdminPage, AdminPageHeader } from "@/components/admin/admin-page"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { adminApi } from "@/lib/admin-api"

const endpoint = "/api/platform/managed-partners"
const initial: Definition = {
  name: "", verification: "UNVERIFIED", inboundFileFailurePolicy: "REJECT_FILE",
  transport: { mdnMode: "SYNCHRONOUS", signingRequired: true, encryptionRequired: true, compressionEnabled: false },
  capabilities: [],
}
const selectClass = "h-10 rounded-md border bg-background px-3 text-sm"

export function verificationLabel(status: Definition["verification"]) {
  return { UNVERIFIED: "Unverified", SIMULATOR_TESTED: "Simulator-tested", LOCAL_DEMO: "Local demo" }[status]
}

export function ManagedPartnersPage() {
  const [rows, setRows] = useState<ManagedTemplate[]>()
  const [mappings, setMappings] = useState<MappingOption[]>([])
  const [editing, setEditing] = useState<ManagedTemplate>()
  const [definition, setDefinition] = useState(initial)
  const [selectedMapping, setSelectedMapping] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  const refresh = useCallback(async () => {
    const [catalogue, options] = await Promise.all([
      adminApi<ManagedTemplate[]>(endpoint), adminApi<MappingOption[]>(endpoint + "/mappings"),
    ])
    setRows(catalogue)
    setMappings(options)
  }, [])

  useEffect(() => { void refresh().catch((cause: Error) => setError(cause.message)) }, [refresh])

  function reset(row?: ManagedTemplate) {
    setEditing(row)
    setDefinition(row?.definition ?? initial)
    setSelectedMapping("")
  }

  function addMapping() {
    const capability = mappings.find((item) => item.capability?.mappingRevisionId === selectedMapping)?.capability
    if (!capability) return
    setDefinition({ ...definition, capabilities: [...definition.capabilities, capability] })
    setSelectedMapping("")
  }

  async function write(url: string, method: string, body: unknown, message: string) {
    setBusy(true)
    setError(undefined)
    setNotice(undefined)
    try {
      await adminApi<ManagedTemplate>(url, { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) })
      reset()
      await refresh()
      setNotice(message)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The catalogue could not be updated.")
    } finally {
      setBusy(false)
    }
  }

  function save(event: React.FormEvent) {
    event.preventDefault()
    const { id, revision, version } = editing ?? {}
    if (id) {
      void write(endpoint + "/" + id, "PUT", { revision, version, definition }, "Draft saved.")
    } else {
      void write(endpoint, "POST", definition, "Draft created.")
    }
  }

  const groups = Map.groupBy(rows ?? [], (row) => row.id)
  return <AdminPage className="max-w-5xl">
    <AdminPageHeader title="Managed partners" />
    {error && <AdminNotice>{error}</AdminNotice>}
    {notice && <p role="status" className="mb-4 text-sm">{notice}</p>}
    <section aria-label="Partner catalogue" className="mb-8 divide-y border-y">
      {!rows && <p className="py-4 text-sm text-muted-foreground">Loading catalogue…</p>}
      {rows?.length === 0 && <p className="py-4 text-sm text-muted-foreground">No managed partners yet.</p>}
      {[...groups.values()].map((revisions) => {
        const latest = revisions[0]
        const published = revisions.find((row) => row.status !== "DRAFT")
        return <div className="flex flex-wrap items-center justify-between gap-3 py-4" key={latest.id}>
          <div>
            <h2 className="font-medium">{latest.definition?.name}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{latest.definition?.capabilities.length} document routes · revision {latest.revision}</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {latest.definition && <Badge variant="outline">{verificationLabel(latest.definition.verification)}</Badge>}
            <Badge variant="secondary">{latest.status?.toLowerCase()}</Badge>
            <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => reset(latest)}>
              {latest.status === "DRAFT" ? "Edit draft" : "New revision"}
            </Button>
            {latest.status === "DRAFT" && <Button size="sm" disabled={busy} onClick={() => void write(
              endpoint + "/" + latest.id + "/publish", "POST", { revision: latest.revision, version: latest.version }, "Template published.",
            )}>Publish</Button>}
            {published?.status === "PUBLISHED" && <Button size="sm" variant="ghost" disabled={busy} onClick={() => void write(
              endpoint + "/" + published.id + "/retire", "POST", { revision: published.revision, version: published.version }, "Template retired.",
            )}>Retire</Button>}
          </div>
        </div>
      })}
    </section>
    <form onSubmit={save} className="space-y-5">
      <h2 className="text-lg font-semibold">{editing ? "Edit managed template" : "Create managed template"}</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="grid gap-1.5 text-sm font-medium">Partner name
          <Input required maxLength={120} value={definition.name} onChange={(event) => setDefinition({ ...definition, name: event.target.value })} />
        </label>
        <label className="grid gap-1.5 text-sm font-medium">Workflow verification
          <select className={selectClass} value={definition.verification} onChange={(event) => setDefinition({ ...definition, verification: event.target.value as Definition["verification"] })}>
            <option value="UNVERIFIED">Unverified</option><option value="SIMULATOR_TESTED">Simulator-tested</option><option value="LOCAL_DEMO">Local demo</option>
          </select>
        </label>
        <label className="grid gap-1.5 text-sm font-medium">AS2 receipt mode
          <select className={selectClass} value={definition.transport.mdnMode} onChange={(event) => setDefinition({
            ...definition, transport: { ...definition.transport, mdnMode: event.target.value as Definition["transport"]["mdnMode"] },
          })}><option value="SYNCHRONOUS">Synchronous</option><option value="ASYNCHRONOUS">Asynchronous</option></select>
        </label>
        <label className="grid gap-1.5 text-sm font-medium">Inbound files with errors
          <select className={selectClass} value={definition.inboundFileFailurePolicy} onChange={(event) => setDefinition({
            ...definition, inboundFileFailurePolicy: event.target.value as Definition["inboundFileFailurePolicy"],
          })}><option value="REJECT_FILE">Reject file</option><option value="KEEP_VALID_MESSAGES">Keep valid messages</option></select>
        </label>
      </div>
      <fieldset className="space-y-2 border-y py-3">
        <legend className="text-sm font-medium">AS2 policy</legend>
        <div className="flex flex-wrap gap-4 text-sm">
          {([
            ["signingRequired", "Require signing"], ["encryptionRequired", "Require encryption"], ["compressionEnabled", "Compress payloads"],
          ] as const).map(([field, label]) => <label className="flex items-center gap-2" key={field}>
            <input type="checkbox" checked={!!definition.transport[field]} onChange={(event) => setDefinition({
              ...definition, transport: { ...definition.transport, [field]: event.target.checked },
            })} />{label}
          </label>)}
        </div>
      </fieldset>
      <fieldset className="space-y-3">
        <legend className="mb-2 text-sm font-medium">Document routes</legend>
        {definition.capabilities.map((capability) =>
          <div className="flex items-center justify-between gap-3 border-b py-2 text-sm" key={capability.mappingRevisionId}>
            <span>{mappings.find((item) => item.capability?.mappingRevisionId === capability.mappingRevisionId)?.name ?? capability.messageType}</span>
            <Button type="button" size="sm" variant="ghost" onClick={() => setDefinition({
              ...definition, capabilities: definition.capabilities.filter((item) => item !== capability),
            })}>Remove</Button>
          </div>)}
        <div className="flex flex-wrap gap-2">
          <select aria-label="Published mapping" className={selectClass + " min-w-0 max-w-full flex-1"} value={selectedMapping} onChange={(event) => setSelectedMapping(event.target.value)}>
            <option value="">Select a published mapping</option>
            {mappings.filter(({ capability }) => capability && !definition.capabilities.some((route) =>
              route.messageType === capability.messageType && route.direction === capability.direction
              && route.standard === capability.standard && route.release === capability.release,
            )).map(({ name, capability }) => <option key={capability?.mappingRevisionId} value={capability?.mappingRevisionId}>{name}</option>)}
          </select>
          <Button type="button" variant="outline" disabled={!selectedMapping || definition.capabilities.length >= 12} onClick={addMapping}>Add route</Button>
        </div>
      </fieldset>
      <div className="flex justify-end gap-2">
        {editing && <Button type="button" variant="ghost" onClick={() => reset()}>Cancel</Button>}
        <Button disabled={busy || !definition.capabilities.length}>Save draft</Button>
      </div>
    </form>
  </AdminPage>
}

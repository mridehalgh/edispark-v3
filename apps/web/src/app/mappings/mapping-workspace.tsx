"use client"

import type {
  MappingNode,
  MappingRevision,
  TestResult,
  TestMappingRetainedSourceResponse,
} from "@edispark/integration-client"
import { ReferenceTableBindings } from "@/components/trading-network/reference-table-bindings"
import {
  ArrowLeft,
  ArrowRight,
  Beaker,
  BookOpen,
  Braces,
  Check,
  ChevronRight,
  CircleAlert,
  Copy,
  FileJson2,
  Globe2,
  History,
  Plus,
  Save,
  Search,
  Send,
  Trash2,
  X,
} from "lucide-react"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { previewMapping, readPointer } from "@/lib/mapping-preview"
import { cn } from "@/lib/utils"
import { bundledMappingInput, mappingTestRequest } from "@/lib/mapping-test-input"
import styles from "./mapping-workspace.module.css"

const DEFAULT_INPUT = JSON.stringify(
  {
    _D: "urn:oasis:names:specification:ubl:schema:xsd:Order-2",
    _A: "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
    _B: "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
    Order: [
      {
        UBLVersionID: [{ _: "2.1" }],
        ID: [{ _: "PO-2026-0042" }],
        SalesOrderID: [{ _: "SO-2026-0042" }],
        IssueDate: [{ _: "2026-11-01" }],
        IssueTime: [{ _: "09:30:00Z" }],
        OrderTypeCode: [{ _: "220" }],
        DocumentCurrencyCode: [{ _: "GBP" }],
        CustomerReference: [{ _: "BUYER-REF-42" }],
        BuyerCustomerParty: [
          {
            Party: [
              {
                EndpointID: [{ _: "[partner_identifier]", schemeID: "GLN" }],
                PartyIdentification: [{ ID: [{ _: "BUYER-001" }] }],
                PartyName: [{ Name: [{ _: "[name]" }] }],
                PostalAddress: [
                  {
                    StreetName: [{ _: "[address]" }],
                    CityName: [{ _: "[address]" }],
                    PostalZone: [{ _: "[address]" }],
                    Country: [{ IdentificationCode: [{ _: "GB" }] }],
                  },
                ],
              },
            ],
          },
        ],
        SellerSupplierParty: [
          {
            Party: [
              {
                EndpointID: [{ _: "[tenant_identifier]", schemeID: "GLN" }],
                PartyIdentification: [{ ID: [{ _: "SUPPLIER-001" }] }],
                PartyName: [{ Name: [{ _: "[name]" }] }],
              },
            ],
          },
        ],
        Delivery: [
          {
            ID: [{ _: "DELIVERY-42" }],
            LatestDeliveryDate: [{ _: "2026-11-07" }],
            DeliveryLocation: [{ ID: [{ _: "STORE-042" }] }],
          },
        ],
        PaymentTerms: [
          { ID: [{ _: "NET30" }], PaymentDueDate: [{ _: "2026-12-01" }] },
        ],
        AnticipatedMonetaryTotal: [
          {
            LineExtensionAmount: [{ _: 100, currencyID: "GBP" }],
            TaxExclusiveAmount: [{ _: 95, currencyID: "GBP" }],
            TaxInclusiveAmount: [{ _: 114, currencyID: "GBP" }],
            PayableAmount: [{ _: 114, currencyID: "GBP" }],
          },
        ],
        OrderLine: [
          {
            LineItem: [
              {
                ID: [{ _: "1" }],
                Quantity: [{ _: 10, unitCode: "EA" }],
                LineExtensionAmount: [{ _: 100, currencyID: "GBP" }],
                Item: [
                  {
                    Description: [{ _: "Comprehensive sample retail item" }],
                    Name: [{ _: "Sample item" }],
                    BuyersItemIdentification: [{ ID: [{ _: "BUY-SKU-001" }] }],
                    SellersItemIdentification: [
                      { ID: [{ _: "SELL-SKU-001" }] },
                    ],
                    StandardItemIdentification: [
                      { ID: [{ _: "05012345000017", schemeID: "GTIN" }] },
                    ],
                  },
                ],
                Price: [{ PriceAmount: [{ _: 10, currencyID: "GBP" }] }],
              },
            ],
          },
        ],
      },
    ],
  },
  null,
  2,
)

async function readApi<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: "no-store" })
  const body = await response.json().catch(() => undefined)
  if (!response.ok)
    throw new Error(
      body?.error?.message ?? "The request could not be completed.",
    )
  return body as T
}

function decodeOutput(value?: string) {
  if (!value) return ""
  if (value.trimStart().startsWith("{") || value.trimStart().startsWith("["))
    return value
  try {
    return decodeURIComponent(
      Array.from(atob(value))
        .map(
          (character) =>
            `%${character.charCodeAt(0).toString(16).padStart(2, "0")}`,
        )
        .join(""),
    )
  } catch {
    return value
  }
}

function leafPointers(value: unknown, base = ""): string[] {
  if (value === null || typeof value !== "object") return base ? [base] : []
  return Object.entries(value).flatMap(([key, child]) =>
    leafPointers(
      child,
      `${base}/${key.replaceAll("~", "~0").replaceAll("/", "~1")}`,
    ),
  )
}

function fieldLabel(pointer: string) {
  const parts = pointer.split("/").filter((part) => part && !/^(?:[0-9]+|\*|_)$/.test(part))
  const leaf = parts.at(-1) ?? pointer
  const words = leaf === "id" || leaf === "ID" ? parts.slice(-2).join(" ") : leaf
  const spaced = words.replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/([A-Z])([A-Z][a-z])/g, "$1 $2")
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

const operationLabels: Record<NonNullable<MappingNode["operation"]>, string> = {
  COPY: "Copy",
  OPTIONAL_COPY: "Copy if present",
  OPTIONAL_CODE_LOOKUP: "Translate if present",
  OMIT: "Omit",
  CONSTANT: "Fixed value",
  CONCAT: "Join",
  COALESCE: "First available",
  UPPERCASE: "Uppercase",
  LOWERCASE: "Lowercase",
  CODE_LOOKUP: "Translate",
  LOOKUP_TABLE: "Table lookup",
}

function JsonPane({ value, label }: { value: string; label: string }) {
  const json = /^[{\[]/.test(value.trimStart())
  return (
    <div
      className={cn("min-w-0 overflow-auto bg-muted/25 px-3 py-3 sm:px-4", styles.panelScroll)}
      role="region"
      tabIndex={0}
      aria-label={label}
    >
      <pre className={cn("font-mono text-xs leading-6 text-foreground", json && styles.code)}>{json
        ? value.split("\n").map((line, index) => {
            const key = line.match(/^\s*"[^"]+"(?=:)/)?.[0]
            return <span className={styles.line} key={index}>{key
              ? <><span className="font-medium text-primary">{key}</span>{line.slice(key.length)}</>
              : line}{"\n"}</span>
          })
        : value}</pre>
    </div>
  )
}

function formatOutput(value?: string) {
  const decoded = decodeOutput(value)
  try { return JSON.stringify(JSON.parse(decoded), null, 2) }
  catch { return decoded }
}

function availableInput(mapping: MappingRevision) {
  return bundledMappingInput(mapping) ?? (
    mapping.compatibility?.documentType === "ORDER" && mapping.compatibility.direction === "OUTBOUND"
      ? DEFAULT_INPUT : undefined
  )
}

function CodeMapEditor({
  node,
  editable,
  onChange,
}: {
  node: MappingNode
  editable: boolean
  onChange: (codeMap: Record<string, string>) => void
}) {
  const syntaxLabel = (syntax?: string) =>
    syntax?.replaceAll("_", " ").replace("D96A", "D.96A")
  const listLabel = node.codeList?.replaceAll("_", " ").toLocaleLowerCase()

  return (
    <label className="grid gap-1 text-xs font-medium text-muted-foreground sm:col-span-2">
      <span className="flex flex-wrap items-baseline justify-between gap-2">
        <span>Code translations</span>
        {node.codeList && node.sourceSyntax && node.targetSyntax && (
          <span className="font-normal text-muted-foreground/80">
            {listLabel} · {syntaxLabel(node.sourceSyntax)} →{" "}
            {syntaxLabel(node.targetSyntax)}
          </span>
        )}
      </span>
      <textarea
        key={JSON.stringify(node.codeMap)}
        defaultValue={JSON.stringify(node.codeMap ?? {}, null, 2)}
        disabled={!editable}
        rows={5}
        onBlur={(event) => {
          try {
            const parsed = JSON.parse(event.currentTarget.value) as unknown
            if (
              !parsed ||
              Array.isArray(parsed) ||
              typeof parsed !== "object" ||
              Object.values(parsed).some((value) => typeof value !== "string")
            )
              throw new Error("invalid")
            event.currentTarget.setCustomValidity("")
            onChange(parsed as Record<string, string>)
          } catch {
            event.currentTarget.setCustomValidity(
              "Enter a JSON object containing source and target code strings.",
            )
            event.currentTarget.reportValidity()
          }
        }}
        className="rounded-md border bg-background p-2 font-mono text-xs text-foreground disabled:opacity-70"
        aria-label="Code translations as JSON"
      />
    </label>
  )
}

export function MappingWorkspacePage() {
  const { mappingId } = useParams()
  const navigate = useNavigate()
  const [sourceParams] = useSearchParams()
  const sourceQuery = sourceParams.toString()
  const [mapping, setMapping] = useState<MappingRevision>()
  const [nodes, setNodes] = useState<MappingNode[]>([])
  const [input, setInput] = useState("")
  const [activeNodeId, setActiveNodeId] = useState<string>()
  const [activePane, setActivePane] = useState<"source" | "mapping" | "output">(
    "mapping",
  )
  const [search, setSearch] = useState("")
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [testOpen, setTestOpen] = useState(false)
  const [sampleOpen, setSampleOpen] = useState(false)
  const [testResult, setTestResult] = useState<TestResult>()
  const [testError, setTestError] = useState<string>()
  const [testing, setTesting] = useState(false)
  const testRequest = useRef<AbortController | null>(null)
  const [fixtureTables, setFixtureTables] = useState<Record<string, string>>({})
  const [effectiveAt, setEffectiveAt] = useState("")
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  const clearTest = useCallback(() => {
    testRequest.current?.abort()
    testRequest.current = null
    setTesting(false)
    setTestResult(undefined)
    setTestError(undefined)
  }, [])

  const load = useCallback(async (signal: AbortSignal) => {
    if (!mappingId) return
    clearTest()
    setError(undefined)
    setNotice(undefined)
    setInput("")
    setDirty(false)
    setFixtureTables({})
    setSearch("")
    try {
      const result = await readApi<MappingRevision>(
        `/api/integration/mappings/${mappingId}`,
        { signal },
      )
      setMapping(result)
      setNodes(result.nodes ?? [])
      setActiveNodeId(result.nodes?.[0]?.id)
      setInput(availableInput(result) ?? "")
      const source = new URLSearchParams(sourceQuery)
      if (source.has("sourceSet")) {
        const retained = await readApi<TestMappingRetainedSourceResponse>(
          `/api/integration/mappings/${mappingId}/test-retained-source`,
          { signal, method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({
            documentSetId: source.get("sourceSet"), documentId: source.get("sourceDocument"),
            representationId: source.get("sourceRepresentation"), contentHash: source.get("sourceHash"),
          }) },
        )
        if (retained.input) setInput(retained.input)
        if (retained.test) setTestResult({ ...retained.test, output: formatOutput(retained.test.output) })
        setNotice("Loaded and tested the exact retained document. Review the findings, create a draft revision, and test your changes before publishing.")
        setTestOpen(true)
      }
    } catch (requestError) {
      if (signal.aborted) return
      setError(
        requestError instanceof Error
          ? requestError.message
          : "The document rule could not be loaded.",
      )
    }
  }, [clearTest, mappingId, sourceQuery])
  useEffect(() => {
    const controller = new AbortController()
    void load(controller.signal)
    return () => {
      controller.abort()
      testRequest.current?.abort()
    }
  }, [load])

  const parsedInput = useMemo(() => {
    try {
      return JSON.parse(input) as unknown
    } catch {
      return undefined
    }
  }, [input])
  const lookups = nodes.flatMap((node) => node.tableLookup ? [node.tableLookup] : [])
  const hasLookups = nodes.some((node) => node.operation === "LOOKUP_TABLE")
  const lookupAliases = [...new Set(lookups.flatMap((lookup) => lookup.alias ? [lookup.alias] : []))]
  const referenceTableRevisions = Object.fromEntries(lookupAliases.flatMap((alias) =>
    Object.hasOwn(fixtureTables, alias) ? [[alias, fixtureTables[alias]]] : [],
  ))
  const fixtureTablesReady = lookupAliases.every((alias) => Object.hasOwn(fixtureTables, alias))
  const preview = useMemo(() => {
    if (parsedInput === undefined || hasLookups) return undefined
    try { return previewMapping(parsedInput, nodes) }
    catch { return undefined }
  }, [hasLookups, nodes, parsedInput])
  const previewText = useMemo(() => {
    if (testResult?.output) return testResult.output
    if (!input.trim()) return "Add sample input in Test to see the mapping output."
    if (parsedInput === undefined) return "Enter valid JSON to preview the mapping."
    if (hasLookups) return "Select table revisions and run a test to see the output."
    return preview === undefined ? "Run a test to check this input." : JSON.stringify(preview, null, 2)
  }, [hasLookups, input, parsedInput, preview, testResult?.output])
  const sourcePointers = useMemo(
    () =>
      (parsedInput === undefined ? [] : leafPointers(parsedInput)).filter(
        (pointer) => pointer.toLowerCase().includes(search.toLowerCase()),
      ),
    [parsedInput, search],
  )
  const editable = mapping?.status === "DRAFT"
  const example = mapping ? availableInput(mapping) : undefined
  const selectedNode = nodes.find((node) => node.id === activeNodeId)
  const testBlocked = dirty ? "Save the draft before testing."
    : !input.trim() ? "Add JSON input to run a test."
    : parsedInput === undefined ? "Correct the JSON input before testing."
    : !fixtureTablesReady ? "Select a revision for each reference table." : undefined

  function changeInput(value: string) {
    setInput(value)
    clearTest()
  }

  function updateNode(id: string | undefined, patch: Partial<MappingNode>) {
    if (!id || !editable) return
    setNodes((current) =>
      current.map((node) => (node.id === id ? { ...node, ...patch } : node)),
    )
    setDirty(true)
    clearTest()
  }
  function addNode() {
    const node: MappingNode = {
      id: crypto.randomUUID(),
      operation: "COPY",
      sourcePointers: ["/document/id"],
      targetPointer: "/document/id",
    }
    setNodes((current) => [...current, node])
    setActiveNodeId(node.id)
    setDirty(true)
    clearTest()
  }
  function removeNode(id?: string) {
    setNodes((current) => current.filter((node) => node.id !== id))
    if (id === activeNodeId) setActiveNodeId(undefined)
    setDirty(true)
    clearTest()
  }

  async function save() {
    if (!mappingId) return
    try {
      setBusy(true)
      setError(undefined)
      const saved = await readApi<MappingRevision>(
        `/api/integration/mappings/${mappingId}/draft`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ nodes }),
        },
      )
      setMapping(saved)
      setDirty(false)
      setNotice("Draft saved.")
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "The draft could not be saved.",
      )
    } finally {
      setBusy(false)
    }
  }

  async function test() {
    if (!mappingId || !mapping || testBlocked || testing) return
    clearTest()
    const controller = new AbortController()
    testRequest.current = controller
    setTesting(true)
    try {
      const result = await readApi<TestResult>(
        `/api/integration/mappings/${mappingId}/test`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(mappingTestRequest(mapping, input, referenceTableRevisions)),
          signal: controller.signal,
        },
      )
      if (!controller.signal.aborted) setTestResult({ ...result, output: formatOutput(result.output) })
    } catch (requestError) {
      if (!controller.signal.aborted) {
        setTestError(requestError instanceof Error ? requestError.message : "The test could not run.")
      }
    } finally {
      if (testRequest.current === controller) {
        testRequest.current = null
        setTesting(false)
      }
    }
  }

  async function publish() {
    if (!mappingId || dirty || !testResult?.passed || !testResult.output) return
    try {
      setBusy(true)
      setError(undefined)
      const published = await readApi<MappingRevision>(
        `/api/integration/mappings/${mappingId}/publish`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            fixtures: [
              { name: "Workspace qualification", input, expected: testResult.output,
                referenceTableRevisions },
            ],
            effectiveAt: effectiveAt
              ? new Date(effectiveAt).toISOString()
              : undefined,
          }),
        },
      )
      setMapping(published)
      setNotice(
        effectiveAt
          ? "Rule publication scheduled."
          : "Rule published. This revision is now immutable.",
      )
      setTestOpen(false)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "The document rule could not be published.",
      )
    } finally {
      setBusy(false)
    }
  }

  async function revise() {
    if (!mappingId) return
    try {
      setBusy(true)
      const revision = await readApi<MappingRevision>(
        `/api/integration/mappings/${mappingId}/revisions`,
        { method: "POST" },
      )
      navigate(`/organisation/document-rules/${revision.id}${sourceQuery ? `?${sourceQuery}` : ""}`)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "A new revision could not be created.",
      )
    } finally {
      setBusy(false)
    }
  }

  if (!mapping && !error)
    return (
      <div className="grid min-h-[calc(100vh-4rem)] place-items-center">
        <p className="text-sm text-muted-foreground">Loading document rule…</p>
      </div>
    )

  return (
    <div className={cn("min-h-[calc(100dvh-4rem)] bg-background", styles.workspace)}>
      <header className="border-b bg-background px-4 pt-4 sm:px-6">
        <Link
          to="/organisation/document-rules"
          className="inline-flex min-h-8 items-center gap-1.5 rounded-sm text-sm font-medium text-primary hover:underline"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          Document rules
        </Link>
        <div className="flex flex-wrap items-end justify-between gap-3 pb-4 pt-1">
          <div className="min-w-0 space-y-2">
            <h1 className="break-words text-balance text-2xl font-semibold tracking-tight">
              {mapping?.name ?? "Document rule unavailable"}
            </h1>
            {mapping && <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
              <span>Revision {mapping.revision ?? 1}</span>
              {mapping.status && <Badge variant="secondary" className={cn(
                "capitalize", mapping.status === "PUBLISHED" && "bg-success/25 text-success-foreground",
              )}>{mapping.status === "PUBLISHED" && <Check className="mr-1 h-3 w-3" aria-hidden="true" />}{mapping.status.toLowerCase()}</Badge>}
              {mapping.scope === "GLOBAL" && <span className="inline-flex items-center gap-1.5">
                <Globe2 className="h-3.5 w-3.5" aria-hidden="true" />{mapping.vendor ?? "EdiSpark"}
              </span>}
              {editable && <span role="status">{dirty ? "Unsaved changes" : "Draft saved"}</span>}
            </div>}
          </div>
          {mapping && (editable ? (
            <Button size="sm" onClick={() => void save()} disabled={busy || testing || !dirty || !nodes.length}
              className={cn("gap-2", styles.tool)}>
              <Save className="h-4 w-4" aria-hidden="true" />Save draft
            </Button>
          ) : (
            <Button size="sm" onClick={() => void revise()} disabled={busy || testing} className={cn("gap-2", styles.tool)}>
              <History className="h-4 w-4" aria-hidden="true" />
              {mapping.scope === "GLOBAL" ? "Use as starting point" : "Create new revision"}
            </Button>
          ))}
        </div>
        {mapping && <nav className="-mb-px flex gap-5" aria-label="Document rule views">
          <button type="button" aria-pressed={!testOpen && !sampleOpen} onClick={() => { setTestOpen(false); setSampleOpen(false) }}
            className={cn("min-h-11 border-b-2 px-1 text-sm font-medium hover:text-primary", !testOpen && !sampleOpen ? "border-primary text-primary" : "border-transparent text-muted-foreground")}>
            Mapping
          </button>
          <button type="button" aria-pressed={testOpen} onClick={() => { setTestOpen(true); setSampleOpen(false) }}
            className={cn("inline-flex min-h-11 items-center gap-1.5 border-b-2 px-1 text-sm font-medium hover:text-primary", testOpen ? "border-primary text-primary" : "border-transparent text-muted-foreground")}>
            <Beaker className="h-4 w-4" aria-hidden="true" />Test
          </button>
          {mapping.sample && <button type="button" aria-pressed={sampleOpen} onClick={() => { setSampleOpen(true); setTestOpen(false) }}
            className={cn("inline-flex min-h-11 items-center gap-1.5 border-b-2 px-1 text-sm font-medium hover:text-primary", sampleOpen ? "border-primary text-primary" : "border-transparent text-muted-foreground")}>
            <BookOpen className="h-4 w-4" aria-hidden="true" />Samples
          </button>}
        </nav>}
      </header>

      {editable && !testOpen && !sampleOpen && (
        <div className="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3 sm:px-6">
          <p className="max-w-xl text-xs leading-5 text-muted-foreground">
            Map repeated lines with * in both paths. Unmapped populated fields
            block publication; use a reviewed omission only when the partner
            does not need the field.
          </p>
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              const repeat = (path: string) =>
                path.replace(
                  /\/(OrderLine|InvoiceLine|DespatchLine|ReceiptLine|RemittanceAdviceLine|lines)\/\d+\//g,
                  "/$1/*/",
                )
              const repeated = nodes.map((node) => ({
                ...node,
                sourcePointers: node.sourcePointers?.map(repeat),
                targetPointer: repeat(node.targetPointer ?? ""),
              }))
              const unique = new Map(
                repeated.map((node) => [
                  JSON.stringify([
                    node.operation,
                    node.sourcePointers,
                    node.targetPointer,
                    node.constant,
                    node.codeMap,
                  ]),
                  node,
                ]),
              )
              setNodes([...unique.values()])
              setDirty(true)
              clearTest()
            }}
          >
            Apply rules to every line
          </Button>
        </div>
      )}

      {(error || notice) && (
        <div
          className={cn(
            "flex items-center gap-2 border-b px-5 py-2 text-sm",
            error
              ? "bg-destructive/10 text-destructive"
              : "bg-success/20 text-success-foreground",
          )}
          role={error ? "alert" : "status"}
        >
          {error ? (
            <CircleAlert className="h-4 w-4" />
          ) : (
            <Check className="h-4 w-4" />
          )}
          {error ?? notice}
          <button
            className="ml-auto"
            onClick={() => {
              setError(undefined)
              setNotice(undefined)
            }}
            aria-label="Dismiss message"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {mapping && !testOpen && !sampleOpen && <section aria-label="Mapping workspace" className="px-4 pb-6 pt-4 sm:px-6">
        <nav className="mb-3 flex gap-4 border-b xl:hidden" aria-label="Mapping panels">
          {([["source", "Source"], ["mapping", "Field rules"], ["output", "Output"]] as const).map(([pane, label]) => (
            <button key={pane} type="button" aria-pressed={activePane === pane} onClick={() => setActivePane(pane)}
              className={cn("min-h-11 border-b-2 px-1 text-sm font-medium", activePane === pane ? "border-primary text-primary" : "border-transparent text-muted-foreground")}>
              {label}
            </button>
          ))}
        </nav>
        <div className={cn("grid min-w-0 rounded-lg border bg-background xl:divide-x", styles.workbench)}>
        <aside
          className={cn(
            "min-w-0 bg-muted/15",
            activePane !== "source" && "hidden xl:block",
          )}
          aria-labelledby="source-fields-title"
        >
          <div className="border-b p-3 sm:p-4">
            <div className="flex items-center justify-between gap-2"><div className="flex items-center gap-2">
              <FileJson2 className="h-4 w-4 text-primary" />
              <h2 id="source-fields-title" className="text-sm font-semibold">
                Source fields
              </h2>
            </div><span className="text-xs tabular-nums text-muted-foreground">{sourcePointers.length}</span></div>
            <label className="relative mt-3 block">
              <span className="sr-only">Search source fields</span>
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Find a source field"
                className="h-9 pl-8 text-sm"
              />
            </label>
          </div>
          <div className={cn("divide-y overflow-auto", styles.panelScroll)}>
            {sourcePointers.map((pointer) => {
              const selected = selectedNode?.sourcePointers?.includes(pointer)
              const content = <>
                <span className="block break-all font-mono text-xs font-medium">{pointer}</span>
                <span className="mt-0.5 block truncate text-xs text-muted-foreground">{JSON.stringify(readPointer(parsedInput, pointer))}</span>
              </>
              const className = cn("block w-full min-w-0 px-3 py-2.5 text-left sm:px-4", selected && "bg-accent text-primary")
              return editable && activeNodeId ? (
                <button key={pointer} type="button" aria-label={`Use source field ${pointer}`} aria-pressed={selected ?? false}
                  className={cn(className, "min-h-11 hover:bg-accent focus-visible:ring-inset")}
                  onClick={() => updateNode(activeNodeId, { sourcePointers: [pointer] })}>{content}</button>
              ) : <div key={pointer} className={className}>{content}</div>
            })}
            {!sourcePointers.length && <p className="px-4 py-6 text-sm text-muted-foreground">
              {search ? "No fields match your search." : "Add valid JSON in Test to see source fields."}
            </p>}
          </div>
        </aside>

        <section
          className={cn(
            "min-w-0",
            activePane !== "mapping" && "hidden xl:block",
          )}
          aria-labelledby="rules-title"
        >
          <div className="flex min-h-14 items-center justify-between gap-2 border-b px-3 py-2 sm:px-4">
            <div className="flex items-center gap-2">
              <h2 id="rules-title" className="text-sm font-semibold">Field rules</h2>
              <span className="text-xs tabular-nums text-muted-foreground">{nodes.length}</span>
            </div>
            {editable && (
              <Button
                size="sm"
                variant="outline"
                onClick={addNode}
                className="gap-2"
              >
                <Plus className="h-4 w-4" />
                Add field
              </Button>
            )}
          </div>
          <div aria-hidden="true" className="grid grid-cols-[1fr_6.25rem_1fr] gap-2 border-b bg-muted/45 px-3 py-2 text-xs font-medium text-muted-foreground sm:px-4">
            <span>Source</span><span className="text-center">Operation</span><span>Target</span>
          </div>
          <div className={cn("divide-y overflow-auto", styles.panelScroll)}>
            {nodes.map((node, index) => {
              const selected = activeNodeId === node.id
              const sourcePath = node.sourcePointers?.join(", ") ?? ""
              const targetPath = node.targetPointer ?? ""
              return <article key={node.id ?? index} className={cn(selected && "bg-accent/50")}>
                <button type="button" onClick={() => setActiveNodeId(node.id)} aria-pressed={selected}
                  className="grid min-h-14 w-full min-w-0 grid-cols-[1fr_6.25rem_1fr] items-center gap-2 px-3 py-2 text-left hover:bg-accent/60 focus-visible:ring-inset sm:px-4">
                  {node.operation === "CONSTANT"
                    ? <span className="truncate font-mono text-xs" title={node.constant}>{node.constant || "Set a value"}</span>
                    : <span className="min-w-0" title={sourcePath}>
                      <span className="block truncate text-sm font-medium">{sourcePath ? fieldLabel(node.sourcePointers?.[0] ?? "") : "Select a field"}</span>
                      <span className="block truncate font-mono text-xs text-muted-foreground">{sourcePath}</span>
                    </span>}
                  <span className="flex min-w-0 items-center justify-center gap-1 rounded bg-background/80 px-1 py-1 text-center text-xs leading-tight text-muted-foreground">
                    {node.operation ? operationLabels[node.operation] : "Rule"}
                    <ArrowRight className="h-3 w-3 shrink-0" aria-hidden="true" />
                  </span>
                  <span className="min-w-0" title={targetPath}>
                    <span className="block truncate text-sm font-medium">{targetPath ? fieldLabel(targetPath) : "Set a target"}</span>
                    <span className="block truncate font-mono text-xs text-muted-foreground">{targetPath}</span>
                  </span>
                </button>
                {selected && !editable && node.operation === "OMIT" && node.constant && <p className="px-4 pb-3 text-sm text-muted-foreground">{node.constant}</p>}
                {selected && !editable && (node.operation === "CODE_LOOKUP" || node.operation === "OPTIONAL_CODE_LOOKUP") &&
                  <div className="px-4 pb-3"><CodeMapEditor node={node} editable={false} onChange={() => {}} /></div>}
                {selected && editable && <div className="border-t px-3 py-3 sm:px-4">
                  <div className="mb-2 flex items-center justify-between">
                    <h3 className="text-sm font-medium">Edit field rule</h3>
                    <Button variant="ghost" size="icon" onClick={() => removeNode(node.id)} aria-label={`Remove rule ${index + 1}`}>
                      <Trash2 className="h-4 w-4" aria-hidden="true" />
                    </Button>
                  </div>
                <div className="grid gap-2 sm:grid-cols-[8.5rem_1fr]">
                  <label className="grid gap-1 text-xs font-medium text-muted-foreground">
                    Operation
                    <select
                      disabled={!editable}
                      value={node.operation}
                      onChange={(event) =>
                        updateNode(node.id, {
                          operation: event.target
                            .value as MappingNode["operation"],
                          tableLookup: event.target.value === "LOOKUP_TABLE"
                            ? node.tableLookup ?? { alias: "", resultColumn: "" } : undefined,
                          ...(event.target.value === "CODE_LOOKUP" &&
                          !node.codeMap
                            ? { codeMap: {} }
                            : {}),
                        })
                      }
                      className="h-9 rounded-md border bg-background px-2 text-sm text-foreground"
                    >
                      <option value="COPY">Copy value</option>
                      <option value="OPTIONAL_COPY">Copy when present</option>
                      <option value="OPTIONAL_CODE_LOOKUP">
                        Translate when present
                      </option>
                      <option value="OMIT">Reviewed omission</option>
                      <option value="CONSTANT">Fixed value</option>
                      <option value="CONCAT">Join values</option>
                      <option value="COALESCE">First available</option>
                      <option value="UPPERCASE">Uppercase</option>
                      <option value="LOWERCASE">Lowercase</option>
                      <option value="CODE_LOOKUP">Translate code</option>
                      <option value="LOOKUP_TABLE">Look up in table</option>
                    </select>
                  </label>
                  <label className="grid gap-1 text-xs font-medium text-muted-foreground">
                    {node.operation === "CONSTANT"
                      ? "Fixed value"
                      : "Source JSON pointer"}
                    <Input
                      disabled={!editable}
                      value={
                        node.operation === "CONSTANT"
                          ? (node.constant ?? "")
                          : (node.sourcePointers?.[0] ?? "")
                      }
                      onChange={(event) =>
                        updateNode(
                          node.id,
                          node.operation === "CONSTANT"
                            ? { constant: event.target.value }
                            : { sourcePointers: [event.target.value] },
                        )
                      }
                      className="h-9 font-mono text-xs text-foreground"
                    />
                  </label>
                  <label className="grid gap-1 text-xs font-medium text-muted-foreground sm:col-span-2">
                    Target JSON pointer (use * for each line)
                    <Input
                      disabled={!editable}
                      value={node.targetPointer ?? ""}
                      onChange={(event) =>
                        updateNode(node.id, {
                          targetPointer: event.target.value,
                        })
                      }
                      className="h-9 font-mono text-xs text-foreground"
                    />
                  </label>
                  {(node.operation === "CODE_LOOKUP" ||
                    node.operation === "OPTIONAL_CODE_LOOKUP") && (
                    <CodeMapEditor
                      node={node}
                      editable={editable}
                      onChange={(codeMap) => updateNode(node.id, { codeMap })}
                    />
                  )}
                  {node.operation === "LOOKUP_TABLE" && <>
                    <label className="grid gap-1 text-xs font-medium">Table alias
                      <Input disabled={!editable} value={node.tableLookup?.alias ?? ""} placeholder="sites"
                        onChange={(event) => updateNode(node.id, {
                          tableLookup: { ...node.tableLookup, alias: event.target.value },
                        })} />
                    </label>
                    <label className="grid gap-1 text-xs font-medium">Return column
                      <Input disabled={!editable} value={node.tableLookup?.resultColumn ?? ""} placeholder="erpSite"
                        onChange={(event) => updateNode(node.id, {
                          tableLookup: { ...node.tableLookup, resultColumn: event.target.value },
                        })} />
                    </label>
                  </>}
                  {node.operation === "OMIT" && (
                    <label className="grid gap-1 text-xs font-medium sm:col-span-2">
                      Business reason for omitting this field
                      <Input
                        required
                        disabled={!editable}
                        value={node.constant ?? ""}
                        onChange={(event) =>
                          updateNode(node.id, { constant: event.target.value })
                        }
                        placeholder="Explain why the partner does not need this field"
                      />
                    </label>
                  )}
                </div>
                </div>}
              </article>
            })}
            {nodes.length === 0 && (
              <div className="px-6 py-16 text-center">
                <Braces className="mx-auto h-6 w-6 text-muted-foreground" />
                <p className="mt-3 text-sm font-medium">No rules</p>
                {editable && (
                  <Button size="sm" className="mt-4" onClick={addNode}>
                    Add field
                  </Button>
                )}
              </div>
            )}
          </div>
        </section>

        <aside
          className={cn(
            "min-w-0",
            activePane !== "output" && "hidden xl:block",
          )}
          aria-labelledby="output-title"
        >
          <div className="flex min-h-14 items-center justify-between gap-2 border-b px-3 py-2 sm:px-4">
            <h2 id="output-title" className="text-sm font-semibold">{testResult?.output ? "Test output" : "Output preview"}</h2>
            <Braces className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
          </div>
          <JsonPane value={previewText} label="Generated document rule output" />
        </aside>
        </div>
      </section>}

      {sampleOpen && mapping?.sample && (
        <section
          className="m-4 min-w-0 overflow-hidden rounded-lg border bg-background sm:mx-6"
          aria-labelledby="samples-title"
        >
          <div className="flex items-center justify-between border-b bg-background px-4 py-3">
            <h2 id="samples-title" className="text-sm font-semibold">
              Samples
            </h2>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setSampleOpen(false)}
              aria-label="Close samples"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
          <div className="grid grid-cols-[minmax(0,1fr)] divide-y xl:grid-cols-[repeat(auto-fit,minmax(0,1fr))] xl:divide-x xl:divide-y-0">
            {[
              { stage: "raw-input", label: "Raw EDI input", value: mapping.sample.sourceRaw },
              { stage: "source", label: mapping.sample.sourceFormat ?? "Source JSON", value: mapping.sample.sourceDocument },
              { stage: "target", label: mapping.sample.targetFormat ?? "Target JSON", value: mapping.sample.targetDocument },
              { stage: "raw-output", label: "Raw EDI output", value: mapping.sample.targetRaw },
            ].filter(({ value }) => value).map(({ stage, label, value }) => (
              <div key={stage} className="grid min-w-0 content-start max-xl:min-h-40">
                <h3 className="border-b px-4 py-2 text-xs font-medium">{label}</h3>
                <JsonPane value={value ?? ""} label={label} />
              </div>
            ))}
          </div>
          <footer className="flex justify-end border-t px-4 py-3">
            <Button
              onClick={() => {
                changeInput(mapping.sample?.sourceDocument ?? "")
                setSampleOpen(false)
                setTestOpen(true)
              }}
              className="gap-2"
            >
              <Beaker className="h-4 w-4" />
              Test sample
            </Button>
          </footer>
        </section>
      )}

      {testOpen && mapping && (
        <section aria-label="Mapping test" aria-busy={testing} className="m-4 min-w-0 rounded-lg border bg-background sm:mx-6">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b px-3 py-3 sm:px-4">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <h2 className="text-base font-semibold">Test mapping</h2>
              {testResult && <span role="status" className={cn(
                "inline-flex items-center gap-1.5 text-sm font-medium",
                testResult.passed ? "text-success-foreground" : "text-destructive",
              )}>
                {testResult.passed ? <Check className="h-4 w-4" aria-hidden="true" /> : <CircleAlert className="h-4 w-4" aria-hidden="true" />}
                {testResult.passed ? "Passed" : "Needs attention"}
              </span>}
            </div>
            <div className="flex flex-wrap gap-2">
              {example && <Button variant="outline" size="sm" type="button" disabled={testing || busy || input === example}
                onClick={() => changeInput(example)} className={styles.tool}>
                Load sample
              </Button>}
              <Button size="sm" type="button" onClick={() => void test()} disabled={busy || testing || !!testBlocked}
                className={cn("gap-2", styles.tool)}>
                <Beaker className="h-4 w-4" aria-hidden="true" />{testing ? "Running test…" : "Run test"}
              </Button>
            </div>
          </div>
          {testBlocked && <p role="status" className="border-b bg-muted/25 px-4 py-2 text-sm text-muted-foreground">{testBlocked}</p>}
          {(testError || !!testResult?.failures?.length) && (
            <div role="alert" className="border-b bg-destructive/10 px-4 py-3 text-sm">
              {testError
                ? <p className="break-words text-destructive">{testError}</p>
                : <ul className="list-disc space-y-1 pl-4">{testResult?.failures?.map((failure, index) =>
                    <li key={index} className="break-words">{failure}</li>)}</ul>}
            </div>
          )}
          {lookups.length > 0 && <ReferenceTableBindings lookups={lookups} bindings={fixtureTables} busy={busy || testing}
            onSelect={(alias, revisionId) => {
              setFixtureTables((current) => ({ ...current, [alias]: revisionId }))
              clearTest()
            }} />}
          <div className="grid min-w-0 divide-y lg:grid-cols-2 lg:divide-x lg:divide-y-0">
            <label className="grid min-w-0 content-start">
              <span className="border-b px-4 py-3 text-sm font-medium">Input JSON</span>
              <textarea
                value={input}
                onChange={(event) => changeInput(event.target.value)}
                readOnly={testing}
                spellCheck={false}
                placeholder="Paste a JSON document"
                aria-invalid={!!input.trim() && parsedInput === undefined}
                className="h-[clamp(16rem,47vh,32rem)] w-full resize-y bg-background px-4 py-3 font-mono text-xs leading-6 text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
              />
            </label>
            <div className="grid min-w-0 content-start">
              <h3 className="border-b px-4 py-3 text-sm font-medium">{testResult?.output ? "Test output" : "Output preview"}</h3>
              <JsonPane
                value={previewText}
                label={testResult?.output ? "Document rule test output" : "Document rule output preview"}
              />
            </div>
          </div>
          {editable && <footer className="flex flex-wrap items-end justify-end gap-3 border-t px-4 py-3">
            <label className="grid gap-1 text-sm font-medium">
              Publish at (optional)
              <Input type="datetime-local" value={effectiveAt} onChange={(event) => setEffectiveAt(event.target.value)} />
            </label>
            <Button onClick={() => void publish()} disabled={busy || testing || dirty || !testResult?.passed}
              className={cn("gap-2", styles.tool)}>
              <Send className="h-4 w-4" aria-hidden="true" />
              {effectiveAt ? "Schedule publication" : "Publish revision"}
            </Button>
          </footer>}
        </section>
      )}
    </div>
  )
}

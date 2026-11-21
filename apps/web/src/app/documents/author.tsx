"use client"

import type {
  DocumentProfile,
  SsccReservation,
  TradingPartner,
} from "@edispark/integration-client"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import {
  AlertTriangle,
  ArrowLeft,
  Check,
  Copy,
  FileCheck2,
  Plus,
  RotateCcw,
  ShieldCheck,
  Trash2,
} from "lucide-react"
import { Link, useNavigate, useSearchParams } from "react-router-dom"

import { LayoutBody } from "@/components/layout/layout"
import { BusinessCodeSelect } from "@/components/documents/business-code-select"
import { SpecialistAuthoringSections } from "@/components/documents/specialist-authoring-sections"
import { AdvancedUblFields } from "@/components/documents/advanced-ubl-fields"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { getSmartCopyDraft } from "@/lib/documents-api"
import {
  authoringDefinitions,
  buildUblDocument,
  hasValidSscc,
  type AuthorableDocumentType,
  type AuthoringAdvancedField,
  type AuthoringLine,
  type AuthoringReferences,
  type AuthoringSpecialistValues,
} from "@/lib/ubl-authoring"
import type { SmartCopyDraft } from "@/lib/ubl-smart-copy"
import { authoringCatalog } from "@/lib/ubl-authoring-catalog"
import { CURRENCIES, QUANTITY_UNITS, ENDPOINT_SCHEMES, ORDER_RESPONSE_LINE_CODES } from "@/lib/business-code-lists"
import { cn } from "@/lib/utils"

const documentTypes = Object.keys(
  authoringDefinitions,
) as AuthorableDocumentType[]

function newLine(index: number): AuthoringLine {
  return {
    id: String(index + 1),
    itemName: "",
    itemIdentifier: "",
    quantity: "1",
    unitCode: "EA",
    unitPrice: "0.00",
  }
}

function Field({
  label,
  children,
  hint,
  className,
}: {
  label: string
  children: React.ReactNode
  hint?: string
  className?: string
}) {
  return (
    <label
      className={cn("grid min-w-0 gap-1.5 text-sm font-medium", className)}
    >
      {label}
      {children}
      {hint ? (
        <span className="break-words text-xs font-normal text-muted-foreground">
          {hint}
        </span>
      ) : null}
    </label>
  )
}

function blankReferences(): AuthoringReferences {
  return { order: "", despatch: "", invoice: "" }
}

function blankSpecialist(): AuthoringSpecialistValues {
  return {
    responseCode: "AP",
    responseDescription: "",
    requestedDeliveryDate: "",
    promisedDeliveryDate: "",
    actualDeliveryDate: "",
    actualDespatchDate: "",
    deliveryAddress: { line1: "", city: "", postcode: "", countryCode: "GB" },
    paymentMeansCode: "",
    paymentDueDate: "",
    paymentTerms: "",
    paymentAccountId: "",
    taxCategory: "S",
    taxPercent: "20",
    taxAmount: "",
    allowanceAmount: "",
    allowanceReason: "",
    shipmentId: "1",
    trackingId: "",
    carrierName: "",
    vehicleId: "",
    grossWeight: "",
    weightUnit: "KGM",
    handlingUnits: [],
  }
}

export function AuthorDocumentPage() {
  const navigate = useNavigate()
  const [search] = useSearchParams()
  const initialType = search.get("type") as AuthorableDocumentType | null
  const [documentType, setDocumentType] = useState<AuthorableDocumentType>(
    initialType && documentTypes.includes(initialType)
      ? initialType
      : "ORDER_RESPONSE",
  )
  const [references, setReferences] =
    useState<AuthoringReferences>(blankReferences)
  const [senderName, setSenderName] = useState("")
  const [senderEndpoint, setSenderEndpoint] = useState("")
  const [senderEndpointScheme, setSenderEndpointScheme] = useState("GLN")
  const [recipientName, setRecipientName] = useState("")
  const [recipientEndpoint, setRecipientEndpoint] = useState("")
  const [recipientEndpointScheme, setRecipientEndpointScheme] = useState("GLN")
  const [currency, setCurrency] = useState("GBP")
  const [lines, setLines] = useState<AuthoringLine[]>([newLine(0)])
  const [specialist, setSpecialist] =
    useState<AuthoringSpecialistValues>(blankSpecialist)
  const [advancedFields, setAdvancedFields] = useState<
    AuthoringAdvancedField[]
  >([])
  const [partners, setPartners] = useState<TradingPartner[]>([])
  const [partnerId, setPartnerId] = useState("")
  const [profiles, setProfiles] = useState<DocumentProfile[]>([])
  const [profileId, setProfileId] = useState("")
  const [smartCopy, setSmartCopy] = useState<SmartCopyDraft>()
  const [copyLoading, setCopyLoading] = useState(false)
  const [copyError, setCopyError] = useState<string>()
  const [verifiedCopySource, setVerifiedCopySource] = useState<string>()
  const [commercialReviewed, setCommercialReviewed] = useState(false)
  const [edited, setEdited] = useState<Set<string>>(new Set())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [generatingSscc, setGeneratingSscc] = useState(false)
  const authoringSessionId = useRef(crypto.randomUUID())
  const reservationSequence = useRef(0)
  const automaticAttempt = useRef("")
  const definition = authoringDefinitions[documentType]
  const sourceSetId = search.get("fromSet")
  const sourceDocumentId = search.get("fromDocument")
  const sourceRepresentationId = search.get("fromRepresentation")
  const sourceContentHash = search.get("fromHash")
  const pinnedCopy = sourceRepresentationId !== null || sourceContentHash !== null
  const copySourceKey = [sourceSetId, sourceDocumentId, sourceRepresentationId, sourceContentHash, documentType, search.get("mode")].join("/")
  const copySourcePending = pinnedCopy && verifiedCopySource !== copySourceKey
  const related = Boolean(sourceSetId && sourceDocumentId)
  const correction = search.get("mode") === "correct"
  const selectedProfile = profiles.find((profile) => profile.id === profileId)
  const ssccGenerationAvailable =
    documentType === "DESPATCH_ADVICE" &&
    (!selectedProfile || selectedProfile.ssccGenerationMode !== "MANUAL")

  useEffect(() => {
    const controller = new AbortController()
    void fetch("/api/integration/partners", {
      signal: controller.signal,
      cache: "no-store",
    })
      .then((response) =>
        response.ok ? (response.json() as Promise<TradingPartner[]>) : [],
      )
      .then((items) =>
        setPartners(items.filter((item) => item.status === "ACTIVE")),
      )
      .catch(() => undefined)
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!partnerId) {
      setProfiles([])
      setProfileId("")
      return
    }
    const controller = new AbortController()
    void fetch(`/api/integration/partners/${partnerId}/document-profiles`, {
      signal: controller.signal,
      cache: "no-store",
    })
      .then((response) =>
        response.ok ? (response.json() as Promise<DocumentProfile[]>) : [],
      )
      .then((items) => {
        setProfiles(
          items.filter(
            (profile) =>
              profile.status === "PUBLISHED" &&
              profile.direction === "OUTBOUND" &&
              profile.documentType === documentType,
          ),
        )
        setProfileId("")
      })
      .catch(() => undefined)
    return () => controller.abort()
  }, [documentType, partnerId])

  useEffect(() => {
    if (!selectedProfile) return
    const additions = (selectedProfile.fieldRules ?? []).flatMap((rule) => {
      if (
        !rule.defaultValue ||
        advancedFields.some((field) => field.fieldKey === rule.fieldKey)
      )
        return []
      const catalogField = authoringCatalog[documentType].find(
        (field) => field.fieldKey === rule.fieldKey,
      )
      return catalogField
        ? [
            {
              fieldKey: catalogField.fieldKey,
              ublKey: catalogField.ublKey,
              value: rule.defaultValue,
            },
          ]
        : []
    })
    if (additions.length)
      setAdvancedFields((current) => [...current, ...additions])
  }, [advancedFields, documentType, selectedProfile])

  function applyDraft(draft: SmartCopyDraft) {
    setPartnerId(draft.partnerId ?? "")
    setReferences(draft.values.references)
    setSenderName(draft.values.senderName)
    setSenderEndpoint(draft.values.senderEndpoint)
    setSenderEndpointScheme(draft.values.senderEndpointScheme ?? "GLN")
    setRecipientName(draft.values.recipientName)
    setRecipientEndpoint(draft.values.recipientEndpoint)
    setRecipientEndpointScheme(draft.values.recipientEndpointScheme ?? "GLN")
    setCurrency(draft.values.currency)
    setLines(draft.values.lines)
    if (draft.values.specialist) setSpecialist((current) => ({ ...current, ...draft.values.specialist }))
    if (draft.correction?.responseCode)
      setSpecialist((current) => ({
        ...current,
        responseCode: draft.correction!.responseCode!,
      }))
    setCommercialReviewed(false)
    setEdited(new Set())
  }

  useEffect(() => {
    if (!sourceSetId || !sourceDocumentId) return
    const controller = new AbortController()
    setCopyLoading(true)
    setCopyError(undefined)
    void getSmartCopyDraft(
      sourceSetId,
      sourceDocumentId,
      documentType,
      controller.signal,
      correction,
      pinnedCopy
        ? { representationId: sourceRepresentationId ?? "", contentHash: sourceContentHash ?? "" }
        : undefined,
    )
      .then((draft) => {
        if (controller.signal.aborted) return
        setVerifiedCopySource(copySourceKey)
        setSmartCopy(draft)
        applyDraft(draft)
      })
      .catch((caught: unknown) => {
        if (!(caught instanceof DOMException && caught.name === "AbortError"))
          setCopyError(
            caught instanceof Error
              ? caught.message
              : "The previous document could not be copied.",
          )
      })
      .finally(() => {
        if (!controller.signal.aborted) setCopyLoading(false)
      })
    return () => controller.abort()
  }, [documentType, sourceDocumentId, sourceSetId, correction, sourceRepresentationId, sourceContentHash, pinnedCopy, copySourceKey])

  function markEdited(path: string) {
    setEdited((current) => new Set(current).add(path))
  }

  function updateLine(
    index: number,
    field: keyof AuthoringLine,
    value: string,
  ) {
    setLines((current) =>
      current.map((line, lineIndex) =>
        lineIndex === index ? { ...line, [field]: value } : line,
      ),
    )
    markEdited(`lines.${index}`)
    setCommercialReviewed(false)
  }

  function startBlank() {
    setSmartCopy(undefined)
    setPartnerId("")
    setProfileId("")
    setReferences(blankReferences())
    setSenderName("")
    setSenderEndpoint("")
    setRecipientName("")
    setRecipientEndpoint("")
    setCurrency("GBP")
    setLines([newLine(0)])
    setSpecialist(blankSpecialist())
    setAdvancedFields([])
    setCommercialReviewed(false)
    setEdited(new Set())
  }

  const reserveSsccs = useCallback(
    async (count: number, purpose: string) => {
      reservationSequence.current += 1
      const response = await fetch("/api/integration/sscc/reservations", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          issuerId: selectedProfile?.ssccIssuerId,
          count,
          idempotencyKey: `${authoringSessionId.current}:${reservationSequence.current}`,
          context: purpose,
        }),
      })
      const body = (await response.json()) as
        | SsccReservation
        | { error?: { message?: string } }
      if (!response.ok)
        throw new Error(
          "error" in body
            ? body.error?.message
            : "SSCCs could not be generated.",
        )
      const reservation = body as SsccReservation
      if (!reservation.id || reservation.ssccs?.length !== count)
        throw new Error("The SSCC reservation was incomplete.")
      return reservation
    },
    [selectedProfile?.ssccIssuerId],
  )

  async function generateSscc(unitIndex: number) {
    setGeneratingSscc(true)
    setError(undefined)
    try {
      const reservation = await reserveSsccs(
        1,
        `ASN ${authoringSessionId.current} handling unit ${unitIndex + 1}`,
      )
      setSpecialist((current) => ({
        ...current,
        handlingUnits: current.handlingUnits.map((unit, index) =>
          index === unitIndex
            ? {
                ...unit,
                sscc: reservation.ssccs![0],
                ssccSource: "GENERATED",
                ssccReservationId: reservation.id,
              }
            : unit,
        ),
      }))
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "SSCC could not be generated.",
      )
    } finally {
      setGeneratingSscc(false)
    }
  }

  const generateMissingSsccs = useCallback(async () => {
    const missingIndexes = specialist.handlingUnits.flatMap((unit, index) =>
      unit.sscc ? [] : [index],
    )
    if (!missingIndexes.length) return
    setGeneratingSscc(true)
    setError(undefined)
    try {
      const reservation = await reserveSsccs(
        missingIndexes.length,
        `ASN ${authoringSessionId.current} handling units`,
      )
      const ssccByIndex = new Map(
        missingIndexes.map((index, valueIndex) => [
          index,
          reservation.ssccs![valueIndex],
        ]),
      )
      setSpecialist((current) => ({
        ...current,
        handlingUnits: current.handlingUnits.map((unit, index) =>
          ssccByIndex.has(index)
            ? {
                ...unit,
                sscc: ssccByIndex.get(index)!,
                ssccSource: "GENERATED",
                ssccReservationId: reservation.id,
              }
            : unit,
        ),
      }))
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "SSCCs could not be generated.",
      )
    } finally {
      setGeneratingSscc(false)
    }
  }, [reserveSsccs, specialist.handlingUnits])

  useEffect(() => {
    if (
      selectedProfile?.ssccGenerationMode !== "AUTOMATIC" ||
      documentType !== "DESPATCH_ADVICE"
    )
      return
    const signature = specialist.handlingUnits
      .map((unit) => `${unit.id}:${unit.sscc}`)
      .join("|")
    if (
      !specialist.handlingUnits.some((unit) => !unit.sscc) ||
      generatingSscc ||
      automaticAttempt.current === signature
    )
      return
    automaticAttempt.current = signature
    void generateMissingSsccs()
  }, [
    documentType,
    generateMissingSsccs,
    generatingSscc,
    selectedProfile?.ssccGenerationMode,
    specialist.handlingUnits,
  ])

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (copySourcePending) return
    if (smartCopy?.requiresCommercialReview && !commercialReviewed) {
      setError(
        "Review and confirm the copied quantities and amounts before saving.",
      )
      return
    }
    setBusy(true)
    setError(undefined)
    const form = new FormData(event.currentTarget)
    const content = buildUblDocument({
      documentType,
      id: String(form.get("id")),
      issueDate: String(form.get("issueDate")),
      references,
      senderName,
      senderEndpoint,
      senderEndpointScheme,
      recipientName,
      recipientEndpoint,
      recipientEndpointScheme,
      currency,
      note: String(form.get("note") ?? ""),
      lines,
      specialist,
      advancedFields,
    })
    const ssccReservationIds = [
      ...new Set(
        specialist.handlingUnits.flatMap((unit) =>
          unit.ssccReservationId ? [unit.ssccReservationId] : [],
        ),
      ),
    ]
    try {
      const response = await fetch("/api/documents", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          documentType,
          content,
          metadata: {
            name: `${definition.label} ${String(form.get("id"))}`,
            direction: "OUTBOUND",
            standard: "UBL",
            ublVersion: "2.1",
            ...(partnerId ? { partnerId } : {}),
            ...(profileId ? { documentProfileRevisionId: profileId } : {}),
            ...(ssccReservationIds.length
              ? { ssccReservationIds: ssccReservationIds.join(",") }
              : {}),
          },
          related: related
            ? {
                setId: sourceSetId,
                documentId: sourceDocumentId,
                relationType: correction ? "CORRECTS" : definition.relationType,
              }
            : undefined,
        }),
      })
      const body = (await response.json()) as {
        setId?: string
        document?: { id?: string }
        error?: { message?: string }
      }
      if (!response.ok)
        throw new Error(body.error?.message ?? "The draft could not be saved.")
      const documentId = body.document?.id
      if (!body.setId || !documentId)
        throw new Error("The draft was saved without a document identifier.")
      navigate(`/documents/${body.setId}/${documentId}`)
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "The draft could not be saved.",
      )
    } finally {
      setBusy(false)
    }
  }

  const copiedHint = (path: string) =>
    smartCopy?.provenance[path]
      ? `${edited.has(path) ? "Edited after copying" : "Copied"} from ${smartCopy.sources.find((source) => source.documentId === smartCopy.provenance[path])?.reference ?? "previous document"}.`
      : undefined

  const readinessWarnings = useMemo(() => {
    if (documentType !== "DESPATCH_ADVICE" && documentType !== "RECEIPT_ADVICE")
      return []
    const warnings: string[] = []
    const ssccs = specialist.handlingUnits
      .map((unit) => unit.sscc)
      .filter(Boolean)
    if (ssccs.some((sscc) => !hasValidSscc(sscc)))
      warnings.push(
        "One or more SSCCs do not have a valid 18-digit GS1 check digit.",
      )
    if (new Set(ssccs).size !== ssccs.length)
      warnings.push("Each handling unit must use a unique SSCC.")
    lines.forEach((line) => {
      const allocated = specialist.handlingUnits
        .flatMap((unit) => unit.allocations)
        .filter((item) => item.lineId === line.id)
        .reduce((sum, item) => sum + Number(item.quantity || 0), 0)
      if (allocated > Number(line.quantity || 0))
        warnings.push(
          `Line ${line.id} allocates more than its document quantity.`,
        )
      if (allocated > 0 && allocated < Number(line.quantity || 0))
        warnings.push(
          `Line ${line.id} is only partly allocated to handling units.`,
        )
    })
    return warnings
  }, [documentType, lines, specialist.handlingUnits])
  const partnerRequirements = useMemo(
    () =>
      (selectedProfile?.fieldRules ?? []).map((rule) => ({
        ...rule,
        populated:
          advancedFields.some(
            (field) => field.fieldKey === rule.fieldKey && field.value.trim(),
          ) ||
          (rule.fieldKey === "despatch.shipment.handlingUnits[].sscc" &&
            specialist.handlingUnits.length > 0 &&
            specialist.handlingUnits.every((unit) => unit.sscc)),
      })),
    [advancedFields, selectedProfile?.fieldRules, specialist.handlingUnits],
  )

  return (
    <LayoutBody className="mx-auto w-full max-w-[96rem] py-6 sm:py-7">
      <Link
        to={
          related && sourceSetId && sourceDocumentId
            ? `/documents/${sourceSetId}/${sourceDocumentId}`
            : "/documents"
        }
        className="inline-flex min-h-10 items-center gap-2 text-sm text-muted-foreground transition-colors hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        {related ? "Source document" : "Documents"}
      </Link>
      <header className="mt-3 border-b pb-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-semibold tracking-[-0.025em] text-balance">
                {correction ? "Correct" : "Create"}{" "}
                {definition.label.toLowerCase()}
              </h1>
              <Badge variant="secondary">Draft</Badge>
            </div>
            <p className="mt-1.5 max-w-2xl text-sm text-muted-foreground">
              Add the business details your partner needs. Saving creates a
              draft and does not send anything. Corrections preserve the
              original document and exchange history.
            </p>
          </div>
        </div>
      </header>
      <form
        onSubmit={save}
        className="grid gap-6 py-5 xl:grid-cols-[minmax(0,1fr)_20rem]"
      >
        <div className="min-w-0">
          {related ? (
            <section
              className="mb-5 rounded-md border bg-muted/30 px-4 py-3"
              aria-live="polite"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="flex items-center gap-2 text-sm font-medium">
                    <Copy className="h-4 w-4 text-primary" />
                    {copyLoading
                      ? "Copying useful details…"
                      : smartCopy
                        ? `Started from ${smartCopy.sources.length} related ${smartCopy.sources.length === 1 ? "document" : "documents"}`
                        : "Creating a related document"}
                  </p>
                  {smartCopy ? (
                    <p className="mt-1 text-xs text-muted-foreground">
                      {smartCopy.sources
                        .map(
                          (source) =>
                            `${authoringDefinitions[source.type].label} ${source.reference}`,
                        )
                        .join(" · ")}
                    </p>
                  ) : (
                    <p className="mt-1 text-xs text-muted-foreground">
                      This draft will remain linked to its source. Fields
                      outside this form are retained when creating a correction.
                    </p>
                  )}
                </div>
                {smartCopy ? (
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => applyDraft(smartCopy)}
                    >
                      <RotateCcw className="mr-2 h-3.5 w-3.5" />
                      Restore copied details
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      onClick={startBlank}
                    >
                      Start blank
                    </Button>
                  </div>
                ) : null}
              </div>
            </section>
          ) : null}
          {copyError ? (
            <p
              className="flex gap-2 rounded-md border border-amber-500/40 bg-amber-500/5 px-4 py-3 text-sm"
              role="alert"
            >
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-700" />
              <span>
                <strong className="block font-medium">
                  Smart copy was unavailable
                </strong>
                <span className="text-muted-foreground">
                  {copyError} {pinnedCopy ? "Open the current source to start a fresh draft." : "You can still complete the document manually."}
                </span>
              </span>
            </p>
          ) : null}
          {smartCopy?.warnings.map((warning) => (
            <p
              key={warning}
              className="mb-3 flex gap-2 rounded-md border border-amber-500/40 bg-amber-500/5 px-3 py-2.5 text-sm"
            >
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-700" />
              {warning}
            </p>
          ))}
          {error ? (
            <p
              className="rounded-md border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm text-destructive"
              role="alert"
            >
              {error}
            </p>
          ) : null}

          <section className="border-t py-6" aria-labelledby="document-heading">
            <h2 id="document-heading" className="text-base font-semibold">
              Document details
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Reference, date and partner settings for this document.
            </p>
            <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Field label="Document type">
                <select
                  value={documentType}
                  disabled={related}
                  onChange={(event) =>
                    setDocumentType(
                      event.target.value as AuthorableDocumentType,
                    )
                  }
                  className="h-10 rounded-md border bg-background px-3 text-sm disabled:opacity-70"
                >
                  {documentTypes.map((type) => (
                    <option key={type} value={type}>
                      {authoringDefinitions[type].label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field
                label="Document number"
                hint={
                  correction
                    ? "Retain or update the reference for this correction."
                    : "New references are never copied."
                }
              >
                <Input
                  key={smartCopy?.correction?.id}
                  name="id"
                  required
                  autoFocus
                  defaultValue={smartCopy?.correction?.id}
                  placeholder="e.g. OR-2026-0043"
                />
              </Field>
              <Field label="Issue date">
                <Input
                  name="issueDate"
                  type="date"
                  required
                  key={smartCopy?.correction?.issueDate}
                  defaultValue={
                    smartCopy?.correction?.issueDate ??
                    new Date().toISOString().slice(0, 10)
                  }
                />
              </Field>
              <Field label="Currency" hint={copiedHint("currency")}>
                <BusinessCodeSelect
                  value={currency}
                  options={CURRENCIES}
                  required
                  onChange={(value) => {
                    setCurrency(value)
                    markEdited("currency")
                  }}
                />
              </Field>
              {documentType !== "ORDER" &&
              documentType !== "REMITTANCE_ADVICE" ? (
                <Field
                  label="Order reference"
                  hint={copiedHint("references.order")}
                >
                  <Input
                    value={references.order ?? ""}
                    onChange={(event) => {
                      setReferences((current) => ({
                        ...current,
                        order: event.target.value,
                      }))
                      markEdited("references.order")
                    }}
                    required={
                      documentType === "ORDER_RESPONSE" ||
                      documentType === "DESPATCH_ADVICE"
                    }
                  />
                </Field>
              ) : null}
              {documentType === "RECEIPT_ADVICE" ||
              documentType === "INVOICE" ? (
                <Field
                  label="Despatch reference"
                  hint={copiedHint("references.despatch")}
                >
                  <Input
                    value={references.despatch ?? ""}
                    onChange={(event) => {
                      setReferences((current) => ({
                        ...current,
                        despatch: event.target.value,
                      }))
                      markEdited("references.despatch")
                    }}
                  />
                </Field>
              ) : null}
              <Field label="Note">
                <Input
                  key={smartCopy?.correction?.note}
                  name="note"
                  defaultValue={smartCopy?.correction?.note}
                  placeholder="Optional business note"
                />
              </Field>
              <Field
                label="Trading partner"
                hint="Applies the partner's published requirements."
              >
                <select
                  value={partnerId}
                  onChange={(event) => setPartnerId(event.target.value)}
                  className="h-10 rounded-md border bg-background px-3 text-sm"
                >
                  <option value="">Choose later</option>
                  {partners.map((partner) => (
                    <option key={partner.id} value={partner.id}>
                      {partner.displayName}
                    </option>
                  ))}
                </select>
              </Field>
              {partnerId ? (
                <Field label="Partner requirements">
                  <select
                    value={profileId}
                    onChange={(event) => setProfileId(event.target.value)}
                    className="h-10 rounded-md border bg-background px-3 text-sm"
                  >
                    <option value="">No extra requirements</option>
                    {profiles.map((profile) => (
                      <option key={profile.id} value={profile.id}>
                        {profile.name} · version {profile.revision}
                      </option>
                    ))}
                  </select>
                </Field>
              ) : null}
            </div>
          </section>

          <section className="border-t py-6" aria-labelledby="parties-heading">
            <h2 id="parties-heading" className="text-base font-semibold">
              From and to
            </h2>
            <div className="mt-4 grid gap-6 lg:grid-cols-2">
              <fieldset className="grid gap-4 border-t pt-4">
                <legend className="pr-3 text-sm font-semibold">
                  {definition.senderLabel}
                </legend>
                <Field
                  label="Legal or trading name"
                  hint={copiedHint(
                    documentType === "RECEIPT_ADVICE" ||
                      documentType === "REMITTANCE_ADVICE"
                      ? "buyer"
                      : "seller",
                  )}
                >
                  <Input
                    value={senderName}
                    onChange={(event) => {
                      setSenderName(event.target.value)
                      markEdited("sender")
                    }}
                    required
                    placeholder="[name]"
                  />
                </Field>
                <Field
                  label="Electronic address"
                  hint="Use the identifier and scheme agreed with this partner."
                >
                  <Input
                    value={senderEndpoint}
                    onChange={(event) => {
                      setSenderEndpoint(event.target.value)
                      markEdited("sender")
                    }}
                    required
                    placeholder="[partner_identifier]"
                  />
                </Field>
                <Field label="Identifier scheme" hint="GLNs need 13 digits and a valid check digit. Sample placeholders use a supplier-assigned identifier.">
                  <BusinessCodeSelect value={senderEndpointScheme} options={ENDPOINT_SCHEMES} required onChange={(value) => { setSenderEndpointScheme(value); markEdited("sender") }} />
                </Field>
              </fieldset>
              <fieldset className="grid gap-4 border-t pt-4">
                <legend className="pr-3 text-sm font-semibold">
                  {definition.recipientLabel}
                </legend>
                <Field
                  label="Legal or trading name"
                  hint={copiedHint(
                    documentType === "RECEIPT_ADVICE" ||
                      documentType === "REMITTANCE_ADVICE"
                      ? "seller"
                      : "buyer",
                  )}
                >
                  <Input
                    value={recipientName}
                    onChange={(event) => {
                      setRecipientName(event.target.value)
                      markEdited("recipient")
                    }}
                    required
                    placeholder="[name]"
                  />
                </Field>
                <Field
                  label="Electronic address"
                  hint="Use the identifier and scheme agreed with this partner."
                >
                  <Input
                    value={recipientEndpoint}
                    onChange={(event) => {
                      setRecipientEndpoint(event.target.value)
                      markEdited("recipient")
                    }}
                    required
                    placeholder="[tenant_identifier]"
                  />
                </Field>
                <Field label="Identifier scheme" hint="Keep the recipient's agreed identification scheme when correcting a document.">
                  <BusinessCodeSelect value={recipientEndpointScheme} options={ENDPOINT_SCHEMES} required onChange={(value) => { setRecipientEndpointScheme(value); markEdited("recipient") }} />
                </Field>
              </fieldset>
            </div>
          </section>

          <SpecialistAuthoringSections
            documentType={documentType}
            values={specialist}
            lines={lines}
            onChange={setSpecialist}
            onLineChange={updateLine}
            ssccGenerationAvailable={ssccGenerationAvailable}
            generatingSscc={generatingSscc}
            onGenerateSscc={(index) => void generateSscc(index)}
            onGenerateMissingSsccs={() => void generateMissingSsccs()}
          />

          <section className="border-t py-6" aria-labelledby="lines-heading">
            <div className="flex items-center justify-between gap-4">
              <div>
                <h2 id="lines-heading" className="text-base font-semibold">
                  {documentType === "REMITTANCE_ADVICE"
                    ? "Payment allocations"
                    : "Line items"}
                </h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  {documentType === "REMITTANCE_ADVICE"
                    ? "Allocate the payment to each referenced invoice."
                    : "Add one row for each product in this document."}
                </p>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() =>
                  setLines((current) => [...current, newLine(current.length)])
                }
              >
                <Plus className="mr-2 h-4 w-4" />
                Add line
              </Button>
            </div>
            <div className="mt-4 divide-y border-y">
              {lines.map((line, index) => {
                const provenance = copiedHint(`lines.${index}`)
                if (documentType === "REMITTANCE_ADVICE")
                  return (
                    <fieldset key={index} className="min-w-0 py-4">
                      <legend className="sr-only">
                        Payment allocation {index + 1}
                      </legend>
                      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                        <p className="text-sm font-medium">
                          Payment allocation {index + 1}
                        </p>
                        {provenance ? (
                          <p className="text-xs text-muted-foreground">
                            {provenance}
                          </p>
                        ) : null}
                      </div>
                      <div className="mt-2 grid min-w-0 gap-3 sm:grid-cols-2 lg:grid-cols-[5rem_minmax(12rem,1fr)_minmax(10rem,1fr)_2.5rem] lg:items-end">
                        <Field label="Line number">
                          <Input
                            className="min-w-0"
                            value={line.id}
                            onChange={(event) =>
                              updateLine(index, "id", event.target.value)
                            }
                            required
                          />
                        </Field>
                        <Field label="Invoice reference">
                          <Input
                            className="min-w-0"
                            value={line.invoiceReference ?? ""}
                            onChange={(event) =>
                              updateLine(
                                index,
                                "invoiceReference",
                                event.target.value,
                              )
                            }
                            required
                          />
                        </Field>
                        <Field label="Payment amount">
                          <Input
                            className="min-w-0"
                            type="number"
                            min="0"
                            step="0.01"
                            value={line.amount ?? ""}
                            onChange={(event) =>
                              updateLine(index, "amount", event.target.value)
                            }
                            required
                          />
                        </Field>
                        <Button
                          className="self-end"
                          type="button"
                          variant="ghost"
                          size="icon"
                          disabled={lines.length === 1}
                          onClick={() =>
                            setLines((current) =>
                              current.filter(
                                (_, lineIndex) => lineIndex !== index,
                              ),
                            )
                          }
                          aria-label={`Remove allocation ${index + 1}`}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </fieldset>
                  )

                return (
                  <fieldset key={index} className="min-w-0 py-4">
                    <legend className="sr-only">Line item {index + 1}</legend>
                    <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                      <p className="text-sm font-medium">
                        Line item {index + 1}
                      </p>
                      {provenance ? (
                        <p className="text-xs text-muted-foreground">
                          {provenance}
                        </p>
                      ) : null}
                    </div>
                    <div className={cn("mt-2 grid min-w-0 gap-3 sm:grid-cols-2 lg:grid-cols-4 2xl:items-end",
                      documentType !== "ORDER_RESPONSE" && "2xl:grid-cols-[5rem_minmax(12rem,2fr)_minmax(10rem,1fr)_minmax(7rem,.75fr)_minmax(10rem,1fr)_minmax(7rem,.75fr)_2.5rem]")}>
                      {documentType === "ORDER_RESPONSE" && <Field label="Line decision">
                        <BusinessCodeSelect value={line.responseCode ?? "Accepted"} options={ORDER_RESPONSE_LINE_CODES}
                          required onChange={(value) => updateLine(index, "responseCode", value)} />
                      </Field>}
                      <Field label="Line number">
                        <Input
                          className="min-w-0"
                          value={line.id}
                          onChange={(event) =>
                            updateLine(index, "id", event.target.value)
                          }
                          required
                        />
                      </Field>
                      <Field label="Item name">
                        <Input
                          className="min-w-0"
                          value={line.itemName}
                          onChange={(event) =>
                            updateLine(index, "itemName", event.target.value)
                          }
                          required
                        />
                      </Field>
                      <Field label="Item ID">
                        <Input
                          className="min-w-0"
                          value={line.itemIdentifier}
                          onChange={(event) =>
                            updateLine(
                              index,
                              "itemIdentifier",
                              event.target.value,
                            )
                          }
                          required
                        />
                      </Field>
                      <Field label="Quantity">
                        <Input
                          className="min-w-0"
                          type="number"
                          min="0"
                          step="any"
                          value={line.quantity}
                          onChange={(event) =>
                            updateLine(index, "quantity", event.target.value)
                          }
                          required
                        />
                      </Field>
                      <Field label="Unit">
                        <BusinessCodeSelect
                          value={line.unitCode}
                          options={QUANTITY_UNITS}
                          required
                          ariaLabel={`Unit for line ${line.id}`}
                          onChange={(value) =>
                            updateLine(index, "unitCode", value)
                          }
                        />
                      </Field>
                      {documentType === "ORDER_RESPONSE" ? <Field label="EAN / GTIN">
                        <Input value={line.standardItemIdentifier ?? ""} inputMode="numeric"
                          onChange={(event) => updateLine(index, "standardItemIdentifier", event.target.value)}
                          aria-label={`EAN or GTIN for line ${line.id}`} />
                      </Field> : <Field label="Unit price">
                        <Input
                          className="min-w-0"
                          type="number"
                          min="0"
                          step="0.01"
                          value={line.unitPrice}
                          onChange={(event) =>
                            updateLine(index, "unitPrice", event.target.value)
                          }
                          required
                        />
                      </Field>}
                      <Button
                        className="self-end"
                        type="button"
                        variant="ghost"
                        size="icon"
                        disabled={lines.length === 1}
                        onClick={() =>
                          setLines((current) =>
                            current.filter(
                              (_, lineIndex) => lineIndex !== index,
                            ),
                          )
                        }
                        aria-label={`Remove line ${index + 1}`}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </fieldset>
                )
              })}
            </div>
          </section>

          <AdvancedUblFields
            documentType={documentType}
            values={advancedFields}
            onChange={setAdvancedFields}
          />
        </div>

        <aside
          className="rounded-lg border bg-muted/20 p-4 xl:sticky xl:top-20 xl:self-start"
          aria-label="Document readiness"
        >
          <h2 className="text-base font-semibold">Ready to save</h2>
          <ul className="mt-3 divide-y text-sm">
            <li className="flex gap-3 py-3">
              <Check className="mt-0.5 h-4 w-4 text-success-foreground" />
              <span>
                <strong className="block font-medium">Document details</strong>
                <span className="text-xs text-muted-foreground">
                  Your work is saved together in one draft
                </span>
              </span>
            </li>
            <li className="flex gap-3 py-3">
              <ShieldCheck className="mt-0.5 h-4 w-4 text-success-foreground" />
              <span>
                <strong className="block font-medium">
                  Checked before sending
                </strong>
                <span className="text-xs text-muted-foreground">
                  Missing or invalid details are highlighted first
                </span>
              </span>
            </li>
            <li className="flex gap-3 py-3">
              <FileCheck2 className="mt-0.5 h-4 w-4 text-success-foreground" />
              <span>
                <strong className="block font-medium">
                  Prepared for your partner
                </strong>
                <span className="text-xs text-muted-foreground">
                  Their requirements are applied when you send it
                </span>
              </span>
            </li>
          </ul>
          {readinessWarnings.length ? (
            <div className="mt-4 border-y py-3 text-sm" role="status">
              <p className="font-medium text-amber-800">
                Needs attention before finalization
              </p>
              <ul className="mt-2 list-disc space-y-1 pl-5 text-xs text-muted-foreground">
                {readinessWarnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </div>
          ) : null}
          {selectedProfile ? (
            <div className="mt-4 border-t pt-4 text-sm">
              <p className="font-medium">{selectedProfile.name}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                Published version {selectedProfile.revision}
              </p>
              <ul className="mt-3 space-y-2 text-xs">
                {partnerRequirements.map((rule) => (
                  <li
                    key={rule.fieldKey}
                    className="flex items-start justify-between gap-3"
                  >
                    <span className="min-w-0 break-words text-muted-foreground">
                      {authoringCatalog[documentType].find(
                        (field) => field.fieldKey === rule.fieldKey,
                      )?.label ?? rule.fieldKey}
                    </span>
                    <span
                      className={
                        rule.requirement === "REQUIRED" && !rule.populated
                          ? "shrink-0 font-medium text-amber-800"
                          : "shrink-0 text-muted-foreground"
                      }
                    >
                      {rule.requirement === "REQUIRED" && !rule.populated
                        ? "Needed"
                        : rule.requirement?.toLowerCase()}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          {smartCopy?.requiresCommercialReview ? (
            <label className="mt-4 flex items-start gap-2 rounded-md border border-amber-500/40 bg-amber-500/5 px-3 py-2.5 text-sm">
              <input
                type="checkbox"
                className="mt-1"
                checked={commercialReviewed}
                onChange={(event) =>
                  setCommercialReviewed(event.target.checked)
                }
              />
              <span>
                <strong className="block font-medium">
                  Commercial values reviewed
                </strong>
                <span className="text-xs text-muted-foreground">
                  I checked copied quantities, prices, and amounts.
                </span>
              </span>
            </label>
          ) : null}
          <Button className="mt-5 w-full" disabled={busy || copyLoading || copySourcePending}>
            {busy
              ? "Saving draft…"
              : copyLoading
                ? "Copying details…"
                : "Save document draft"}
          </Button>
          <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
            Saving creates a normal versioned Draft. It does not finalize,
            transform, approve, or send anything.
          </p>
        </aside>
      </form>
    </LayoutBody>
  )
}

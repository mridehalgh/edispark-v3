"use client"

import { AlertTriangle, Check, ChevronDown, Package, Plus, Sparkles, Trash2 } from "lucide-react"
import { useState } from "react"

import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { BusinessCodeSelect } from "@/components/documents/business-code-select"
import { COUNTRIES, HANDLING_UNIT_TYPES, ORDER_RESPONSE_CODES, PAYMENT_MEANS, TAX_CATEGORIES, WEIGHT_UNITS } from "@/lib/business-code-lists"
import {
  fillRemainingAllocations,
  getLineAllocationSummary,
  hasValidSscc,
  type AuthorableDocumentType,
  type AuthoringHandlingUnit,
  type AuthoringLine,
  type AuthoringSpecialistValues,
} from "@/lib/ubl-authoring"
import { cn } from "@/lib/utils"

type Props = {
  documentType: AuthorableDocumentType
  values: AuthoringSpecialistValues
  lines: AuthoringLine[]
  onChange: (values: AuthoringSpecialistValues) => void
  onLineChange: (index: number, field: keyof AuthoringLine, value: string) => void
  ssccGenerationAvailable?: boolean
  generatingSscc?: boolean
  onGenerateSscc?: (unitIndex: number) => void
  onGenerateMissingSsccs?: () => void
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return <label className="grid gap-1.5 text-sm font-medium">{label}{children}{hint ? <span className="text-xs font-normal text-muted-foreground">{hint}</span> : null}</label>
}

function newHandlingUnit(index: number): AuthoringHandlingUnit {
  return { id: `HU-${index + 1}`, sscc: "", typeCode: "PALLET", packageCount: "1", grossWeight: "", weightUnit: "KGM", shippingMarks: "", allocations: [] }
}

const quantityFormatter = new Intl.NumberFormat("en-GB", { maximumFractionDigits: 6 })

function quantity(value: number, unitCode: string) {
  return `${quantityFormatter.format(value)} ${unitCode || "units"}`
}

function groupedQuantities(items: Array<{ value: number; unitCode: string }>) {
  const grouped = items.reduce<Record<string, number>>((result, item) => {
    if (item.value <= 0) return result
    const unitCode = item.unitCode || "units"
    result[unitCode] = (result[unitCode] ?? 0) + item.value
    return result
  }, {})

  return Object.entries(grouped)
    .map(([unitCode, value]) => quantity(value, unitCode))
    .join(" · ")
}

export function SpecialistAuthoringSections({ documentType, values, lines, onChange, onLineChange,
  ssccGenerationAvailable = false, generatingSscc = false, onGenerateSscc, onGenerateMissingSsccs }: Props) {
  const [activeHandlingUnit, setActiveHandlingUnit] = useState("unit-0")
  const change = <K extends keyof AuthoringSpecialistValues>(key: K, value: AuthoringSpecialistValues[K]) => onChange({ ...values, [key]: value })
  const shipmentDocument = documentType === "DESPATCH_ADVICE" || documentType === "RECEIPT_ADVICE"
  const commercialDocument = documentType === "ORDER" || documentType === "ORDER_RESPONSE" || documentType === "INVOICE"

  function updateHandlingUnit(index: number, next: AuthoringHandlingUnit) {
    change("handlingUnits", values.handlingUnits.map((unit, unitIndex) => unitIndex === index ? next : unit))
  }

  function updateLineAllocation(unitIndex: number, lineId: string, allocationQuantity: string) {
    const unit = values.handlingUnits[unitIndex]
    updateHandlingUnit(unitIndex, {
      ...unit,
      allocations: [
        ...unit.allocations.filter((entry) => entry.lineId !== lineId),
        ...(allocationQuantity ? [{ lineId, quantity: allocationQuantity }] : []),
      ],
    })
  }

  function addHandlingUnit() {
    const nextIndex = values.handlingUnits.length
    change("handlingUnits", [...values.handlingUnits, newHandlingUnit(nextIndex)])
    setActiveHandlingUnit(`unit-${nextIndex}`)
  }

  function removeHandlingUnit(index: number) {
    change("handlingUnits", values.handlingUnits.filter((_, unitIndex) => unitIndex !== index))
    const nextIndex = Math.max(0, Math.min(index, values.handlingUnits.length - 2))
    setActiveHandlingUnit(values.handlingUnits.length > 1 ? `unit-${nextIndex}` : "")
  }

  const packingSummaries = lines.map((line) => getLineAllocationSummary(line, values.handlingUnits, -1))
  const fullyPackedLines = packingSummaries.filter((summary) => summary.complete).length
  const overAllocatedLines = packingSummaries.filter((summary) => summary.overAllocated > 0).length
  const packingProgress = lines.length === 0 ? 0 : packingSummaries.reduce((total, summary) => {
    if (summary.required <= 0) return total
    return total + Math.min(1, summary.allocatedTotal / summary.required)
  }, 0) / lines.length * 100
  const remainingQuantities = groupedQuantities(lines.map((line, index) => ({
    value: packingSummaries[index]?.remaining ?? 0,
    unitCode: line.unitCode,
  })))

  return <>
    {documentType === "ORDER_RESPONSE" ? <section className="border-t py-6" aria-labelledby="response-heading">
      <h2 id="response-heading" className="text-base font-semibold">Response decision</h2>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <Field label="Overall response"><BusinessCodeSelect value={values.responseCode} options={ORDER_RESPONSE_CODES} required onChange={(value) => change("responseCode", value)} /></Field>
        <Field label="Decision explanation"><Input value={values.responseDescription} onChange={(event) => change("responseDescription", event.target.value)} placeholder="Explain changes or rejection" /></Field>
      </div>
      <details className="mt-5 rounded-md border p-4" open={Boolean(values.bqVendorNumber || values.bqOrderVersion || values.bqLocationReference)}>
        <summary className="cursor-pointer text-sm font-medium">B&Q acknowledgement details</summary>
        <p className="mt-2 text-sm text-muted-foreground">Copy these references from the purchase order. B&Q full acceptance also requires a promised delivery date and a 13-digit EAN on every line.</p>
        <div className="mt-4 grid gap-4 sm:grid-cols-3">
          <Field label="B&Q vendor number"><Input value={values.bqVendorNumber ?? ""} inputMode="numeric" maxLength={7} onChange={(event) => change("bqVendorNumber", event.target.value)} /></Field>
          <Field label="Purchase order version"><Input value={values.bqOrderVersion ?? ""} maxLength={40} onChange={(event) => change("bqOrderVersion", event.target.value)} /></Field>
          <Field label="Sales order / store reference"><Input value={values.bqLocationReference ?? ""} maxLength={17} onChange={(event) => change("bqLocationReference", event.target.value)} /></Field>
        </div>
      </details>
    </section> : null}

    {(commercialDocument || shipmentDocument) ? <section className="border-t py-6" aria-labelledby="delivery-heading">
      <h2 id="delivery-heading" className="text-base font-semibold">Delivery</h2>
      <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {documentType === "ORDER" ? <Field label="Requested delivery date"><Input type="date" value={values.requestedDeliveryDate} onChange={(event) => change("requestedDeliveryDate", event.target.value)} /></Field> : null}
        {documentType === "ORDER_RESPONSE" ? <Field label="Promised delivery date"><Input type="date" value={values.promisedDeliveryDate} onChange={(event) => change("promisedDeliveryDate", event.target.value)} /></Field> : null}
        {shipmentDocument || documentType === "INVOICE" ? <Field label="Actual delivery date"><Input type="date" value={values.actualDeliveryDate} onChange={(event) => change("actualDeliveryDate", event.target.value)} /></Field> : null}
        {documentType === "DESPATCH_ADVICE" ? <Field label="Despatch date"><Input type="date" value={values.actualDespatchDate ?? ""} onChange={(event) => change("actualDespatchDate", event.target.value)} /></Field> : null}
        <Field label="Address line"><Input value={values.deliveryAddress.line1} onChange={(event) => change("deliveryAddress", { ...values.deliveryAddress, line1: event.target.value })} placeholder="[address]" /></Field>
        <Field label="Town or city"><Input value={values.deliveryAddress.city} onChange={(event) => change("deliveryAddress", { ...values.deliveryAddress, city: event.target.value })} /></Field>
        <Field label="Postcode"><Input value={values.deliveryAddress.postcode} onChange={(event) => change("deliveryAddress", { ...values.deliveryAddress, postcode: event.target.value })} /></Field>
        <Field label="Country"><BusinessCodeSelect value={values.deliveryAddress.countryCode} options={COUNTRIES} emptyLabel="Choose a country" onChange={(value) => change("deliveryAddress", { ...values.deliveryAddress, countryCode: value })} /></Field>
      </div>
    </section> : null}

    {shipmentDocument ? <section className="border-t py-6" aria-labelledby="shipment-heading">
      <h2 id="shipment-heading" className="text-base font-semibold">Shipment and transport</h2>
      <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Field label="Shipment ID"><Input value={values.shipmentId} onChange={(event) => change("shipmentId", event.target.value)} required /></Field>
        <Field label="Tracking or consignment ID"><Input value={values.trackingId} onChange={(event) => change("trackingId", event.target.value)} /></Field>
        <Field label="Carrier"><Input value={values.carrierName} onChange={(event) => change("carrierName", event.target.value)} /></Field>
        <Field label="Vehicle or trailer"><Input value={values.vehicleId} onChange={(event) => change("vehicleId", event.target.value)} /></Field>
        <Field label="Shipment gross weight"><Input type="number" min="0" step="any" value={values.grossWeight} onChange={(event) => change("grossWeight", event.target.value)} /></Field>
        <Field label="Weight unit"><BusinessCodeSelect value={values.weightUnit} options={WEIGHT_UNITS} onChange={(value) => change("weightUnit", value)} /></Field>
      </div>

      <div className="mt-8 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <h3 className="text-sm font-semibold">Pallets and handling units</h3>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">Work on one unit at a time. Its summary stays visible when you move to the next.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {ssccGenerationAvailable && values.handlingUnits.some((unit) => !unit.sscc) ? <Button className="shrink-0" type="button" size="sm" variant="outline" disabled={generatingSscc} onClick={onGenerateMissingSsccs}><Sparkles className="mr-2 h-4 w-4" />{generatingSscc ? "Generating…" : "Generate all SSCCs"}</Button> : null}
          <Button className="shrink-0" type="button" size="sm" variant="outline" onClick={addHandlingUnit}><Plus className="mr-2 h-4 w-4" />Add unit</Button>
        </div>
      </div>
      {values.handlingUnits.length ? <>
        <div className="mt-4 border-y bg-muted/30 px-4 py-3" aria-live="polite">
          <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 text-sm">
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
              <span className="font-medium">{values.handlingUnits.length} {values.handlingUnits.length === 1 ? "unit" : "units"}</span>
              <span>{fullyPackedLines} of {lines.length} item lines packed</span>
              <span className="text-muted-foreground">{remainingQuantities ? `${remainingQuantities} remaining` : "Everything is packed"}</span>
            </div>
            {overAllocatedLines > 0 ? <span className="inline-flex items-center gap-1.5 font-medium text-warning-foreground"><AlertTriangle className="h-4 w-4" aria-hidden="true" />{overAllocatedLines} {overAllocatedLines === 1 ? "line is" : "lines are"} over-packed</span> : <span className="tabular-nums text-muted-foreground">{Math.round(packingProgress)}% packed</span>}
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label="Packing progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(packingProgress)}>
            <div className="h-full rounded-full bg-success-foreground transition-[width] duration-200" style={{ width: `${packingProgress}%` }} />
          </div>
        </div>
        <Accordion className="border-x border-b" type="single" collapsible value={activeHandlingUnit} onValueChange={setActiveHandlingUnit}>{values.handlingUnits.map((unit, index) => {
          const allocatedLines = unit.allocations.filter((allocation) => Number(allocation.quantity) > 0)
          const quantitiesOnUnit = groupedQuantities(allocatedLines.map((allocation) => ({
            value: Number(allocation.quantity) || 0,
            unitCode: lines.find((line) => line.id === allocation.lineId)?.unitCode ?? "",
          })))
          const canFillRemaining = lines.some((line) => {
            const summary = getLineAllocationSummary(line, values.handlingUnits, index)
            return summary.availableForCurrent > 0 && summary.availableForCurrent !== summary.allocatedHere
          })
          const unitLineCount = allocatedLines.length
          const validSscc = hasValidSscc(unit.sscc)
          return <AccordionItem key={`${unit.id}-${index}`} value={`unit-${index}`} className="last:border-b-0">
            <AccordionTrigger className="min-h-[4.5rem] px-4 py-3 text-left hover:bg-muted/35 hover:no-underline data-[state=open]:bg-muted/35">
              <span className="grid min-w-0 flex-1 grid-cols-[minmax(0,1fr)_auto] items-center gap-3 pr-3 sm:grid-cols-[minmax(12rem,1.4fr)_minmax(8rem,1fr)_6rem_minmax(9rem,1fr)]">
                <span className="flex min-w-0 items-center gap-3">
                  <span className={cn("grid h-9 w-9 shrink-0 place-items-center rounded-md", validSscc ? "bg-success/35 text-success-foreground" : "bg-muted text-muted-foreground")}><Package className="h-4 w-4" aria-hidden="true" /></span>
                  <span className="min-w-0"><strong className="block truncate text-sm font-semibold">{unit.id || `Handling unit ${index + 1}`}</strong><span className="mt-0.5 block truncate text-xs font-normal text-muted-foreground">{unit.typeCode.toLowerCase().replaceAll("_", " ")} · {unit.packageCount || "0"} {Number(unit.packageCount) === 1 ? "package" : "packages"}</span></span>
                </span>
                <span className="hidden min-w-0 sm:block"><span className="block text-xs font-normal text-muted-foreground">Packed here</span><span className="block truncate text-sm font-medium tabular-nums">{quantitiesOnUnit || "Nothing yet"}</span></span>
                <span className="hidden sm:block"><span className="block text-xs font-normal text-muted-foreground">Item lines</span><span className="text-sm font-medium tabular-nums">{unitLineCount}</span></span>
                <span className="justify-self-end">{validSscc ? <span className="inline-flex items-center gap-1.5 rounded-full bg-success/35 px-2.5 py-1 text-xs font-semibold text-success-foreground"><Check className="h-3.5 w-3.5" aria-hidden="true" />SSCC ready</span> : unit.sscc ? <span className="inline-flex items-center gap-1.5 rounded-full bg-muted px-2.5 py-1 text-xs font-semibold text-warning-foreground"><AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />Check SSCC</span> : <span className="inline-flex rounded-full bg-muted px-2.5 py-1 text-xs font-semibold text-muted-foreground">Needs SSCC</span>}</span>
              </span>
            </AccordionTrigger>
            <AccordionContent className="border-t bg-muted/20 p-0">
              <fieldset><legend className="sr-only">Handling unit {index + 1}</legend>
                <div className="px-4 py-5">
                  <div className="flex items-center justify-between gap-4">
                    <div><h4 className="text-sm font-semibold">Unit details</h4><p className="mt-0.5 text-xs text-muted-foreground">Identity, packaging and label information.</p></div>
                    <Button type="button" size="sm" variant="ghost" onClick={() => removeHandlingUnit(index)}><Trash2 className="mr-2 h-4 w-4" />Remove unit</Button>
                  </div>
                  <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-12">
                    <div className="lg:col-span-3"><Field label="Handling unit ID"><Input value={unit.id} onChange={(event) => updateHandlingUnit(index, { ...unit, id: event.target.value })} required /></Field></div>
                    <div className="lg:col-span-3"><Field label="Type"><BusinessCodeSelect value={unit.typeCode} options={HANDLING_UNIT_TYPES} onChange={(value) => updateHandlingUnit(index, { ...unit, typeCode: value })} /></Field></div>
                    <div className="lg:col-span-2"><Field label="Package count"><Input type="number" min="0" step="1" value={unit.packageCount} onChange={(event) => updateHandlingUnit(index, { ...unit, packageCount: event.target.value })} /></Field></div>
                    <div className="lg:col-span-2"><Field label="Gross weight"><Input type="number" min="0" step="any" value={unit.grossWeight} onChange={(event) => updateHandlingUnit(index, { ...unit, grossWeight: event.target.value })} /></Field></div>
                    <div className="lg:col-span-2"><Field label="Weight unit"><BusinessCodeSelect value={unit.weightUnit} options={WEIGHT_UNITS} onChange={(value) => updateHandlingUnit(index, { ...unit, weightUnit: value })} /></Field></div>
                    <div className="lg:col-span-7"><div className="grid gap-1.5 text-sm font-medium"><span>SSCC</span><div className="flex gap-2"><Input className="font-mono tabular-nums" aria-label={`SSCC for ${unit.id || `handling unit ${index + 1}`}`} inputMode="numeric" maxLength={18} value={unit.sscc} readOnly={unit.ssccSource === "GENERATED"} placeholder="18-digit shipping container code" onChange={(event) => updateHandlingUnit(index, { ...unit, sscc: event.target.value.replace(/\D/g, ""), ssccSource: event.target.value ? "MANUAL" : undefined, ssccReservationId: undefined })} />{ssccGenerationAvailable && unit.ssccSource !== "GENERATED" ? <Button type="button" variant="outline" disabled={generatingSscc} onClick={() => onGenerateSscc?.(index)}><Sparkles className="mr-2 h-4 w-4" />Generate</Button> : null}{unit.ssccSource === "GENERATED" ? <Button type="button" variant="ghost" onClick={() => updateHandlingUnit(index, { ...unit, sscc: "", ssccSource: undefined, ssccReservationId: undefined })}>Replace</Button> : null}</div><span className="text-xs font-normal text-muted-foreground">{unit.ssccSource === "GENERATED" ? "Generated from your organisation’s licensed GS1 range." : "Enter 18 digits, or generate one from your organisation’s range."}</span></div></div>
                    <div className="lg:col-span-5"><Field label="Shipping marks" hint="Optional text printed on the package"><Input value={unit.shippingMarks} onChange={(event) => updateHandlingUnit(index, { ...unit, shippingMarks: event.target.value })} /></Field></div>
                  </div>
                </div>
                <div className="border-t bg-background px-4 py-5">
                  <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
                    <div><h4 className="text-sm font-semibold">Pack items</h4><p className="mt-0.5 text-xs text-muted-foreground">Enter what is physically on this unit. Available quantities update across every unit.</p></div>
                    <div className="flex flex-wrap gap-2">{unit.allocations.length > 0 ? <Button type="button" size="sm" variant="ghost" onClick={() => updateHandlingUnit(index, { ...unit, allocations: [] })}>Clear quantities</Button> : null}<Button type="button" size="sm" variant="outline" disabled={!canFillRemaining} onClick={() => change("handlingUnits", fillRemainingAllocations(lines, values.handlingUnits, index))}>Pack everything remaining</Button></div>
                  </div>
                  <div className="mt-4 overflow-hidden border-y">
                    <div className="hidden grid-cols-[minmax(12rem,1.5fr)_7rem_7rem_7rem_minmax(12rem,.8fr)] gap-3 bg-muted/40 px-3 py-2 text-xs font-medium text-muted-foreground lg:grid"><span>Item</span><span className="text-right">Ordered</span><span className="text-right">Other units</span><span className="text-right">Remaining</span><span>On this unit</span></div>
                    <div className="divide-y">{lines.map((line, lineIndex) => {
                      const allocation = unit.allocations.find((entry) => entry.lineId === line.id)
                      const summary = getLineAllocationSummary(line, values.handlingUnits, index)
                      const allocationId = `handling-unit-${index}-line-${lineIndex}`
                      const helpId = `${allocationId}-help`
                      const exceedsAvailable = summary.allocatedHere > summary.availableForCurrent
                      return <div key={line.id} className="grid gap-3 px-3 py-3 lg:grid-cols-[minmax(12rem,1.5fr)_7rem_7rem_7rem_minmax(12rem,.8fr)] lg:items-center">
                        <div className="min-w-0"><p className="truncate text-sm font-medium">{line.id} · {line.itemName || "Unnamed item"}</p><div className="mt-1 flex flex-wrap gap-2 lg:hidden"><span className="text-xs text-muted-foreground">Ordered {quantity(summary.required, line.unitCode)}</span><span className="text-xs text-muted-foreground">Other units {quantity(summary.allocatedElsewhere, line.unitCode)}</span></div></div>
                        <span className="hidden text-right text-sm tabular-nums lg:block">{quantity(summary.required, line.unitCode)}</span>
                        <span className="hidden text-right text-sm tabular-nums text-muted-foreground lg:block">{quantity(summary.allocatedElsewhere, line.unitCode)}</span>
                        <span className={cn("text-sm tabular-nums lg:text-right", summary.overAllocated > 0 ? "font-medium text-warning-foreground" : summary.remaining === 0 ? "text-success-foreground" : "text-foreground")}>{summary.overAllocated > 0 ? `Over ${quantity(summary.overAllocated, line.unitCode)}` : summary.remaining === 0 ? "Packed" : quantity(summary.remaining, line.unitCode)}</span>
                        <div><label className="sr-only" htmlFor={allocationId}>{line.itemName || `Line ${line.id}`} on this unit</label><div className="flex gap-2"><Input id={allocationId} className="h-9 min-w-0 text-right tabular-nums" type="number" min="0" max={summary.availableForCurrent} step="any" value={allocation?.quantity ?? ""} placeholder="0" aria-describedby={helpId} aria-invalid={exceedsAvailable || undefined} onChange={(event) => updateLineAllocation(index, line.id, event.target.value)} /><Button className="shrink-0" type="button" size="sm" variant="outline" disabled={summary.availableForCurrent <= 0 || summary.availableForCurrent === summary.allocatedHere} onClick={() => updateLineAllocation(index, line.id, String(summary.availableForCurrent))}>Use max</Button></div><p id={helpId} className={cn("mt-1 text-right text-xs", exceedsAvailable ? "text-warning-foreground" : "text-muted-foreground")}>Up to {quantity(summary.availableForCurrent, line.unitCode)}</p></div>
                      </div>
                    })}</div>
                  </div>
                </div>
              </fieldset>
            </AccordionContent>
          </AccordionItem>
        })}</Accordion>
      </> : <div className="mt-4 border-y py-8 text-center"><Package className="mx-auto h-5 w-5 text-muted-foreground" aria-hidden="true" /><p className="mt-2 text-sm font-medium">No handling units yet</p><p className="mt-1 text-sm text-muted-foreground">Add the first pallet or package, then assign what is packed on it.</p><Button className="mt-4" type="button" size="sm" variant="outline" onClick={addHandlingUnit}><Plus className="mr-2 h-4 w-4" />Add first unit</Button></div>}
    </section> : null}

    {(documentType === "INVOICE" || documentType === "REMITTANCE_ADVICE") ? <section className="border-t py-6" aria-labelledby="payment-heading"><h2 id="payment-heading" className="text-base font-semibold">Payment</h2><div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Field label="Payment method"><BusinessCodeSelect value={values.paymentMeansCode} options={PAYMENT_MEANS} emptyLabel="Choose a payment method" onChange={(value) => change("paymentMeansCode", value)} /></Field><Field label="Due date"><Input type="date" value={values.paymentDueDate} onChange={(event) => change("paymentDueDate", event.target.value)} /></Field><Field label="Account identifier"><Input value={values.paymentAccountId} onChange={(event) => change("paymentAccountId", event.target.value)} /></Field><Field label="Payment terms"><Input value={values.paymentTerms} onChange={(event) => change("paymentTerms", event.target.value)} /></Field></div></section> : null}

    {commercialDocument ? <details className="group border-t py-5"><summary className="flex cursor-pointer list-none items-center gap-3 text-sm"><span className="font-semibold">Tax and adjustments</span><span className="text-muted-foreground">Optional</span><ChevronDown className="ml-auto h-4 w-4 text-muted-foreground transition-transform duration-200 group-open:rotate-180" aria-hidden="true" /></summary><div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Field label="Tax treatment"><BusinessCodeSelect value={values.taxCategory} options={TAX_CATEGORIES} onChange={(value) => change("taxCategory", value)} /></Field><Field label="Tax percentage"><Input type="number" min="0" max="100" step="any" value={values.taxPercent} onChange={(event) => change("taxPercent", event.target.value)} /></Field><Field label="Tax amount"><Input type="number" min="0" step="0.01" value={values.taxAmount} onChange={(event) => change("taxAmount", event.target.value)} /></Field><Field label="Allowance amount"><Input type="number" min="0" step="0.01" value={values.allowanceAmount} onChange={(event) => change("allowanceAmount", event.target.value)} /></Field><Field label="Allowance reason"><Input value={values.allowanceReason} onChange={(event) => change("allowanceReason", event.target.value)} /></Field></div></details> : null}

    {(documentType === "DESPATCH_ADVICE" || documentType === "RECEIPT_ADVICE") ? <details className="group border-t py-5"><summary className="flex cursor-pointer list-none items-center gap-3 text-sm"><span className="font-semibold">Item traceability</span><span className="text-muted-foreground">Lots, expiry dates and serial numbers</span><ChevronDown className="ml-auto h-4 w-4 text-muted-foreground transition-transform duration-200 group-open:rotate-180" aria-hidden="true" /></summary><div className="mt-4 divide-y border-y">{lines.map((line, index) => <fieldset key={line.id} className="grid gap-3 py-4 sm:grid-cols-2 lg:grid-cols-4"><legend className="sr-only">Traceability for line {line.id}</legend><Field label={`${line.id} lot number`}><Input value={line.lotNumber ?? ""} onChange={(event) => onLineChange(index, "lotNumber", event.target.value)} /></Field><Field label="Expiry date"><Input type="date" value={line.expiryDate ?? ""} onChange={(event) => onLineChange(index, "expiryDate", event.target.value)} /></Field><Field label="Serial numbers" hint="Comma-separated"><Input value={line.serialNumbers ?? ""} onChange={(event) => onLineChange(index, "serialNumbers", event.target.value)} /></Field>{documentType === "RECEIPT_ADVICE" ? <><Field label="Rejected quantity"><Input type="number" min="0" step="any" value={line.rejectedQuantity ?? ""} onChange={(event) => onLineChange(index, "rejectedQuantity", event.target.value)} /></Field><Field label="Rejection reason"><Input value={line.rejectionReason ?? ""} onChange={(event) => onLineChange(index, "rejectionReason", event.target.value)} /></Field></> : null}</fieldset>)}</div></details> : null}
  </>
}

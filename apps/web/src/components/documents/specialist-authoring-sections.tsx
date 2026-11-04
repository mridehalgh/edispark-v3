"use client"

import { AlertTriangle, Check, ChevronDown, Package, Plus, Trash2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { BusinessCodeSelect } from "@/components/documents/business-code-select"
import { COUNTRIES, HANDLING_UNIT_TYPES, ORDER_RESPONSE_CODES, PAYMENT_MEANS, TAX_CATEGORIES, WEIGHT_UNITS } from "@/lib/business-code-lists"
import {
  fillRemainingAllocations,
  getLineAllocationSummary,
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

export function SpecialistAuthoringSections({ documentType, values, lines, onChange, onLineChange }: Props) {
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
    </section> : null}

    {(commercialDocument || shipmentDocument) ? <section className="border-t py-6" aria-labelledby="delivery-heading">
      <h2 id="delivery-heading" className="text-base font-semibold">Delivery</h2>
      <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {documentType === "ORDER" ? <Field label="Requested delivery date"><Input type="date" value={values.requestedDeliveryDate} onChange={(event) => change("requestedDeliveryDate", event.target.value)} /></Field> : null}
        {documentType === "ORDER_RESPONSE" ? <Field label="Promised delivery date"><Input type="date" value={values.promisedDeliveryDate} onChange={(event) => change("promisedDeliveryDate", event.target.value)} /></Field> : null}
        {shipmentDocument || documentType === "INVOICE" ? <Field label="Actual delivery date"><Input type="date" value={values.actualDeliveryDate} onChange={(event) => change("actualDeliveryDate", event.target.value)} /></Field> : null}
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

      <div className="mt-8 flex flex-col items-start justify-between gap-4 sm:flex-row">
        <div>
          <h3 className="text-sm font-semibold">Pallets and handling units</h3>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">Add each physical pallet, carton or roll cage, then assign the items packed on it.</p>
        </div>
        <Button className="shrink-0" type="button" size="sm" variant="outline" onClick={() => change("handlingUnits", [...values.handlingUnits, newHandlingUnit(values.handlingUnits.length)])}>
          <Plus className="mr-2 h-4 w-4" />Add handling unit
        </Button>
      </div>
      {values.handlingUnits.length ? <>
        <div className="mt-4 border-y bg-muted/20 px-3 py-3" aria-live="polite">
          <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 text-sm">
            <div>
              <span className="font-medium">{fullyPackedLines} of {lines.length} lines fully packed</span>
              <span className="ml-2 text-muted-foreground">{remainingQuantities || "Nothing left to pack"}</span>
            </div>
            {overAllocatedLines > 0 ? <span className="inline-flex items-center gap-1.5 font-medium text-warning-foreground"><AlertTriangle className="h-4 w-4" aria-hidden="true" />{overAllocatedLines} {overAllocatedLines === 1 ? "line needs" : "lines need"} attention</span> : null}
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label="Packing progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(packingProgress)}>
            <div className="h-full rounded-full bg-success-foreground transition-[width] duration-200" style={{ width: `${packingProgress}%` }} />
          </div>
        </div>
        <div className="divide-y overflow-hidden rounded-b-md border-x border-b">{values.handlingUnits.map((unit, index) => {
          const allocatedLines = unit.allocations.filter((allocation) => Number(allocation.quantity) > 0)
          const quantitiesOnUnit = groupedQuantities(allocatedLines.map((allocation) => ({
            value: Number(allocation.quantity) || 0,
            unitCode: lines.find((line) => line.id === allocation.lineId)?.unitCode ?? "",
          })))
          const canFillRemaining = lines.some((line) => {
            const summary = getLineAllocationSummary(line, values.handlingUnits, index)
            return summary.availableForCurrent > 0 && summary.availableForCurrent !== summary.allocatedHere
          })
          return <details key={`${unit.id}-${index}`} className="group/unit" open={!unit.sscc}>
            <summary className="grid min-h-16 cursor-pointer list-none grid-cols-[1.25rem_minmax(0,1fr)] items-center gap-3 px-3 py-2.5 text-sm sm:grid-cols-[1.25rem_minmax(9rem,1.3fr)_minmax(8rem,1fr)_6rem_minmax(10rem,1fr)]">
              <ChevronDown className="h-4 w-4 text-muted-foreground transition-transform duration-200 group-open/unit:rotate-180" aria-hidden="true" />
              <span className="flex min-w-0 items-center gap-2">
                <Package className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                <span className="min-w-0"><strong className="block truncate font-medium">{unit.id || `Handling unit ${index + 1}`}</strong><span className="block truncate text-xs text-muted-foreground">{unit.sscc ? `SSCC ${unit.sscc}` : "SSCC not added"}</span></span>
              </span>
              <span className="col-start-2 sm:col-start-auto"><span className="block text-xs text-muted-foreground">Type</span>{unit.typeCode.toLowerCase().replaceAll("_", " ")}</span>
              <span className="col-start-2 sm:col-start-auto"><span className="block text-xs text-muted-foreground">Packages</span><span className="tabular-nums">{unit.packageCount || "0"}</span></span>
              <span className="col-start-2 sm:col-start-auto"><span className="block text-xs text-muted-foreground">Packed here</span><span className="tabular-nums">{quantitiesOnUnit || "No items yet"}</span></span>
            </summary>
            <fieldset className="border-t bg-muted/20 px-3 py-4"><legend className="sr-only">Handling unit {index + 1}</legend>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Handling unit ID"><Input value={unit.id} onChange={(event) => updateHandlingUnit(index, { ...unit, id: event.target.value })} required /></Field>
          <Field label="SSCC" hint="18 digits; GS1 check digit is validated before finalization."><Input inputMode="numeric" maxLength={18} value={unit.sscc} onChange={(event) => updateHandlingUnit(index, { ...unit, sscc: event.target.value.replace(/\D/g, "") })} /></Field>
          <Field label="Type"><BusinessCodeSelect value={unit.typeCode} options={HANDLING_UNIT_TYPES} onChange={(value) => updateHandlingUnit(index, { ...unit, typeCode: value })} /></Field>
          <Field label="Package count"><Input type="number" min="0" step="1" value={unit.packageCount} onChange={(event) => updateHandlingUnit(index, { ...unit, packageCount: event.target.value })} /></Field>
          <Field label="Gross weight"><Input type="number" min="0" step="any" value={unit.grossWeight} onChange={(event) => updateHandlingUnit(index, { ...unit, grossWeight: event.target.value })} /></Field>
          <Field label="Weight unit"><BusinessCodeSelect value={unit.weightUnit} options={WEIGHT_UNITS} onChange={(value) => updateHandlingUnit(index, { ...unit, weightUnit: value })} /></Field>
          <Field label="Shipping marks"><Input value={unit.shippingMarks} onChange={(event) => updateHandlingUnit(index, { ...unit, shippingMarks: event.target.value })} /></Field>
          <div className="flex items-end justify-end"><Button type="button" size="sm" variant="ghost" onClick={() => change("handlingUnits", values.handlingUnits.filter((_, unitIndex) => unitIndex !== index))}><Trash2 className="mr-2 h-4 w-4" />Remove</Button></div>
        </div>
        <div className="mt-5 border-t pt-4">
          <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
            <div><p className="text-sm font-medium">Items on this unit</p><p className="mt-0.5 text-xs text-muted-foreground">See what is already packed elsewhere and fill only the quantity still available.</p></div>
            <div className="flex flex-wrap gap-2">
              {unit.allocations.length > 0 ? <Button type="button" size="sm" variant="ghost" onClick={() => updateHandlingUnit(index, { ...unit, allocations: [] })}>Clear this unit</Button> : null}
              <Button type="button" size="sm" variant="outline" disabled={!canFillRemaining} onClick={() => change("handlingUnits", fillRemainingAllocations(lines, values.handlingUnits, index))}>Pack all remaining</Button>
            </div>
          </div>
          <div className="mt-3 divide-y border-y">{lines.map((line, lineIndex) => {
            const allocation = unit.allocations.find((entry) => entry.lineId === line.id)
            const summary = getLineAllocationSummary(line, values.handlingUnits, index)
            const allocationId = `handling-unit-${index}-line-${lineIndex}`
            const helpId = `${allocationId}-help`
            const exceedsAvailable = summary.allocatedHere > summary.availableForCurrent
            return <div key={line.id} className="grid gap-3 py-3 lg:grid-cols-[minmax(0,1.2fr)_minmax(12rem,.8fr)_minmax(17rem,1fr)] lg:items-center">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{line.id} · {line.itemName || "Unnamed item"}</p>
                <p className="mt-0.5 text-xs text-muted-foreground">Ordered {quantity(summary.required, line.unitCode)}{summary.allocatedElsewhere > 0 ? ` · ${quantity(summary.allocatedElsewhere, line.unitCode)} on other units` : ""}</p>
              </div>
              <div className={cn("flex items-center gap-1.5 text-sm", summary.overAllocated > 0 ? "font-medium text-warning-foreground" : summary.complete ? "text-success-foreground" : "text-foreground")}>
                {summary.overAllocated > 0 ? <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" /> : summary.complete ? <Check className="h-4 w-4 shrink-0" aria-hidden="true" /> : null}
                {summary.overAllocated > 0 ? `Over by ${quantity(summary.overAllocated, line.unitCode)}` : summary.complete ? "Fully packed" : `${quantity(summary.remaining, line.unitCode)} remaining`}
              </div>
              <div className="grid gap-1.5">
                <label className="text-xs font-medium" htmlFor={allocationId}>On this unit</label>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Input id={allocationId} className="min-w-0 sm:flex-1" type="number" min="0" max={summary.availableForCurrent} step="any" value={allocation?.quantity ?? ""} placeholder="0" aria-describedby={helpId} aria-invalid={exceedsAvailable || undefined} onChange={(event) => updateLineAllocation(index, line.id, event.target.value)} />
                  <Button className="shrink-0" type="button" size="sm" variant="outline" disabled={summary.availableForCurrent <= 0 || summary.availableForCurrent === summary.allocatedHere} onClick={() => updateLineAllocation(index, line.id, String(summary.availableForCurrent))}>Pack remaining</Button>
                  {summary.allocatedHere > 0 ? <Button className="shrink-0" type="button" size="sm" variant="ghost" onClick={() => updateLineAllocation(index, line.id, "")}>Clear</Button> : null}
                </div>
                <p id={helpId} className={cn("text-xs", exceedsAvailable ? "text-warning-foreground" : "text-muted-foreground")}>Maximum on this unit: {quantity(summary.availableForCurrent, line.unitCode)}</p>
              </div>
            </div>
          })}</div>
        </div>
      </fieldset></details>})}</div>
      </> : <div className="mt-4 rounded-md border border-dashed px-4 py-7 text-center"><Package className="mx-auto h-5 w-5 text-muted-foreground" aria-hidden="true" /><p className="mt-2 text-sm font-medium">No handling units yet</p><p className="mt-1 text-sm text-muted-foreground">Add a pallet or package when this shipment is packed.</p></div>}
    </section> : null}

    {(documentType === "INVOICE" || documentType === "REMITTANCE_ADVICE") ? <section className="border-t py-6" aria-labelledby="payment-heading"><h2 id="payment-heading" className="text-base font-semibold">Payment</h2><div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Field label="Payment method"><BusinessCodeSelect value={values.paymentMeansCode} options={PAYMENT_MEANS} emptyLabel="Choose a payment method" onChange={(value) => change("paymentMeansCode", value)} /></Field><Field label="Due date"><Input type="date" value={values.paymentDueDate} onChange={(event) => change("paymentDueDate", event.target.value)} /></Field><Field label="Account identifier"><Input value={values.paymentAccountId} onChange={(event) => change("paymentAccountId", event.target.value)} /></Field><Field label="Payment terms"><Input value={values.paymentTerms} onChange={(event) => change("paymentTerms", event.target.value)} /></Field></div></section> : null}

    {commercialDocument ? <details className="group border-t py-5"><summary className="flex cursor-pointer list-none items-center gap-3 text-sm"><span className="font-semibold">Tax and adjustments</span><span className="text-muted-foreground">Optional</span><ChevronDown className="ml-auto h-4 w-4 text-muted-foreground transition-transform duration-200 group-open:rotate-180" aria-hidden="true" /></summary><div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Field label="Tax treatment"><BusinessCodeSelect value={values.taxCategory} options={TAX_CATEGORIES} onChange={(value) => change("taxCategory", value)} /></Field><Field label="Tax percentage"><Input type="number" min="0" max="100" step="any" value={values.taxPercent} onChange={(event) => change("taxPercent", event.target.value)} /></Field><Field label="Tax amount"><Input type="number" min="0" step="0.01" value={values.taxAmount} onChange={(event) => change("taxAmount", event.target.value)} /></Field><Field label="Allowance amount"><Input type="number" min="0" step="0.01" value={values.allowanceAmount} onChange={(event) => change("allowanceAmount", event.target.value)} /></Field><Field label="Allowance reason"><Input value={values.allowanceReason} onChange={(event) => change("allowanceReason", event.target.value)} /></Field></div></details> : null}

    {(documentType === "DESPATCH_ADVICE" || documentType === "RECEIPT_ADVICE") ? <details className="group border-t py-5"><summary className="flex cursor-pointer list-none items-center gap-3 text-sm"><span className="font-semibold">Item traceability</span><span className="text-muted-foreground">Lots, expiry dates and serial numbers</span><ChevronDown className="ml-auto h-4 w-4 text-muted-foreground transition-transform duration-200 group-open:rotate-180" aria-hidden="true" /></summary><div className="mt-4 divide-y border-y">{lines.map((line, index) => <fieldset key={line.id} className="grid gap-3 py-4 sm:grid-cols-2 lg:grid-cols-4"><legend className="sr-only">Traceability for line {line.id}</legend><Field label={`${line.id} lot number`}><Input value={line.lotNumber ?? ""} onChange={(event) => onLineChange(index, "lotNumber", event.target.value)} /></Field><Field label="Expiry date"><Input type="date" value={line.expiryDate ?? ""} onChange={(event) => onLineChange(index, "expiryDate", event.target.value)} /></Field><Field label="Serial numbers" hint="Comma-separated"><Input value={line.serialNumbers ?? ""} onChange={(event) => onLineChange(index, "serialNumbers", event.target.value)} /></Field>{documentType === "RECEIPT_ADVICE" ? <><Field label="Rejected quantity"><Input type="number" min="0" step="any" value={line.rejectedQuantity ?? ""} onChange={(event) => onLineChange(index, "rejectedQuantity", event.target.value)} /></Field><Field label="Rejection reason"><Input value={line.rejectionReason ?? ""} onChange={(event) => onLineChange(index, "rejectionReason", event.target.value)} /></Field></> : null}</fieldset>)}</div></details> : null}
  </>
}

import type { ReadinessIssue } from "@edispark/integration-client"
import { documentFieldLabel } from "./document-field-label"

const labels: Record<string, string> = {
  "/CustomerAssignedAccountID/0/_": "B&Q vendor number",
  "/DocumentReference/0/VersionID/0/_": "Purchase order version",
  "/DeliveryLocation/0/ID/0/_": "Sales order / store reference",
  "/PromisedDeliveryPeriod/0/StartDate/0/_": "Promised delivery date",
  "/Item/0/StandardItemIdentification/0/ID/0/_": "Item EAN / GTIN",
  "/Quantity/0/unitCode": "Quantity unit", "/Quantity/0/_": "Quantity",
  "/LineStatusCode/0/_": "Line decision", "/Item/0/Name/0/_": "Item name",
  "/Item/0/SellersItemIdentification/0/ID/0/_": "Supplier item reference",
  "/OrderResponseCode/0/_": "Overall response", "/UBLVersionID/0/_": "UBL version",
}

export function groupDocumentFindings(findings: ReadinessIssue[]) {
  const groups = new Map<string, { key: string; label: string; message?: string; action?: string; lines: number[]; locations: ReadinessIssue[] }>()
  for (const finding of findings) {
    const path = finding.path ?? "/"
    const family = path.replace(/\/(OrderLine|InvoiceLine|DespatchLine|ReceiptLine|RemittanceAdviceLine|lines)\/\d+/g, "/$1/*")
    const key = JSON.stringify([finding.code, finding.owner, finding.severity, family, finding.message, finding.action])
    const label = Object.entries(labels).find(([suffix]) => family.endsWith(suffix))?.[1]
      ?? documentFieldLabel(family).replace(/ › \* › /g, " › ")
    const group = groups.get(key) ?? { key, label, message: finding.message, action: finding.action, lines: [], locations: [] }
    const line = path.match(/\/(?:OrderLine|InvoiceLine|DespatchLine|ReceiptLine|RemittanceAdviceLine|lines)\/(\d+)/)?.[1]
    if (line !== undefined && !group.lines.includes(Number(line) + 1)) group.lines.push(Number(line) + 1)
    group.locations.push(finding)
    groups.set(key, group)
  }
  return Array.from(groups.values()).map((group) => ({ ...group, lines: group.lines.sort((a, b) => a - b) }))
}

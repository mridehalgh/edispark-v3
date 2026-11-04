import { createAuthenticatedDocumentsClient, documentsErrorResponse, isUuid } from "@/lib/documents-server"
import { createAuthenticatedIntegrationClient } from "@/lib/integration-server"
import { authoringCatalog } from "@/lib/ubl-authoring-catalog"
import type { AuthorableDocumentType } from "@/lib/ubl-authoring"

function objectValue(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : undefined
}

function first(value: unknown): Record<string, unknown> | undefined {
  return Array.isArray(value) ? objectValue(value[0]) : undefined
}

function scalar(value: unknown): string | undefined {
  const item = first(value)
  const raw = item?._
  return typeof raw === "string" || typeof raw === "number" || typeof raw === "boolean" ? String(raw) : undefined
}

function valuesForField(content: Record<string, unknown>, documentType: AuthorableDocumentType, fieldKey: string) {
  const rootName = ({ ORDER: "Order", ORDER_RESPONSE: "OrderResponse", DESPATCH_ADVICE: "DespatchAdvice", INVOICE: "Invoice", REMITTANCE_ADVICE: "RemittanceAdvice", RECEIPT_ADVICE: "ReceiptAdvice" } as const)[documentType]
  const root = first(content[rootName])
  if (!root) return []
  const catalogField = authoringCatalog[documentType].find((field) => field.fieldKey === fieldKey)
  if (catalogField) return [scalar(root[catalogField.ublKey])].filter((value): value is string => value !== undefined)
  if (fieldKey === "despatch.shipment.id") return [scalar(first(root.Shipment)?.ID)].filter((value): value is string => value !== undefined)
  if (fieldKey === "despatch.shipment.handlingUnits[].sscc") {
    const units = first(root.Shipment)?.TransportHandlingUnit
    return Array.isArray(units) ? units.map((unit) => scalar(objectValue(unit)?.TraceID)).filter((value): value is string => value !== undefined) : []
  }
  if (fieldKey === "invoice.payment.meansCode") return [scalar(first(root.PaymentMeans)?.PaymentMeansCode)].filter((value): value is string => value !== undefined)
  return []
}

interface RouteContext {
  params: { setId: string; documentId: string }
}

export async function POST(request: Request, { params }: RouteContext) {
  if (!isUuid(params.setId) || !isUuid(params.documentId)) {
    return Response.json({ error: { code: "INVALID_DOCUMENT_ID", message: "The document identifier is invalid." } }, { status: 400 })
  }
  try {
    const client = await createAuthenticatedDocumentsClient(request)
    const { data: documentSet } = await client.getDocumentSet({ path: { id: params.setId }, throwOnError: true })
    let profileId = documentSet.metadata?.documentProfileRevisionId
    const partnerId = documentSet.metadata?.partnerId
    if (partnerId) {
      const document = await client.getDocument({ path: { setId: params.setId, docId: params.documentId }, throwOnError: true })
      const versionNumber = document.data.currentVersion?.versionNumber
      if (!versionNumber || document.data.currentVersion?.format !== "JSON") {
        return Response.json({ error: { code: "PROFILE_VALIDATION_UNAVAILABLE", message: "Partner validation requires a current UBL JSON version." } }, { status: 422 })
      }
      const version = await client.getDocumentVersionContent({ path: { setId: params.setId, docId: params.documentId, versionNumber }, throwOnError: true })
      const content = JSON.parse(await version.data.text()) as Record<string, unknown>
      const integration = await createAuthenticatedIntegrationClient(request, "member")
      const profiles = await integration.listPartnerDocumentProfiles({ path: { partnerId }, throwOnError: true })
      const documentType = document.data.type as AuthorableDocumentType
      let profile = profiles.data.find((item) => item.id === profileId && item.status === "PUBLISHED" && item.documentType === documentType)
      if (!profile) {
        const agreements = await integration.listTradingAgreements({ throwOnError: true })
        const capability = agreements.data.filter((item) => item.partnerId === partnerId && item.status === "ACTIVE")
          .flatMap((item) => item.capabilities ?? []).find((item) => item.direction === "OUTBOUND" && item.documentType === documentType)
        profileId = capability?.documentProfileRevisionId
        profile = profiles.data.find((item) => item.id === profileId && item.status === "PUBLISHED")
      }
      if (!profileId) {
        const { data } = await client.finalizeDocument({ path: { setId: params.setId, docId: params.documentId }, throwOnError: true })
        return Response.json(data)
      }
      if (!profile) return Response.json({ error: { code: "DOCUMENT_PROFILE_UNAVAILABLE", message: "The pinned partner profile is no longer available." } }, { status: 409 })
      const violations = (profile.fieldRules ?? []).flatMap((rule) => {
        const values = valuesForField(content, documentType, rule.fieldKey ?? "")
        if (rule.requirement === "REQUIRED" && values.length === 0) return [rule.message || `${rule.fieldKey} is required by ${profile.name}.`]
        if (rule.fixedValue !== undefined && values.some((value) => value !== rule.fixedValue)) return [rule.message || `${rule.fieldKey} must have the value required by ${profile.name}.`]
        if (rule.allowedValues?.length && values.some((value) => !rule.allowedValues?.includes(value))) return [rule.message || `${rule.fieldKey} contains a value not allowed by ${profile.name}.`]
        if (rule.pattern && values.some((value) => !new RegExp(rule.pattern!).test(value))) return [rule.message || `${rule.fieldKey} does not match the required format.`]
        const numbers = values.map(Number)
        if (rule.minimum !== undefined && numbers.some((value) => !Number.isFinite(value) || value < rule.minimum!)) return [rule.message || `${rule.fieldKey} is below the minimum required by ${profile.name}.`]
        if (rule.maximum !== undefined && numbers.some((value) => !Number.isFinite(value) || value > rule.maximum!)) return [rule.message || `${rule.fieldKey} is above the maximum allowed by ${profile.name}.`]
        return []
      })
      if (violations.length) return Response.json({ error: { code: "PARTNER_PROFILE_INVALID", message: violations.join(" "), details: { violations } } }, { status: 422 })
    }
    const { data } = await client.finalizeDocument({
      path: { setId: params.setId, docId: params.documentId },
      throwOnError: true,
    })
    return Response.json(data)
  } catch (error) {
    return documentsErrorResponse(error)
  }
}

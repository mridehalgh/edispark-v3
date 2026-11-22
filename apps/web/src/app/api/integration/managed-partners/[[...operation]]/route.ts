import type { AcceptanceRequest, Binding, Publish, Request as SetupRequest } from "@edispark/integration-client"

import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId, managedPartnerBody } from "@/lib/integration-server"

export const dynamic = "force-dynamic"
type Context = { params: Promise<{ operation?: string[] }> }

async function handle(request: Request, { params }: Context) {
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const parts = (await params).operation ?? []
    const [first, second, third, fourth, fifth] = parts
    if (request.method === "GET" && !parts.length) {
      const { data } = await client.listPublishedManagedPartners({ throwOnError: true })
      return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
    }
    if (first === "reference-tables") {
      if (request.method === "GET" && parts.length === 1) {
        const { data } = await client.listPartnerReferenceTables({ throwOnError: true })
        return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
      }
      if (request.method === "POST" && parts.length === 1) {
        const { data } = await client.publishPartnerReferenceTable({
          body: await managedPartnerBody<Publish>(request), throwOnError: true,
        })
        return Response.json(data, { status: 201 })
      }
      if (request.method === "PUT" && parts.length === 5 && second === "agreements"
        && third && isIntegrationResourceId(third) && fourth === "capabilities" && fifth && isIntegrationResourceId(fifth)) {
        const { data } = await client.bindPartnerReferenceTable({
          path: { agreementId: third, capabilityId: fifth },
          body: await managedPartnerBody<Binding>(request), throwOnError: true,
        })
        return Response.json(data)
      }
    }
    if (first === "agreements" && second && isIntegrationResourceId(second)) {
      const path = { agreementId: second }
      if (request.method === "GET" && parts.length === 2) {
        const { data } = await client.getManagedAgreementSetup({ path, throwOnError: true })
        return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
      }
      if (request.method === "POST" && parts.length === 3 && third === "acceptance") {
        const { data } = await client.recordManagedRetailerAcceptance({ path, body: await managedPartnerBody<AcceptanceRequest>(request), throwOnError: true })
        return Response.json(data)
      }
    }
    if (request.method === "POST" && parts.length === 4 && first && isIntegrationResourceId(first)
      && second === "revisions" && third && /^[1-9][0-9]{0,8}$/.test(third) && fourth === "apply") {
      const path = { id: first, revision: Number(third) }
      const { data } = await client.applyManagedPartnerTemplate({ path, body: await managedPartnerBody<SetupRequest>(request), throwOnError: true })
      return Response.json(data, { status: 201 })
    }
    return new Response(null, { status: 404 })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export { handle as GET, handle as POST, handle as PUT }

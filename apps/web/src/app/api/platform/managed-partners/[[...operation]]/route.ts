import type { Definition, Edit, Revision } from "@edispark/integration-client"

import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId, managedPartnerBody } from "@/lib/integration-server"

export const dynamic = "force-dynamic"
type Context = { params: Promise<{ operation?: string[] }> }

async function handle(request: Request, { params }: Context) {
  try {
    const client = await createAuthenticatedIntegrationClient(request, "platform")
    const parts = (await params).operation ?? []
    const [id, action] = parts
    if (request.method === "GET" && parts.length <= 1) {
      if (id && id !== "mappings") return new Response(null, { status: 404 })
      const { data } = id
        ? await client.listManagedPartnerMappings({ throwOnError: true })
        : await client.listManagedPartnerRevisions({ throwOnError: true })
      return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
    }
    if (request.method === "POST" && !parts.length) {
      const { data } = await client.createManagedPartnerTemplate({ body: await managedPartnerBody<Definition>(request), throwOnError: true })
      return Response.json(data, { status: 201 })
    }
    if (!id || !isIntegrationResourceId(id)) return new Response(null, { status: 404 })
    const path = { id }
    if (request.method === "PUT" && parts.length === 1) {
      const { data } = await client.editManagedPartnerTemplate({ path, body: await managedPartnerBody<Edit>(request), throwOnError: true })
      return Response.json(data)
    }
    if (request.method === "POST" && parts.length === 2 && (action === "publish" || action === "retire")) {
      const options = { path, body: await managedPartnerBody<Revision>(request), throwOnError: true as const }
      const { data } = action === "publish"
        ? await client.publishManagedPartnerTemplate(options)
        : await client.retireManagedPartnerTemplate(options)
      return Response.json(data)
    }
    return new Response(null, { status: 404 })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export { handle as GET, handle as POST, handle as PUT }

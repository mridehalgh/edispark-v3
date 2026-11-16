import type { DocumentProfileUpdateRequest } from "@edispark/integration-client"

import { createAuthenticatedIntegrationClient, integrationErrorResponse } from "@/lib/integration-server"

const ACTIONS = new Set(["draft", "publish", "revisions", "archive"])

export async function PUT(request: Request, { params: pendingParams }: { params: Promise<{ profileId: string; action: string }> }) {
  const params = await pendingParams
  if (params.action !== "draft") return Response.json({ error: { message: "Unsupported action." } }, { status: 404 })
  try {
    const body = await request.json() as DocumentProfileUpdateRequest
    const client = await createAuthenticatedIntegrationClient(request)
    const { data } = await client.updatePartnerDocumentProfileDraft({ path: { profileId: params.profileId }, body, throwOnError: true })
    return Response.json(data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

export async function POST(request: Request, { params: pendingParams }: { params: Promise<{ profileId: string; action: string }> }) {
  const params = await pendingParams
  if (!ACTIONS.has(params.action) || params.action === "draft") return Response.json({ error: { message: "Unsupported action." } }, { status: 404 })
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const path = { profileId: params.profileId }
    const result = params.action === "publish"
      ? await client.publishPartnerDocumentProfile({ path, throwOnError: true })
      : params.action === "revisions"
        ? await client.createPartnerDocumentProfileRevision({ path, throwOnError: true })
        : await client.archivePartnerDocumentProfile({ path, throwOnError: true })
    return Response.json(result.data, { status: params.action === "revisions" ? 201 : 200 })
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

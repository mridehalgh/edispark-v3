import { createAuthenticatedIntegrationClient, integrationErrorResponse, isIntegrationResourceId } from "@/lib/integration-server"

const ACTIONS = new Set(["activate", "retire", "default"])

export async function POST(request: Request, { params: pendingParams }: { params: Promise<{ issuerId: string; action: string }> }) {
  const params = await pendingParams
  if (!isIntegrationResourceId(params.issuerId) || !ACTIONS.has(params.action)) {
    return Response.json({ error: { message: "Unsupported SSCC issuer action." } }, { status: 404 })
  }
  try {
    const client = await createAuthenticatedIntegrationClient(request)
    const path = { issuerId: params.issuerId }
    const result = params.action === "activate"
      ? await client.activateSsccIssuer({ path, throwOnError: true })
      : params.action === "retire"
        ? await client.retireSsccIssuer({ path, throwOnError: true })
        : await client.makeDefaultSsccIssuer({ path, throwOnError: true })
    return Response.json(result.data)
  } catch (error) {
    return integrationErrorResponse(error)
  }
}

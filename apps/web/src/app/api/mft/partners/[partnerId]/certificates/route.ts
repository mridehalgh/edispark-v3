import type { RotatePartnerCertificatesRequest } from "@edispark/mft-client"

import { createAuthenticatedMftClient, isMftResourceId, mftErrorResponse } from "@/lib/mft-server"

interface RouteContext { params: { partnerId: string } }

export const dynamic = "force-dynamic"

export async function POST(request: Request, { params }: RouteContext) {
  if (!isMftResourceId(params.partnerId)) {
    return Response.json({ error: { code: "INVALID_PARTNER", message: "The trading partner identifier is invalid." } }, { status: 400 })
  }
  try {
    const body = await request.json() as RotatePartnerCertificatesRequest
    const client = await createAuthenticatedMftClient(request)
    const { data } = await client.rotateMftPartnerCertificates({
      path: { partnerId: params.partnerId }, body, throwOnError: true,
    })
    return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

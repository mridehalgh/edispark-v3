import type { CreatePartnerRequest } from "@edispark/mft-client"

import { createAuthenticatedMftClient, mftErrorResponse } from "@/lib/mft-server"

export const dynamic = "force-dynamic"

export async function GET(request: Request) {
  try {
    const client = await createAuthenticatedMftClient(request)
    const { data } = await client.listMftPartners({ throwOnError: true })
    return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

export async function POST(request: Request) {
  try {
    const body = await request.json() as CreatePartnerRequest
    const client = await createAuthenticatedMftClient(request)
    const { data } = await client.createMftPartner({ body, throwOnError: true })
    return Response.json(data, { status: 201 })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

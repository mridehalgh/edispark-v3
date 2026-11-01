import { createAuthenticatedMftClient, mftErrorResponse } from "@/lib/mft-server"

export const dynamic = "force-dynamic"

export async function GET(request: Request) {
  try {
    const client = await createAuthenticatedMftClient(request)
    const { data } = await client.listMftMdnDeliveries({ throwOnError: true })
    return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

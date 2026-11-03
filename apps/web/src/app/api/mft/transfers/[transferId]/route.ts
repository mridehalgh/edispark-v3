import { createAuthenticatedMftClient, isMftResourceId, mftErrorResponse } from "@/lib/mft-server"

export const dynamic = "force-dynamic"

export async function GET(request: Request, { params }: { params: { transferId: string } }) {
  if (!isMftResourceId(params.transferId)) {
    return Response.json({ error: { code: "INVALID_TRANSFER", message: "The file identifier is invalid." } }, { status: 400 })
  }
  try {
    const client = await createAuthenticatedMftClient(request)
    const { data } = await client.getMftTransfer({ path: { transferId: params.transferId }, throwOnError: true })
    return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

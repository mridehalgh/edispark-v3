import { createAuthenticatedMftClient, isMftResourceId, mftErrorResponse } from "@/lib/mft-server"

export async function POST(request: Request, { params }: { params: { transferId: string } }) {
  if (!isMftResourceId(params.transferId)) {
    return Response.json({ error: { code: "INVALID_TRANSFER", message: "The file identifier is invalid." } }, { status: 400 })
  }
  try {
    const client = await createAuthenticatedMftClient(request)
    const { data } = await client.resendMftTransfer({ path: { transferId: params.transferId }, throwOnError: true })
    return Response.json(data, { status: 202 })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

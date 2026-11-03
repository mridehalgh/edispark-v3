import { createAuthenticatedMftClient, isMftResourceId, mftErrorResponse } from "@/lib/mft-server"

export const dynamic = "force-dynamic"

export async function GET(request: Request, { params }: { params: { transferId: string } }) {
  if (!isMftResourceId(params.transferId)) {
    return Response.json({ error: { code: "INVALID_TRANSFER", message: "The file identifier is invalid." } }, { status: 400 })
  }
  try {
    const client = await createAuthenticatedMftClient(request)
    const [{ data: payload }, { data: transfer }] = await Promise.all([
      client.downloadMftTransferPayload({ path: { transferId: params.transferId }, throwOnError: true }),
      client.getMftTransfer({ path: { transferId: params.transferId }, throwOnError: true }),
    ])
    const filename = (transfer.filename ?? "download.bin").replace(/[\r\n"]/g, "_")
    return new Response(payload, {
      headers: {
        "Cache-Control": "private, no-store",
        "Content-Type": transfer.contentType ?? "application/octet-stream",
        "Content-Disposition": `attachment; filename="${filename}"`,
        "X-Content-Type-Options": "nosniff",
      },
    })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

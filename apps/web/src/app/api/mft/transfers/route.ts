import { createAuthenticatedMftClient, mftErrorResponse } from "@/lib/mft-server"

export const dynamic = "force-dynamic"

export async function GET(request: Request) {
  try {
    const client = await createAuthenticatedMftClient(request)
    const { data } = await client.listMftTransfers({ throwOnError: true })
    return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

export async function POST(request: Request) {
  try {
    const form = await request.formData()
    const file = form.get("file")
    const partnerId = form.get("partnerId")
    if (!(file instanceof File) || typeof partnerId !== "string") {
      return Response.json({ error: { code: "INVALID_TRANSFER", message: "Choose a partner and a file." } }, { status: 400 })
    }
    const client = await createAuthenticatedMftClient(request)
    const { data } = await client.submitMftTransfer({
      body: { file },
      query: { partnerId },
      headers: { "Idempotency-Key": request.headers.get("idempotency-key") ?? crypto.randomUUID() },
      throwOnError: true,
    })
    return Response.json(data, { status: 202 })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

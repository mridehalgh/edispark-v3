import { createAuthenticatedMftClient, mftErrorResponse } from "@/lib/mft-server"

export const dynamic = "force-dynamic"

export async function GET(request: Request) {
  try {
    const client = await createAuthenticatedMftClient(request)
    const { data } = await client.listMftCertificates({ throwOnError: true })
    return Response.json(data, { headers: { "Cache-Control": "private, no-store" } })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

export async function POST(request: Request) {
  try {
    const form = await request.formData()
    const file = form.get("file")
    const name = form.get("name")
    const kind = form.get("kind")
    const password = form.get("password")
    if (!(file instanceof File) || typeof name !== "string" || (kind !== "LOCAL_IDENTITY" && kind !== "PARTNER_PUBLIC")) {
      return Response.json({ error: { code: "INVALID_CERTIFICATE", message: "Certificate name, kind, and file are required." } }, { status: 400 })
    }
    const client = await createAuthenticatedMftClient(request)
    const { data } = await client.uploadMftCertificate({
      body: { file },
      query: { name, kind, password: typeof password === "string" && password ? password : undefined },
      throwOnError: true,
    })
    return Response.json(data, { status: 201 })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

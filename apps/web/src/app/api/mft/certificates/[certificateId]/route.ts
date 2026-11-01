import { createAuthenticatedMftClient, isMftResourceId, mftErrorResponse } from '@/lib/mft-server'
export async function GET(request: Request, { params }: { params: { certificateId: string } }) {
  if (!isMftResourceId(params.certificateId)) return Response.json({ error: { code: 'INVALID_CERTIFICATE', message: 'The certificate identifier is invalid.' } }, { status: 400 })
  try { const client = await createAuthenticatedMftClient(request); const { data } = await client.getMftCertificateUsage({ path: { certificateId: params.certificateId }, throwOnError: true }); return Response.json(data) }
  catch (error) { return mftErrorResponse(error) }
}
export async function DELETE(request: Request, { params }: { params: { certificateId: string } }) {
  if (!isMftResourceId(params.certificateId)) return Response.json({ error: { code: 'INVALID_CERTIFICATE', message: 'The certificate identifier is invalid.' } }, { status: 400 })
  try { const client = await createAuthenticatedMftClient(request); await client.deleteUnusedMftCertificate({ path: { certificateId: params.certificateId }, throwOnError: true }); return new Response(null, { status: 204 }) }
  catch (error) { return mftErrorResponse(error) }
}

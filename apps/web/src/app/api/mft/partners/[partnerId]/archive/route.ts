import { createAuthenticatedMftClient, isMftResourceId, mftErrorResponse } from '@/lib/mft-server'
export async function POST(request: Request, { params }: { params: { partnerId: string } }) {
  if (!isMftResourceId(params.partnerId)) return Response.json({ error: { code: 'INVALID_PARTNER', message: 'The partner identifier is invalid.' } }, { status: 400 })
  try { const client = await createAuthenticatedMftClient(request); const { data } = await client.archiveMftPartnerRevision({ path: { partnerId: params.partnerId }, throwOnError: true }); return Response.json(data) }
  catch (error) { return mftErrorResponse(error) }
}

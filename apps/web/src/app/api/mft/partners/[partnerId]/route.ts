import type { CreatePartnerRequest } from '@edispark/mft-client'
import { createAuthenticatedMftClient, isMftResourceId, mftErrorResponse } from '@/lib/mft-server'

export async function GET(request: Request, { params }: { params: { partnerId: string } }) {
  if (!isMftResourceId(params.partnerId)) return Response.json({ error: { code: 'INVALID_PARTNER', message: 'The partner identifier is invalid.' } }, { status: 400 })
  try { const client = await createAuthenticatedMftClient(request); const { data } = await client.getMftPartnerUsage({ path: { partnerId: params.partnerId }, throwOnError: true }); return Response.json(data) }
  catch (error) { return mftErrorResponse(error) }
}

export async function DELETE(request: Request, { params }: { params: { partnerId: string } }) {
  if (!isMftResourceId(params.partnerId)) return Response.json({ error: { code: 'INVALID_PARTNER', message: 'The partner identifier is invalid.' } }, { status: 400 })
  try { const client = await createAuthenticatedMftClient(request); await client.deleteMftPartnerDraft({ path: { partnerId: params.partnerId }, throwOnError: true }); return new Response(null, { status: 204 }) }
  catch (error) { return mftErrorResponse(error) }
}

export async function PUT(request: Request, { params }: { params: { partnerId: string } }) {
  if (!isMftResourceId(params.partnerId)) return Response.json({ error: { code: 'INVALID_PARTNER', message: 'The partner identifier is invalid.' } }, { status: 400 })
  try { const client = await createAuthenticatedMftClient(request); const { data } = await client.updateMftPartnerDraft({ path: { partnerId: params.partnerId }, body: await request.json() as CreatePartnerRequest, throwOnError: true }); return Response.json(data) }
  catch (error) { return mftErrorResponse(error) }
}

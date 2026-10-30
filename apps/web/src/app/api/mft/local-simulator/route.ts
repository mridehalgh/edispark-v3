import { MftBffError, createAuthenticatedMftClient, mftErrorResponse } from "@/lib/mft-server"
import {
  isLocalDevelopmentEnabled,
  isLocalSimulationDirection,
  localMftSample,
} from "@/lib/local-mft-simulator"

export const dynamic = "force-dynamic"

const DEFAULT_SIMULATOR_URL = "http://localhost:8082"
const REQUEST_TIMEOUT_MS = 15_000

export async function GET(request: Request) {
  if (!isLocalDevelopmentEnabled(process.env.NODE_ENV)) return new Response(null, { status: 404 })

  try {
    await simulatorPartner(request)
    const messages = await simulatorRequest("/api/simulator/messages")
    return Response.json({ messages }, { headers: { "Cache-Control": "private, no-store" } })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

export async function POST(request: Request) {
  if (!isLocalDevelopmentEnabled(process.env.NODE_ENV)) return new Response(null, { status: 404 })

  try {
    const body = await request.json().catch(() => undefined)
    if (!isLocalSimulationDirection(body?.direction)) {
      return Response.json(
        { error: { code: "INVALID_SIMULATION", message: "Choose an inbound or outbound simulation." } },
        { status: 400 },
      )
    }

    const { client, partnerId } = await simulatorPartner(request)
    const sample = localMftSample(body.direction)
    if (body.direction === "OUTBOUND") {
      const { data } = await client.submitMftTransfer({
        body: { file: new File([sample.content], sample.filename, { type: sample.contentType }) },
        query: { partnerId },
        headers: { "Idempotency-Key": `local-simulator:${crypto.randomUUID()}` },
        throwOnError: true,
      })
      return Response.json({ direction: body.direction, filename: sample.filename, transfer: data }, { status: 202 })
    }

    const form = new FormData()
    form.set("file", new File([sample.content], sample.filename, { type: sample.contentType }))
    const result = await simulatorRequest("/api/simulator/send", { method: "POST", body: form })
    return Response.json({ direction: body.direction, filename: sample.filename, result }, { status: 201 })
  } catch (error) {
    return mftErrorResponse(error)
  }
}

async function simulatorPartner(request: Request) {
  const client = await createAuthenticatedMftClient(request)
  const { data: partners } = await client.listMftPartners({ throwOnError: true })
  const partner = partners.find(({ localAs2Id, remoteAs2Id }) => (
    localAs2Id === "EDISPARK" && remoteAs2Id === "SIMULATOR"
  ))
  if (!partner?.id) {
    throw new MftBffError(409, "LOCAL_SIMULATOR_NOT_READY", "The local simulator partner is not ready yet.")
  }
  return { client, partnerId: partner.id }
}

async function simulatorRequest(path: string, init?: RequestInit) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  try {
    const baseUrl = (process.env.AS2_SIMULATOR_URL ?? DEFAULT_SIMULATOR_URL).replace(/\/+$/, "")
    const response = await fetch(`${baseUrl}${path}`, { ...init, cache: "no-store", signal: controller.signal })
    if (!response.ok) throw new Error(`Simulator returned ${response.status}`)
    return await response.json()
  } catch (error) {
    console.error("Local AS2 simulator request failed", error)
    throw new MftBffError(
      502,
      "LOCAL_SIMULATOR_UNAVAILABLE",
      "The local AS2 simulator is unavailable. Start the complete stack with `pnpm dev`.",
    )
  } finally {
    clearTimeout(timeout)
  }
}

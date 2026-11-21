import { agentBase, agentIdentity } from "@/lib/agent-server"

export async function GET(request: Request) {
  const actor = await agentIdentity(request)
  if (!actor) return new Response(null, { status: 401 })
  const response = await fetch(agentBase + "/usage", {
    headers: { Authorization: "Bearer " + actor.token }, cache: "no-store", signal: request.signal,
  })
  return new Response(response.ok ? response.body : null, { status: response.status, headers: {
    "Content-Type": "application/json", "Cache-Control": "private, no-store",
  } })
}

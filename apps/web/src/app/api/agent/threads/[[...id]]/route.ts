import { agentBase, agentIdentity } from "@/lib/agent-server"

export async function GET(request: Request, context: { params: Promise<{ id?: string[] }> }) {
  const actor = await agentIdentity(request)
  if (!actor) return new Response(null, { status: 401 })
  const { id } = await context.params
  if (id && (id.length !== 1 || !/^[a-f0-9-]{36}$/i.test(id[0]))) return new Response(null, { status: 400 })
  const response = await fetch(agentBase + "/threads" + (id ? "/" + id[0] : ""), {
    headers: { Authorization: "Bearer " + actor.token }, cache: "no-store", signal: request.signal,
  })
  return new Response(response.ok ? response.body : null, { status: response.status, headers: {
    "Content-Type": "application/json", "Cache-Control": "private, no-store",
  } })
}

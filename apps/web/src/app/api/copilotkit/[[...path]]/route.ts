import { HttpAgent } from "@ag-ui/client"
import { CopilotRuntime, createCopilotHonoHandler } from "@copilotkit/runtime/v2"
import { agentBase, agentIdentity } from "@/lib/agent-server"

const identities = new WeakMap<Request, ReturnType<typeof agentIdentity>>()
function identity(request: Request) {
  const pending = identities.get(request) ?? agentIdentity(request)
  identities.set(request, pending)
  return pending
}

const runtime = new CopilotRuntime({
  agents: async ({ request }) => {
    const actor = await identity(request)
    if (!actor) throw Error("Sign in to continue.")
    return { default: new HttpAgent({ url: agentBase + "/run", headers: { Authorization: "Bearer " + actor.token } }) }
  },
  identifyUser: async request => {
    const actor = await identity(request)
    if (!actor) throw Error("Sign in to continue.")
    return { id: actor.id, name: "Workspace user" }
  },
})
const handler = createCopilotHonoHandler({ runtime, basePath: "/api/copilotkit" })

async function handle(request: Request) {
  if (request.method === "POST" && request.headers.get("origin") !== new URL(process.env.BETTER_AUTH_URL ?? request.url).origin) {
    return new Response(null, { status: 403 })
  }
  if (!await identity(request)) return new Response(null, { status: 401 })
  // Saved conversations are served by Spring, which checks current record access.
  const path = new URL(request.url).pathname.slice("/api/copilotkit".length)
  if (request.method === "GET" && path === "/threads") {
    return Response.json({ threads: [], nextCursor: null }, { headers: { "Cache-Control": "no-store" } })
  }
  if (path !== "/info" && path !== "/agent/default/run" && !/^\/agent\/default\/stop\/[a-f0-9-]{36}$/.test(path)) {
    return new Response(null, { status: 404 })
  }
  return handler.fetch(request)
}
export { handle as GET, handle as POST }

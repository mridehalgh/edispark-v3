import { auth, authReady } from "@/lib/auth"
import { toNextJsHandler } from "better-auth/next-js"

const handler = async (request: Request) => {
  await authReady
  return auth.handler(request)
}

export const { GET, POST } = toNextJsHandler(handler)

import { auth, authReady } from "@/lib/auth"
import { toNextJsHandler } from "better-auth/next-js"
import { getPublicJwtKey, getRemoteJwtSigningConfig } from "@/lib/jwt-signing"

let publicKey: ReturnType<typeof getPublicJwtKey> | undefined

const handler = async (request: Request) => {
  const signing = getRemoteJwtSigningConfig()
  if (request.method === "GET" && new URL(request.url).pathname === "/api/auth/jwks" && signing) {
    publicKey ??= getPublicJwtKey(signing).catch(error => { publicKey = undefined; throw error })
    return Response.json({ keys: [await publicKey] }, { headers: { "Cache-Control": "public, max-age=300" } })
  }
  await authReady
  return auth.handler(request)
}

export const { GET, POST } = toNextJsHandler(handler)

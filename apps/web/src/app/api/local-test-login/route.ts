export async function POST(request: Request) {
  if (process.env.NODE_ENV !== "development" || process.env.LOCAL_TEST_USER_BOOTSTRAP !== "true") {
    return new Response(null, { status: 404 })
  }

  const { activateLocalTestWorkspace, auth, authReady } = await import("@/lib/auth")
  const email = process.env.LOCAL_TEST_USER_EMAIL
  const password = process.env.LOCAL_TEST_USER_PASSWORD
  if (!email || !password) return new Response(null, { status: 404 })

  await authReady
  const requestUrl = new URL(request.url)
  const response = await auth.handler(new Request(new URL("/api/auth/sign-in/email", requestUrl.origin), {
    method: "POST",
    headers: {
      "content-type": "application/json",
      origin: requestUrl.origin,
    },
    body: JSON.stringify({ email, password, rememberMe: false }),
  }))
  if (response.ok) activateLocalTestWorkspace()
  return response
}

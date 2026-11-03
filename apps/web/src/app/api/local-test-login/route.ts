export async function POST(request: Request) {
  if (process.env.NODE_ENV !== "development" || process.env.LOCAL_TEST_USER_BOOTSTRAP !== "true") {
    return new Response(null, { status: 404 })
  }

  const { activateLocalTestWorkspace, auth, authReady } = await import("@/lib/auth")
  const requested = await request.json().catch(() => ({})) as { persona?: string }
  const member = requested.persona === "member"
  const email = member ? process.env.LOCAL_MEMBER_USER_EMAIL : process.env.LOCAL_TEST_USER_EMAIL
  const password = member ? process.env.LOCAL_MEMBER_USER_PASSWORD : process.env.LOCAL_TEST_USER_PASSWORD
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
  if (response.ok) activateLocalTestWorkspace(email)
  return response
}

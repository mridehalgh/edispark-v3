const frontend = new URL(process.env.FRONTEND_URL)
const backend = new URL(process.env.BACKEND_URL)
if (frontend.protocol !== 'https:' || backend.protocol !== 'https:') throw new Error('Deployment probes require HTTPS')
for (const [origin, path, status] of [[frontend, '/api/auth/ok', 200], [backend, '/actuator/health/readiness', 200], [backend, '/api/document-sets', 401]]) {
  const result = await fetch(new URL(path, origin), { redirect: 'error', signal: AbortSignal.timeout(15_000) })
  if (result.status !== status) throw new Error(`Deployment probe failed: ${path} (${result.status})`)
}

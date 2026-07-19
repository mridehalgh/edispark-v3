"use client"

import { useState } from "react"
import { Link, useNavigate, useSearchParams } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { authClient } from "@/lib/auth-client"

export function InvitePage() {
  const [params] = useSearchParams(); const navigate = useNavigate(); const { data: session, isPending } = authClient.useSession(); const [error, setError] = useState<string | null>(null)
  const invitationId = params.get("id")
  async function accept() { if (!invitationId) return setError("This invitation link is invalid."); const result = await authClient.organization.acceptInvitation({ invitationId }); if (result.error) return setError(result.error.message ?? "Unable to accept invitation."); navigate("/", { replace: true }) }
  if (!isPending && !session) return <main className="grid min-h-screen place-items-center px-4"><Card className="w-full max-w-md"><CardHeader><CardTitle>Sign in to join</CardTitle><CardDescription>Use or create an EDI Spark account before accepting this invitation.</CardDescription></CardHeader><CardContent><Button asChild><Link to={`/sign-in?next=${encodeURIComponent(`/invite?id=${invitationId ?? ""}`)}`}>Sign in</Link></Button></CardContent></Card></main>
  return <main className="grid min-h-screen place-items-center px-4"><Card className="w-full max-w-md"><CardHeader><CardTitle>Join organization</CardTitle><CardDescription>Accept this invitation to access the shared workspace.</CardDescription></CardHeader><CardContent className="space-y-3">{error && <p role="alert" className="text-sm text-destructive">{error}</p>}<Button onClick={accept} disabled={!invitationId}>Accept invitation</Button></CardContent></Card></main>
}

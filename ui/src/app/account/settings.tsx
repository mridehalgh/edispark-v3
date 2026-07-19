import React, { FormEvent, useState } from "react"
import { LayoutBody } from "@/components/layout/layout"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { authClient } from "@/lib/auth-client"

export function AccountSettingsPage() {
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(null); setMessage(null)
    const form = new FormData(event.currentTarget)
    const result = await authClient.changePassword({ currentPassword: String(form.get("currentPassword")), newPassword: String(form.get("newPassword")), revokeOtherSessions: true })
    if (result.error) return setError(result.error.message ?? "Unable to change password.")
    event.currentTarget.reset(); setMessage("Password changed. Other sessions have been signed out.")
  }

  return (
    <LayoutBody className="mx-auto w-full max-w-2xl space-y-6 py-8">
      <header><h1 className="text-2xl font-semibold tracking-tight">Account settings</h1><p className="mt-1 text-sm text-muted-foreground">Manage your sign-in security.</p></header>
      <Card><CardHeader><CardTitle className="text-lg">Change password</CardTitle><CardDescription>Changing your password signs out your other active sessions.</CardDescription></CardHeader><CardContent>
        <form onSubmit={handleSubmit} className="max-w-md space-y-4">{error && <p role="alert" className="text-sm text-destructive">{error}</p>}{message && <p role="status" className="text-sm text-success-foreground">{message}</p>}
          <label className="grid gap-1.5 text-sm font-medium">Current password<Input required name="currentPassword" type="password" autoComplete="current-password" /></label>
          <label className="grid gap-1.5 text-sm font-medium">New password<Input required minLength={8} name="newPassword" type="password" autoComplete="new-password" /></label>
          <Button type="submit">Update password</Button>
        </form>
      </CardContent></Card>
    </LayoutBody>
  )
}

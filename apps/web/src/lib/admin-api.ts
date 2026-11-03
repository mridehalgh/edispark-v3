"use client"

export async function adminApi<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, cache: "no-store" })
  const contentType = response.headers.get("content-type") ?? ""
  const body = contentType.includes("application/json")
    ? await response.json().catch(() => undefined)
    : undefined
  if (!response.ok) {
    throw new Error(body?.error?.message ?? "The request could not be completed.")
  }
  return body as T
}

export function formatAdminDate(value?: string) {
  if (!value) return "Not recorded"
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value))
}

export function sentenceCase(value?: string) {
  if (!value) return "Unknown"
  const normalized = value.toLowerCase().replaceAll("_", " ")
  return normalized.charAt(0).toUpperCase() + normalized.slice(1)
}

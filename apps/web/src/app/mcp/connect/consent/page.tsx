import { notFound } from "next/navigation"
import { mcpEnabled } from "@/lib/auth"
import McpConnect from "../connect"

export const dynamic = "force-dynamic"

export default function McpConnectPage() {
  if (!mcpEnabled) notFound()
  return <McpConnect />
}


import type { Metadata } from "next"
import "./globals.css"
import { cn } from "@/lib/utils"
import "@xyflow/react/dist/style.css"

export const metadata: Metadata = {
  title: "EDI Spark",
  description: "Retailer EDI, without the traditional overhead.",
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
    <body className={cn(
        "min-h-screen bg-background font-sans antialiased"
    )}>
    {children}
    </body>
    </html>
  )
}

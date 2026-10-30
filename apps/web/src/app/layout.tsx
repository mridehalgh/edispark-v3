
import type { Metadata } from "next"
import "./globals.css";
import {cn} from "@/lib/utils";
import dynamic from "next/dynamic";
import React from "react";
import ReactRouterComponent from "@/app/router";
import '@xyflow/react/dist/style.css';

export const metadata: Metadata = {
  title: "EDI Spark",
  description: "Retailer EDI, without the traditional overhead.",
};

const NoSSRForAdminRouting  = dynamic(async () => ReactRouterComponent, { ssr: false });
const LocalDevelopmentDock = process.env.NODE_ENV === "development"
  ? dynamic(() => import("@/components/local-development/local-development-dock"), { ssr: false })
  : null

// @ts-ignore
function SafeHydrate() {
  return (
      <div suppressHydrationWarning>
          <NoSSRForAdminRouting />
      </div>
  )
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
    <SafeHydrate />
    {LocalDevelopmentDock && <LocalDevelopmentDock />}
    </body>
    </html>
  );
}

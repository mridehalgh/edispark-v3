
import type { Metadata } from "next"
import { Source_Sans_3 as FontSans } from "next/font/google"
import "./globals.css";
import {cn} from "@/lib/utils";
import dynamic from "next/dynamic";
import React from "react";
import ReactRouterComponent from "@/app/router";
import '@xyflow/react/dist/style.css';

// @ts-ignore
// @ts-ignore
const fontSans = FontSans({
  subsets: ["latin"],
  variable: "--font-sans",
})

export const metadata: Metadata = {
  title: "EDI Spark",
  description: "Retailer EDI, without the traditional overhead.",
};

const NoSSRForAdminRouting  = dynamic(async () => ReactRouterComponent, { ssr: false });

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
        "min-h-screen bg-background font-sans antialiased",
        fontSans.variable
    )}>
    <SafeHydrate />
    </body>
    </html>
  );
}

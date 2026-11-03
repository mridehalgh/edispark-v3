import { PHASE_DEVELOPMENT_SERVER } from "next/constants.js"

/** @type {(phase: string) => import('next').NextConfig} */
export default function nextConfig(phase) {
  return {
    // A production build must never replace chunks used by a running dev server.
    distDir: phase === PHASE_DEVELOPMENT_SERVER ? ".next-dev" : ".next",
    images: { unoptimized: true },
    async rewrites() {
      return [
        {
          source: "/:any((?!api(?:/|$)).*)",
          destination: "/",
        },
      ]
    },
  }
}

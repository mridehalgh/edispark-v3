/** @type {import('next').NextConfig} */
const nextConfig = {
  images: { unoptimized: true },
  async rewrites() {
    return [
      // Rewrite everything to `pages/index`
      {
        source: "/:any((?!api(?:/|$)).*)",
        destination: "/",
      },
    ];
  },
};

export default nextConfig;

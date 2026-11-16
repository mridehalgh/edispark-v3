import { build } from 'esbuild'
import { cp, mkdir } from 'node:fs/promises'
await mkdir('dist/migrations', { recursive: true })
await cp('apps/web/migrations', 'dist/migrations/migrations', { recursive: true })
await build({ entryPoints: ['apps/web/scripts/migrate-auth.mts'], outfile: 'dist/migrations/index.mjs', bundle: true, platform: 'node', target: 'node24', format: 'esm', banner: { js: "import { createRequire } from 'node:module'; const require = createRequire(import.meta.url);" } })

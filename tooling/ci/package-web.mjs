import { cp, mkdir, readdir, lstat, readFile, readlink, realpath } from 'node:fs/promises'
import { resolve, basename, dirname, relative, isAbsolute } from 'node:path'
import { execFileSync } from 'node:child_process'
import { build } from 'esbuild'

const output = resolve(process.argv[2] ?? 'dist/web')
await mkdir(output, { recursive: true })
// Next's traced output can include local .env files. Explicitly exclude them and SQLite state.
const allowed = path => !/^\.env(?:\.|$)/.test(basename(path)) && !/\.db(?:-|$)/.test(basename(path))
await cp('apps/web/.next/standalone', output, { recursive: true, filter: allowed, verbatimSymlinks: true })
await cp('apps/web/.next/static', `${output}/apps/web/.next/static`, { recursive: true })
await cp('apps/web/public', `${output}/apps/web/public`, { recursive: true })
await build({
  entryPoints: ['apps/web/runtime/start.mjs'], outfile: `${output}/index.mjs`,
  bundle: true, platform: 'node', target: 'node24', format: 'esm',
  external: ['./apps/web/server.js'],
  banner: { js: "import { createRequire } from 'node:module'; const require = createRequire(import.meta.url);" },
})
await execFileSync('node', ['--check', `${output}/index.mjs`])
// Fail if a trace ever reintroduces credentials or development state.
async function inspect(path) {
  for (const name of await readdir(path)) {
    const file = `${path}/${name}`
    if (!allowed(file)) throw new Error(`Forbidden runtime artifact: ${file}`)
    const stat = await lstat(file)
    if (stat.isSymbolicLink()) {
      const target = await readlink(file)
      const destination = await realpath(resolve(dirname(file), target))
      if (isAbsolute(target) || relative(output, destination).startsWith('..')) throw new Error(`Runtime link escapes the package: ${file}`)
    }
    if (stat.isDirectory()) await inspect(file)
  }
}
await inspect(output)
// Keep build metadata in the package for deployed release checks.
const pkg = JSON.parse(await readFile('apps/web/package.json', 'utf8'))
console.log(`Packaged ${pkg.name} for native Node 24 Web Functions`)

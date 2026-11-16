import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { mkdir, readFile, writeFile, chmod, copyFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const lock = JSON.parse(await readFile(new URL('./tools.json', import.meta.url), 'utf8'))
const destination = resolve(process.env.CI_TOOLS_DIR ?? '.ci-tools')
await mkdir(destination, { recursive: true })
for (const name of process.argv.slice(2)) {
  const tool = process.arch === 'arm64' ? lock[name]?.arm64 : lock[name]
  if (!tool || !/^[a-z-]+$/.test(name)) throw new Error(`Unpinned tool: ${name}`)
  const response = await fetch(tool.url, { signal: AbortSignal.timeout(120_000) })
  if (!response.ok) throw new Error(`Unable to download ${name}: ${response.status}`)
  const bytes = Buffer.from(await response.arrayBuffer())
  if (createHash('sha256').update(bytes).digest('hex') !== tool.sha256) throw new Error(`Invalid ${name} checksum`)
  const archive = resolve(destination, `${name}.download`)
  await writeFile(archive, bytes)
  const output = resolve(destination, name)
  if (tool.archive === 'binary') await copyFile(archive, output)
  else if (tool.archive === 'zip') execFileSync('unzip', ['-o', archive, name, '-d', destination])
  else {
    const entries = execFileSync('tar', ['-tzf', archive], { encoding: 'utf8' }).trim().split('\n')
    const entry = entries.find(path => path === name || path.endsWith(`/${name}`))
    if (!entry || entry.startsWith('/') || entry.split('/').includes('..')) throw new Error(`Unsafe archive: ${name}`)
    execFileSync('tar', ['-xzf', archive, '-C', destination, entry])
    if (resolve(destination, entry) !== output) await copyFile(resolve(destination, entry), output)
  }
  await chmod(output, 0o755)
}

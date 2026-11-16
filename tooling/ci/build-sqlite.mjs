import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
const prefix = resolve(dirname(process.execPath), '..')
if (!existsSync(`${prefix}/include/node/node.h`)) throw new Error('Verified Node distribution headers are required for an offline SQLite build')
execFileSync('pnpm', ['rebuild', 'better-sqlite3'], {
  stdio: 'inherit',
  env: { ...process.env, npm_config_build_from_source: 'true', npm_config_nodedir: prefix },
})

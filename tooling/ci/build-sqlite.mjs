import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { createRequire } from 'node:module'
const prefix = resolve(dirname(process.execPath), '..')
if (!existsSync(`${prefix}/include/node/node.h`)) throw new Error('Verified Node distribution headers are required for an offline SQLite build')
// SQLite belongs to the web workspace; rebuilding the root silently does nothing.
execFileSync('pnpm', ['--filter', '@edispark/web', 'rebuild', 'better-sqlite3'], {
  stdio: 'inherit',
  env: { ...process.env, npm_config_build_from_source: 'true', npm_config_nodedir: prefix },
})
const require = createRequire(new URL('../../apps/web/package.json', import.meta.url))
const Database = require('better-sqlite3')
const database = new Database(':memory:')
try {
  if (database.prepare('SELECT 1 AS ready').get().ready !== 1) throw new Error('SQLite binding verification failed')
} finally {
  database.close()
}

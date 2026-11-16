import { execFileSync } from 'node:child_process'
import { mkdtemp, writeFile, rm } from 'node:fs/promises'
import { join } from 'node:path'

const endpoint = process.env.CODEARTIFACT_NPM_ENDPOINT
const maven = process.env.CODEARTIFACT_MAVEN_ENDPOINT
const domain = process.env.CODEARTIFACT_DOMAIN
const owner = process.env.AWS_TOOLING_ACCOUNT_ID
if (!endpoint || !maven || !domain || !/^[0-9]{12}$/.test(owner ?? '')) throw new Error('Approved dependency repositories are not configured')
for (const value of [endpoint, maven]) {
  const url = new URL(value)
  if (url.protocol !== 'https:' || !url.hostname.endsWith('.d.codeartifact.eu-west-2.amazonaws.com') || url.username || url.password) throw new Error('Only the London approved repositories may supply release dependencies')
}
const temporary = await mkdtemp(join(process.env.RUNNER_TEMP, 'approved-settings-'))
try {
  const token = execFileSync('aws', ['codeartifact', 'get-authorization-token', '--domain', domain, '--domain-owner', owner, '--duration-seconds', '900', '--region', 'eu-west-2', '--query', 'authorizationToken', '--output', 'text'], { encoding: 'utf8' }).trim()
  if (!token || token === 'None') throw new Error('No dependency fetch token returned')
  const npmrc = join(temporary, 'npmrc')
  await writeFile(npmrc, `registry=${endpoint}\n//${new URL(endpoint).host}${new URL(endpoint).pathname}:_authToken=${token}\n`, { mode: 0o600 })
  const settings = join(temporary, 'settings.xml')
  const escape = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
  await writeFile(settings, `<settings><mirrors><mirror><id>approved</id><mirrorOf>*</mirrorOf><url>${escape(maven)}</url></mirror></mirrors><servers><server><id>approved</id><username>aws</username><password>${escape(token)}</password></server></servers></settings>`, { mode: 0o600 })
  execFileSync('pnpm', ['fetch', '--frozen-lockfile', '--ignore-scripts', '--store-dir', 'dist/approved/pnpm'], { stdio: 'inherit', env: { ...process.env, NPM_CONFIG_USERCONFIG: npmrc } })
  execFileSync('./mvnw', ['-B', '--no-transfer-progress', '-s', settings, '-Dmaven.repo.local=dist/approved/maven', '-Popenapi-export', 'dependency:go-offline', '-DexcludeReactor=true'], { stdio: 'inherit' })
} finally { await rm(temporary, { recursive: true, force: true }) }

import { createHash, constants, verify } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

export const requiredArtifacts = ['api.tar', 'web.zip', 'migrations.zip', 'sbom.json', 'scan.json']
export function validateManifest(manifest, { commit, repository, now = new Date() }) {
  if (!/^[0-9a-f]{40}$/.test(commit) || manifest.schema !== 1 || manifest.commit !== commit || manifest.repository !== repository
    || manifest.workflow !== '.github/workflows/release.yml' || manifest.policy !== 'pass') throw new Error('Untrusted release identity or policy')
  const age = now.getTime() - Date.parse(manifest.scannedAt)
  if (!Number.isFinite(age) || age < -60_000 || age > 24 * 60 * 60 * 1000) throw new Error('Release scan is stale')
  if (!requiredArtifacts.every(name => /^[0-9a-f]{64}$/.test(manifest.artifacts?.[name]))) throw new Error('Missing release evidence')
  if (Object.keys(manifest.artifacts).some(name => !requiredArtifacts.includes(name))) throw new Error('Unexpected release artifact')
}
export async function verifyRelease(directory, expected, publicKey) {
  const bytes = await readFile(resolve(directory, 'manifest.json'))
  const signature = await readFile(resolve(directory, 'manifest.sig'))
  if (!verify('sha256', bytes, { key: publicKey, padding: constants.RSA_PKCS1_PSS_PADDING, saltLength: 32 }, signature)) throw new Error('Invalid release signature')
  const manifest = JSON.parse(bytes.toString('utf8'))
  validateManifest(manifest, expected)
  for (const name of requiredArtifacts) {
    const hash = createHash('sha256').update(await readFile(resolve(directory, name))).digest('hex')
    if (hash !== manifest.artifacts[name]) throw new Error(`Modified release artifact: ${name}`)
  }
  return manifest
}

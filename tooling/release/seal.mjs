import { createHash } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import { KMSClient, SignCommand } from '@aws-sdk/client-kms'
import { requiredArtifacts, validateManifest } from './manifest.mjs'

if (process.env.GITHUB_REF !== 'refs/heads/main') throw new Error('Only trusted main releases can be signed')
const directory = process.argv[2]
const artifacts = {}
for (const name of requiredArtifacts) artifacts[name] = createHash('sha256').update(await readFile(`${directory}/${name}`)).digest('hex')
const scan = JSON.parse(await readFile(`${directory}/scan.json`, 'utf8'))
if ((scan.Results ?? []).some(result => (result.Vulnerabilities ?? []).some(item => ['HIGH', 'CRITICAL'].includes(item.Severity)))) throw new Error('Release contains blocking vulnerabilities')
const manifest = {
  schema: 1, commit: process.env.GITHUB_SHA, repository: process.env.GITHUB_REPOSITORY,
  workflow: '.github/workflows/release.yml', scannedAt: scan.CreatedAt,
  policy: 'pass', artifacts,
}
validateManifest(manifest, { commit: process.env.GITHUB_SHA, repository: process.env.GITHUB_REPOSITORY })
const bytes = Buffer.from(JSON.stringify(manifest))
const kms = new KMSClient({ region: 'eu-west-2' })
const result = await kms.send(new SignCommand({ KeyId: process.env.RELEASE_SIGNING_KEY_ARN, Message: createHash('sha256').update(bytes).digest(), MessageType: 'DIGEST', SigningAlgorithm: 'RSASSA_PSS_SHA_256' }))
if (!result.Signature) throw new Error('No release signature returned')
await writeFile(`${directory}/manifest.json`, bytes)
await writeFile(`${directory}/manifest.sig`, result.Signature)

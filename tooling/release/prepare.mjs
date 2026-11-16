import { readFile, writeFile } from 'node:fs/promises'
import { S3Client, PutObjectCommand, HeadObjectCommand } from '@aws-sdk/client-s3'
import { createHash } from 'node:crypto'

const config = JSON.parse(process.env.RELEASE_CONFIGURATION_JSON ?? '{}')
const previous = JSON.parse(await readFile(process.argv[2], 'utf8'))
const manifest = JSON.parse(await readFile('dist/release/manifest.json', 'utf8'))
const commit = process.env.GITHUB_SHA
if (manifest.commit !== commit || !/^[0-9a-f]{40}$/.test(commit)) throw new Error('Invalid release commit')
if (!config.foundation?.account_id || !config.release_bucket || !config.domain_name) throw new Error('Release account bindings are missing')
const zip = await readFile('dist/release/web.zip')
if (createHash('sha256').update(zip).digest('hex') !== manifest.artifacts['web.zip']) throw new Error('Web package changed after verification')
const s3 = new S3Client({ region: 'eu-west-1' })
const key = `releases/${commit}/web.zip`
const checksum = createHash('sha256').update(zip).digest('base64')
let object
try {
  object = await s3.send(new PutObjectCommand({ Bucket: config.release_bucket, Key: key, Body: zip, ChecksumSHA256: checksum, IfNoneMatch: '*' }))
} catch (error) {
  if (error.$metadata?.httpStatusCode !== 412) throw error
  object = await s3.send(new HeadObjectCommand({ Bucket: config.release_bucket, Key: key, ChecksumMode: 'ENABLED' }))
  if (object.ChecksumSHA256 !== checksum) throw new Error('Existing release key contains different bytes')
}
if (!object.VersionId) throw new Error('Release storage must be versioned')
const next = {
  ...config, backend_image: `${config.foundation.ecr_repository}@${process.env.IMAGE_DIGEST}`,
  revisions: { ...previous.revisions, [commit]: { bucket: config.release_bucket, key, version_id: object.VersionId } },
  traffic: previous.traffic,
}
delete next.release_bucket
await writeFile(process.argv[3], JSON.stringify(next))

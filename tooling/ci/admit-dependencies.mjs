import { readFile } from 'node:fs/promises'
import { CodeartifactClient, DescribePackageVersionCommand, CopyPackageVersionsCommand } from '@aws-sdk/client-codeartifact'

const client = new CodeartifactClient({ region: 'eu-west-2' })
const packages = JSON.parse(await readFile(process.argv[2], 'utf8'))
const emergency = process.env.DEPENDENCY_EMERGENCY === 'true'
if (emergency && !/^https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+\/issues\/\d+$/.test(process.env.EMERGENCY_REFERENCE ?? '')) throw new Error('An approved emergency requires its tracked security issue')
if (!Array.isArray(packages) || !packages.length) throw new Error('Provide a reviewed package admission manifest')
for (const item of packages) {
  if (!['npm', 'maven'].includes(item.format) || !item.package || !item.version || !item.revision) throw new Error('Incomplete package identity/revision')
  const identity = { domain: process.env.CODEARTIFACT_DOMAIN, domainOwner: process.env.AWS_TOOLING_ACCOUNT_ID, format: item.format, namespace: item.namespace, package: item.package }
  const source = `${item.format}-ingress`
  const version = await client.send(new DescribePackageVersionCommand({ ...identity, repository: source, packageVersion: item.version }))
  const published = version.packageVersion?.publishedTime?.getTime()
  if (!published || version.packageVersion.revision !== item.revision) throw new Error('Package revision changed or has no quarantine timestamp')
  if (!emergency && Date.now() - published < 7 * 24 * 60 * 60_000) throw new Error('Package has not completed the seven-day quarantine')
  const result = await client.send(new CopyPackageVersionsCommand({ ...identity, sourceRepository: source, destinationRepository: `${item.format}-approved`, versionRevisions: { [item.version]: item.revision }, allowOverwrite: false, includeFromUpstream: false }))
  if (Object.keys(result.failedVersions ?? {}).length) throw new Error('Admission copy failed; approved versions are never overwritten')
}

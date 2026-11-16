import { execFileSync } from 'node:child_process'
import { readFile, writeFile } from 'node:fs/promises'
import { classifyPlan } from '../ci/plan-policy.mjs'
import { CloudWatchClient, DescribeAlarmsCommand } from '@aws-sdk/client-cloudwatch'

const root = 'infrastructure/terraform/release'
const run = (args, capture = false) => execFileSync('.ci-tools/terraform', [`-chdir=${root}`, ...args], { encoding: 'utf8', stdio: capture ? ['ignore', 'pipe', 'inherit'] : 'inherit' })
const file = 'candidate.tfvars.json'
const before = JSON.parse(await readFile('dist/previous-release.json', 'utf8'))
const candidate = JSON.parse(await readFile(`${root}/${file}`, 'utf8'))
const current = Object.entries(before.traffic).find(([, weight]) => weight === 100)?.[0]
if (!current) throw new Error('A healthy baseline release is required')
if (current === process.env.GITHUB_SHA) process.exit(0)
// An older queued build must never replace a newer trunk deployment.
execFileSync('git', ['merge-base', '--is-ancestor', current, process.env.GITHUB_SHA])
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
const alarms = JSON.parse(process.env.DEPLOYMENT_ALARMS_JSON ?? '{}')
if (!Object.keys(alarms).length) throw new Error('Deployment health alarms are required')
async function requireHealthyAlarms() {
  for (const [region, names] of Object.entries(alarms)) {
    if (!['eu-west-2', 'us-east-1'].includes(region) || !Array.isArray(names) || !names.length) throw new Error('Invalid alarm configuration')
    const result = await new CloudWatchClient({ region }).send(new DescribeAlarmsCommand({ AlarmNames: names }))
    if (result.MetricAlarms?.length !== names.length || result.MetricAlarms.some(alarm => alarm.StateValue === 'ALARM')) throw new Error('Deployment health alarm is missing or firing')
  }
}
async function apply() {
  run(['plan', '-input=false', '-out=release.tfplan', `-var-file=${file}`])
  const plan = JSON.parse(run(['show', '-json', 'release.tfplan'], true))
  const policy = classifyPlan(plan)
  if (!policy.safe) throw new Error(`This release requires infrastructure review: ${policy.review.join(', ')}`)
  run(['apply', '-input=false', '-auto-approve', 'release.tfplan'])
}
try {
  for (const weight of [10, 50, 100]) {
    const previous = Object.entries(before.traffic).find(([, value]) => value === 100)?.[0]
    if (!previous) throw new Error('Promotion requires a single previously healthy release')
    candidate.traffic = weight === 100 ? { [process.env.GITHUB_SHA]: 100 } : { [previous]: 100 - weight, [process.env.GITHUB_SHA]: weight }
    await writeFile(`${root}/${file}`, JSON.stringify(candidate))
    await apply()
    const until = Date.now() + (weight === 100 ? 5 : 10) * 60_000
    do {
      execFileSync('node', ['tooling/release/smoke.mjs'], { stdio: 'inherit' })
      await requireHealthyAlarms()
      await delay(30_000)
    } while (Date.now() < until)
  }
} catch (error) {
  // Retain failed immutable revisions for investigation; only restore routing and the API image.
  const rollback = { ...before, revisions: candidate.revisions }
  await writeFile(`${root}/${file}`, JSON.stringify(rollback))
  await apply()
  throw error
}

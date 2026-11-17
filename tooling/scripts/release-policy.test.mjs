import { test } from 'node:test'
import assert from 'node:assert/strict'
import { classifyPlan } from '../ci/plan-policy.mjs'
import { validateManifest } from '../release/manifest.mjs'

const before = { auth_type: 'IamAuth', auto_deployment_mode: 'Disabled', endpoint_type: 'PerRegion', regions: ['eu-west-2'], revision_weight: [{ revision_id: 'old', weight: 100 }] }
function plan(after, actions = ['update']) {
  return { resource_changes: [{ address: 'aws_lambdaweb_function_endpoint.web', change: { before, after, actions } }] }
}
test('a traffic shift passes but disabling origin authentication requires review', () => {
  assert.equal(classifyPlan(plan({ ...before, revision_weight: [{ revision_id: 'new', weight: 10 }, { revision_id: 'old', weight: 90 }] })).safe, true)
  assert.equal(classifyPlan(plan({ ...before, auth_type: 'ApplicationManaged' })).safe, false)
})
test('deletion, unknown resources and invalid weights cannot use automatic infrastructure approval', () => {
  assert.equal(classifyPlan(plan(null, ['delete'])).safe, false)
  assert.equal(classifyPlan(plan({ ...before, revision_weight: [{ revision_id: 'new', weight: 101 }] })).safe, false)
  assert.equal(classifyPlan({ resource_changes: [{ address: 'aws_iam_role.unexpected', change: { actions: ['create'], after: {} } }] }).safe, false)
})
test('release verification rejects a different commit, stale scans and missing evidence', () => {
  const now = new Date('2026-11-13T12:00:00Z')
  const manifest = { schema: 1, commit: 'a'.repeat(40), repository: 'example/edispark', workflow: '.github/workflows/ci.yml', scannedAt: now.toISOString(), policy: 'pass', artifacts: { 'web.zip': 'b'.repeat(64), 'api.tar': 'c'.repeat(64), 'migrations.zip': 'f'.repeat(64), 'sbom.json': 'd'.repeat(64), 'scan.json': 'e'.repeat(64) } }
  const expected = { commit: manifest.commit, repository: manifest.repository, now }
  assert.doesNotThrow(() => validateManifest(manifest, expected))
  assert.throws(() => validateManifest(manifest, { ...expected, commit: 'f'.repeat(40) }))
  assert.throws(() => validateManifest({ ...manifest, scannedAt: '2026-11-10T12:00:00Z' }, expected))
  assert.throws(() => validateManifest({ ...manifest, artifacts: { 'web.zip': manifest.artifacts['web.zip'] } }, expected))
})

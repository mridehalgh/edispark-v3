import { readFile } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

const equal = (a, b) => JSON.stringify(a) === JSON.stringify(b)
const omit = (value, names) => Object.fromEntries(Object.entries(value ?? {}).filter(([key]) => !names.includes(key)))
const normalize = value => Array.isArray(value) ? value.map(normalize) : value && typeof value === 'object'
  ? Object.fromEntries(Object.entries(value).filter(([key]) => !key.endsWith('_actual')).map(([key, item]) => [key, key === 'environment_variables' ? omit(item, ['RELEASE_ID']) : normalize(item)])) : value

export function classifyPlan(plan) {
  const review = []
  const revisions = (plan.resource_changes ?? []).filter(resource => resource.type === 'aws_lambdaweb_function_revision' && resource.change.before)
  for (const resource of plan.resource_changes ?? []) {
    if (resource.mode === 'data' || resource.change.actions.every(action => action === 'no-op' || action === 'read')) continue
    const { before, after, actions, after_unknown: unknown = {} } = resource.change
    let safe = false
    if (resource.address === 'aws_ecs_task_definition.api' && before && after && actions.includes('create') && actions.includes('delete')) {
      const oldContainers = JSON.parse(before.container_definitions)
      const newContainers = JSON.parse(after.container_definitions)
      safe = after.skip_destroy === true && equal(omit(before, ['arn', 'arn_without_revision', 'revision', 'id', 'container_definitions']), omit(after, ['arn', 'arn_without_revision', 'revision', 'id', 'container_definitions']))
        && oldContainers.length === 1 && newContainers.length === 1
        && equal(omit(oldContainers[0], ['image']), omit(newContainers[0], ['image']))
        && /^[0-9]+\.dkr\.ecr\.eu-west-2\.amazonaws\.com\/.+@sha256:[a-f0-9]{64}$/.test(newContainers[0].image)
        && oldContainers[0].image.split('@')[0] === newContainers[0].image.split('@')[0]
    }
    if (resource.address === 'aws_ecs_service.api' && before && after && equal(actions, ['update'])) {
      safe = equal(omit(before, ['task_definition']), omit(after, ['task_definition']))
    }
    if (/^aws_lambdaweb_function_revision\.web\["[0-9a-f]{40}"\]$/.test(resource.address) && equal(actions, ['create']) && after) {
      const code = after.build_config?.[0]?.code_config?.[0]?.s3_object?.[0]
      safe = revisions.some(prior => prior.change.before.function_name === after.function_name
        && equal(normalize(prior.change.before.service_config), normalize(after.service_config))
        && equal(prior.change.before.build_config?.[0]?.runtime_config, after.build_config?.[0]?.runtime_config)
        && prior.change.before.kms_key_arn === after.kms_key_arn)
        && !!code?.version_id && typeof code.bucket === 'string' && /^releases\/[0-9a-f]{40}\/web\.zip$/.test(code.key)
    }
    if (resource.address === 'aws_lambdaweb_function_endpoint.web' && equal(actions, ['update'])) {
      safe = equal(omit(before, ['revision_weight']), omit(after, ['revision_weight']))
        && after.auth_type === 'IamAuth' && after.auto_deployment_mode === 'Disabled'
        && after.endpoint_type === 'PerRegion' && after.regions.includes('eu-west-2')
        && after.revision_weight.length >= 1 && after.revision_weight.length <= 2
        && after.revision_weight.every(item => Number.isInteger(item.weight) && item.weight >= 1 && item.weight <= 100)
        && after.revision_weight.reduce((total, item) => total + item.weight, 0) === 100
        && !Object.entries(unknown).some(([key, value]) => key !== 'revision_weight' && value === true)
    }
    // Unknown resource/attribute changes, creates and deletes require a reviewed infrastructure run.
    if (!safe) review.push(resource.address)
  }
  return { safe: review.length === 0, review }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const result = classifyPlan(JSON.parse(await readFile(process.argv[2], 'utf8')))
  if (!result.safe) throw new Error(`Infrastructure review required: ${result.review.join(', ')}`)
}

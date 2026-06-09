import type { RequestLifecycleState } from '@/integration/request-lifecycle'
import type { SchemaResponse, SchemaVersionResponse } from '@/integration/documents-api-client'

export type CreateSchemaFormModel = {
  name: string
  format: string
}

export type AddSchemaVersionFormModel = {
  schemaId: string
  versionIdentifier: string
  definitionText: string
}

export function validateCreateSchemaForm(form: CreateSchemaFormModel) {
  return [form.name.trim() ? undefined : 'Schema name is required.'].filter(Boolean) as string[]
}

export function validateAddSchemaVersionForm(form: AddSchemaVersionFormModel) {
  return [
    form.schemaId.trim() ? undefined : 'Schema ID is required.',
    form.versionIdentifier.trim() ? undefined : 'Version identifier is required.',
    form.definitionText.trim() ? undefined : 'Schema definition text is required.'
  ].filter(Boolean) as string[]
}

export function projectSchemaCreationSummary(response: SchemaResponse) {
  return `${response.name} created as ${response.id} in ${response.format} format.`
}

export function projectSchemaVersionSummary(response: SchemaVersionResponse) {
  return `${response.versionIdentifier} created as ${response.id} on ${new Date(response.createdAt).toLocaleString()}. Deprecated: ${response.deprecated ? 'yes' : 'no'}.`
}

export function projectCreateSchemaSubmission(
  form: CreateSchemaFormModel,
  result?: RequestLifecycleState<SchemaResponse>
) {
  const errors = validateCreateSchemaForm(form)

  if (errors.length > 0) {
    return { form, errors, shouldSubmit: false as const }
  }
  if (!result || result.status === 'idle' || result.status === 'pending') {
    return { form, errors: [], shouldSubmit: true as const }
  }
  if (result.status === 'failed') {
    return { form, errors: [result.reason], shouldSubmit: true as const }
  }

  return {
    form,
    errors: [],
    shouldSubmit: true as const,
    summary: projectSchemaCreationSummary(result.data)
  }
}

export function projectAddSchemaVersionSubmission(
  form: AddSchemaVersionFormModel,
  result?: RequestLifecycleState<SchemaVersionResponse>
) {
  const errors = validateAddSchemaVersionForm(form)

  if (errors.length > 0) {
    return { form, errors, shouldSubmit: false as const }
  }
  if (!result || result.status === 'idle' || result.status === 'pending') {
    return { form, errors: [], shouldSubmit: true as const }
  }
  if (result.status === 'failed') {
    return { form, errors: [result.reason], shouldSubmit: true as const }
  }

  return {
    form,
    errors: [],
    shouldSubmit: true as const,
    summary: projectSchemaVersionSummary(result.data)
  }
}

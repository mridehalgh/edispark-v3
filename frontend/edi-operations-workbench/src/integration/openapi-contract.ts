import { projectApiExplorer } from '@/integration/openapi-explorer-projection'
import { resolveLocalBackendConfig, type ResolvedLocalBackendConfig } from '@/integration/local-backend-config'

export type HttpMethod = 'get' | 'post' | 'put' | 'patch' | 'delete' | 'options' | 'head'

export type OpenApiSchemaObject = {
  $ref?: string
  type?: string
  title?: string
  format?: string
  description?: string
  enum?: string[]
  items?: OpenApiSchemaObject | OpenApiReferenceObject
  properties?: Record<string, OpenApiSchemaObject | OpenApiReferenceObject>
}

export type OpenApiReferenceObject = {
  $ref: string
}

export type OpenApiMediaTypeObject = {
  schema?: OpenApiSchemaObject | OpenApiReferenceObject
}

export type OpenApiRequestBodyObject = {
  description?: string
  required?: boolean
  content?: Record<string, OpenApiMediaTypeObject>
}

export type OpenApiResponseObject = {
  description?: string
  content?: Record<string, OpenApiMediaTypeObject>
}

export type OpenApiOperationObject = {
  operationId?: string
  summary?: string
  description?: string
  tags?: string[]
  requestBody?: OpenApiRequestBodyObject | OpenApiReferenceObject
  responses?: Record<string, OpenApiResponseObject | OpenApiReferenceObject>
}

export type OpenApiPathItemObject = Partial<Record<HttpMethod, OpenApiOperationObject>>

export type OpenApiDocument = {
  openapi: string
  info: {
    title: string
    version: string
    description?: string
  }
  paths: Record<string, OpenApiPathItemObject>
}

export type ContractState =
  | {
      kind: 'loading-contract'
      baseUrl: string
      openApiUrl: string
    }
  | {
      kind: 'connected'
      baseUrl: string
      openApiUrl: string
      contractTitle: string
      contractVersion: string
      operationCount: number
    }
  | {
      kind: 'contract-failed'
      baseUrl: string
      openApiUrl: string
      reason: string
    }
  | {
      kind: 'request-failed'
      baseUrl: string
      openApiUrl: string
      contractTitle: string
      contractVersion: string
      operationCount: number
      operationId: string
      reason: string
      status?: number
    }

export type ConnectedContractState = Extract<ContractState, { kind: 'connected' | 'request-failed' }>

type ConnectedContractSnapshot = {
  baseUrl: string
  openApiUrl: string
  contractTitle: string
  contractVersion: string
  operationCount: number
}

export type LoadContractResult = {
  document?: OpenApiDocument
  explorer?: ReturnType<typeof projectApiExplorer>
  state: ContractState
}

export type FetchLike = typeof fetch

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

export function createLoadingContractState(config: ResolvedLocalBackendConfig): ContractState {
  return {
    kind: 'loading-contract',
    baseUrl: config.baseUrl,
    openApiUrl: config.openApiUrl
  }
}

export function createContractFailureState(config: ResolvedLocalBackendConfig, reason: string): ContractState {
  return {
    kind: 'contract-failed',
    baseUrl: config.baseUrl,
    openApiUrl: config.openApiUrl,
    reason
  }
}

export function createRequestFailureState(
  state: ConnectedContractState,
  operationId: string,
  reason: string,
  status?: number
): ContractState {
  const connectedState = snapshotConnectedContractState(state)

  return {
    kind: 'request-failed',
    ...connectedState,
    operationId,
    reason,
    status
  }
}

export function recoverConnectedContractState(state: ConnectedContractState): Extract<ContractState, { kind: 'connected' }> {
  const connectedState = snapshotConnectedContractState(state)

  return {
    kind: 'connected',
    ...connectedState
  }
}

function snapshotConnectedContractState(state: ConnectedContractState): ConnectedContractSnapshot {
  return {
    baseUrl: state.baseUrl,
    openApiUrl: state.openApiUrl,
    contractTitle: state.contractTitle,
    contractVersion: state.contractVersion,
    operationCount: state.operationCount
  }
}

export function isContractReady(state: ContractState): state is ConnectedContractState {
  return state.kind === 'connected' || state.kind === 'request-failed'
}

export function assertOpenApiDocument(value: unknown): OpenApiDocument {
  if (!isRecord(value) || typeof value.openapi !== 'string') {
    throw new Error('The local backend did not return a valid OpenAPI document.')
  }

  if (!isRecord(value.info) || typeof value.info.title !== 'string' || typeof value.info.version !== 'string') {
    throw new Error('The OpenAPI document is missing required info metadata.')
  }

  if (!isRecord(value.paths)) {
    throw new Error('The OpenAPI document is missing its path catalogue.')
  }

  return value as OpenApiDocument
}

function describeContractLoadFailure(error: unknown, config: ResolvedLocalBackendConfig) {
  if (error instanceof Error) {
    return `${error.message} Expected local backend contract at ${config.openApiUrl}.`
  }

  return `Could not load the local backend contract. Expected local backend contract at ${config.openApiUrl}.`
}

export async function loadOpenApiContract(options?: {
  overrideBaseUrl?: string
  fetch?: FetchLike
}): Promise<LoadContractResult> {
  const config = resolveLocalBackendConfig(options?.overrideBaseUrl)
  const fetcher = options?.fetch ?? fetch

  try {
    const response = await fetcher(config.openApiUrl, {
      headers: {
        Accept: 'application/json'
      }
    })

    if (!response.ok) {
      throw new Error(`OpenAPI contract request failed with status ${response.status}`)
    }

    const document = assertOpenApiDocument(await response.json())
    const explorer = projectApiExplorer(document)

    return {
      document,
      explorer,
      state: {
        kind: 'connected',
        baseUrl: config.baseUrl,
        openApiUrl: config.openApiUrl,
        contractTitle: explorer.title,
        contractVersion: explorer.version,
        operationCount: explorer.operations.length
      }
    }
  } catch (error) {
    return {
      state: createContractFailureState(config, describeContractLoadFailure(error, config))
    }
  }
}

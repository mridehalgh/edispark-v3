import type { ContractState } from '@/integration/openapi-contract'

export type RequestLifecycleState<TData = unknown> =
  | {
      status: 'idle'
      operationId: string
    }
  | {
      status: 'pending'
      operationId: string
    }
  | {
      status: 'succeeded'
      operationId: string
      data: TData
    }
  | {
      status: 'failed'
      operationId: string
      reason: string
      retryable: boolean
      statusCode?: number
    }

export type RequestLifecycleMap = Record<string, RequestLifecycleState>

export type RequestLifecycleEvent =
  | {
      type: 'pending'
      operationId: string
    }
  | {
      type: 'succeeded'
      operationId: string
      data: unknown
    }
  | {
      type: 'failed'
      operationId: string
      reason: string
      retryable: boolean
      statusCode?: number
    }
  | {
      type: 'reset'
      operationId: string
    }

export function createIdleRequestState(operationId: string): RequestLifecycleState {
  return {
    status: 'idle',
    operationId
  }
}

export function reduceRequestLifecycleState(
  state: RequestLifecycleMap,
  event: RequestLifecycleEvent
): RequestLifecycleMap {
  if (event.type === 'reset') {
    return {
      ...state,
      [event.operationId]: createIdleRequestState(event.operationId)
    }
  }

  if (event.type === 'pending') {
    return {
      ...state,
      [event.operationId]: {
        status: 'pending',
        operationId: event.operationId
      }
    }
  }

  if (event.type === 'succeeded') {
    return {
      ...state,
      [event.operationId]: {
        status: 'succeeded',
        operationId: event.operationId,
        data: event.data
      }
    }
  }

  return {
    ...state,
    [event.operationId]: {
      status: 'failed',
      operationId: event.operationId,
      reason: event.reason,
      retryable: event.retryable,
      statusCode: event.statusCode
    }
  }
}

export function getRequestLifecycleState(state: RequestLifecycleMap, operationId: string) {
  return state[operationId] ?? createIdleRequestState(operationId)
}

export function canSubmitWhileDisconnected(contractState: ContractState) {
  return contractState.kind === 'connected' || contractState.kind === 'request-failed'
}

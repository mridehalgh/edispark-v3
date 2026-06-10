import { createContext, useCallback, useContext, useEffect, useMemo, useReducer, useState, type PropsWithChildren } from 'react'

import {
  createRequestFailureState,
  isContractReady,
  loadOpenApiContract,
  recoverConnectedContractState,
  type ConnectedContractState,
  type ContractState,
  type OpenApiDocument
} from '@/integration/openapi-contract'
import { createDocumentsApiClient, DocumentsApiError, type DocumentsApiClient } from '@/integration/documents-api-client'
import {
  getRequestLifecycleState,
  reduceRequestLifecycleState,
  type RequestLifecycleMap,
  type RequestLifecycleState
} from '@/integration/request-lifecycle'
import { type ApiExplorerProjection } from '@/integration/openapi-explorer-projection'

type IntegrationContextValue = {
  contractDocument?: OpenApiDocument
  contractState: ContractState
  documentsApi?: DocumentsApiClient
  explorer?: ApiExplorerProjection
  getRequestState(operationId: string): RequestLifecycleState
  refreshContract(): Promise<void>
  resetRequestState(operationId: string): void
  runRequest<TData>(operationId: string, execute: (client: DocumentsApiClient) => Promise<TData>): Promise<RequestLifecycleState<TData>>
}

const IntegrationContext = createContext<IntegrationContextValue | null>(null)

function createUnavailableError(contractState: ContractState, operationId: string) {
  return new DocumentsApiError({
    operationId,
    message: `Live workflow submission is unavailable until the local backend contract loads from ${contractState.openApiUrl}.`,
    retryable: true
  })
}

function toConnectedStateOrUndefined(contractState: ContractState) {
  return isContractReady(contractState) ? recoverConnectedContractState(contractState) : undefined
}

export function IntegrationProvider({ children }: PropsWithChildren) {
  const [contractState, setContractState] = useState<ContractState>({
    kind: 'loading-contract',
    baseUrl: 'http://localhost:8080',
    openApiUrl: 'http://localhost:8080/api-docs'
  })
  const [contractDocument, setContractDocument] = useState<OpenApiDocument>()
  const [explorer, setExplorer] = useState<ApiExplorerProjection>()
  const [requestStateMap, dispatchRequestLifecycle] = useReducer(reduceRequestLifecycleState, {} as RequestLifecycleMap)

  const refreshContract = useCallback(async () => {
    const result = await loadOpenApiContract()
    setContractState(result.state)
    setContractDocument(result.document)
    setExplorer(result.explorer)
  }, [])

  useEffect(() => {
    void refreshContract()
  }, [refreshContract])

  const documentsApi = useMemo(() => {
    const connectedState = toConnectedStateOrUndefined(contractState)

    return connectedState ? createDocumentsApiClient({ baseUrl: connectedState.baseUrl }) : undefined
  }, [contractState])

  const resetRequestState = useCallback((operationId: string) => {
    dispatchRequestLifecycle({ type: 'reset', operationId })

    setContractState((currentState) =>
      currentState.kind === 'request-failed' && currentState.operationId === operationId
        ? recoverConnectedContractState(currentState)
        : currentState
    )
  }, [])

  const runRequest = useCallback(
    async <TData,>(operationId: string, execute: (client: DocumentsApiClient) => Promise<TData>) => {
      if (!documentsApi || !isContractReady(contractState)) {
        const error = createUnavailableError(contractState, operationId)

        dispatchRequestLifecycle({
          type: 'failed',
          operationId,
          reason: error.message,
          retryable: error.retryable,
          statusCode: error.status
        })

        return {
          status: 'failed',
          operationId,
          reason: error.message,
          retryable: error.retryable,
          statusCode: error.status
        } satisfies RequestLifecycleState<TData>
      }

      dispatchRequestLifecycle({ type: 'pending', operationId })

      try {
        const data = await execute(documentsApi)

        dispatchRequestLifecycle({ type: 'succeeded', operationId, data })
        setContractState((currentState) =>
          currentState.kind === 'request-failed' ? recoverConnectedContractState(currentState) : currentState
        )

        return {
          status: 'succeeded',
          operationId,
          data
        } satisfies RequestLifecycleState<TData>
      } catch (error) {
        const apiError =
          error instanceof DocumentsApiError
            ? error
            : new DocumentsApiError({
                operationId,
                message: 'The request failed unexpectedly.',
                retryable: true
              })

        dispatchRequestLifecycle({
          type: 'failed',
          operationId,
          reason: apiError.message,
          retryable: apiError.retryable,
          statusCode: apiError.status
        })

        setContractState((currentState) => {
          if (!isContractReady(currentState)) {
            return currentState
          }

          return createRequestFailureState(currentState as ConnectedContractState, operationId, apiError.message, apiError.status)
        })

        return {
          status: 'failed',
          operationId,
          reason: apiError.message,
          retryable: apiError.retryable,
          statusCode: apiError.status
        } satisfies RequestLifecycleState<TData>
      }
    },
    [contractState, documentsApi]
  )

  const value = useMemo<IntegrationContextValue>(
    () => ({
      contractDocument,
      contractState,
      documentsApi,
      explorer,
      getRequestState(operationId: string) {
        return getRequestLifecycleState(requestStateMap, operationId)
      },
      refreshContract,
      resetRequestState,
      runRequest
    }),
    [contractDocument, contractState, documentsApi, explorer, refreshContract, requestStateMap, resetRequestState, runRequest]
  )

  return <IntegrationContext.Provider value={value}>{children}</IntegrationContext.Provider>
}

export function useIntegration() {
  const context = useContext(IntegrationContext)

  if (!context) {
    throw new Error('useIntegration must be used within an IntegrationProvider.')
  }

  return context
}

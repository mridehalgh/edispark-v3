import { useCallback, useEffect, useState } from 'react'

import { useIntegration } from '@/integration/integration-provider'
import type { DocumentSetPageResponse } from '@/integration/documents-api-client'
import { createIdleRequestState, type RequestLifecycleState } from '@/integration/request-lifecycle'

const listDocumentSetsOperationId = 'listDocumentSets'

export function useDocumentSetCatalogue(limit = 50) {
  const { contractState, runRequest, resetRequestState } = useIntegration()
  const [requestState, setRequestState] = useState<RequestLifecycleState<DocumentSetPageResponse>>(
    createIdleRequestState(listDocumentSetsOperationId) as RequestLifecycleState<DocumentSetPageResponse>
  )
  const [nextToken, setNextToken] = useState<string | undefined>()

  const refresh = useCallback(async () => {
    resetRequestState(listDocumentSetsOperationId)
    const nextState = await runRequest(listDocumentSetsOperationId, (client) => client.listDocumentSets({ limit, nextToken }))

    setRequestState(nextState)
  }, [limit, nextToken, resetRequestState, runRequest])

  useEffect(() => {
    if (contractState.kind !== 'connected') {
      return
    }

    void refresh()
  }, [contractState.kind, refresh])

  return {
    goToNextPage() {
      if (requestState.status === 'succeeded' && requestState.data.nextToken) {
        setNextToken(requestState.data.nextToken)
      }
    },
    goToPreviousPage() {
      if (requestState.status === 'succeeded') {
        setNextToken(requestState.data.previousToken)
      }
    },
    hasNextPage: requestState.status === 'succeeded' ? requestState.data.hasNext : false,
    hasPreviousPage: requestState.status === 'succeeded' ? requestState.data.hasPrevious : false,
    nextToken,
    refresh,
    requestState,
    page: requestState.status === 'succeeded' ? requestState.data : undefined
  }
}

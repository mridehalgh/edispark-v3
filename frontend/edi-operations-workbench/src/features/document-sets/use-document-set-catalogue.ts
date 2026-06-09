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

  const refresh = useCallback(async () => {
    resetRequestState(listDocumentSetsOperationId)
    const nextState = await runRequest(listDocumentSetsOperationId, (client) => client.listDocumentSets({ limit }))

    setRequestState(nextState)
  }, [limit, resetRequestState, runRequest])

  useEffect(() => {
    if (contractState.kind !== 'connected') {
      return
    }

    void refresh()
  }, [contractState.kind, refresh])

  return {
    refresh,
    requestState,
    page: requestState.status === 'succeeded' ? requestState.data : undefined
  }
}

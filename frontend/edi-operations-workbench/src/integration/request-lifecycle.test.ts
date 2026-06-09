import { describe, expect, it } from 'vitest'

import { createIdleRequestState, getRequestLifecycleState, reduceRequestLifecycleState } from '@/integration/request-lifecycle'

describe('request lifecycle state management', () => {
  it('tracks pending, failure, and reset states for a workflow operation', () => {
    const pendingState = reduceRequestLifecycleState({}, { type: 'pending', operationId: 'createDocumentSet' })
    const failedState = reduceRequestLifecycleState(pendingState, {
      type: 'failed',
      operationId: 'createDocumentSet',
      reason: 'Backend unavailable',
      retryable: true,
      statusCode: 503
    })
    const resetState = reduceRequestLifecycleState(failedState, { type: 'reset', operationId: 'createDocumentSet' })

    expect(getRequestLifecycleState(pendingState, 'createDocumentSet')).toEqual({
      status: 'pending',
      operationId: 'createDocumentSet'
    })
    expect(getRequestLifecycleState(failedState, 'createDocumentSet')).toEqual({
      status: 'failed',
      operationId: 'createDocumentSet',
      reason: 'Backend unavailable',
      retryable: true,
      statusCode: 503
    })
    expect(getRequestLifecycleState(resetState, 'createDocumentSet')).toEqual(createIdleRequestState('createDocumentSet'))
  })
})

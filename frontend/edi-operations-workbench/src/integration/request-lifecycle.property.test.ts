import fc from 'fast-check'
import { describe, expect, it } from 'vitest'

import {
  createRequestFailureState,
  recoverConnectedContractState,
  type ConnectedContractState
} from '@/integration/openapi-contract'
import { getRequestLifecycleState, reduceRequestLifecycleState } from '@/integration/request-lifecycle'

const textArb = fc.string({ minLength: 1, maxLength: 30 }).filter((value) => value.trim().length > 0)

type LifecycleAction =
  | { type: 'pending' }
  | { type: 'succeeded'; data: string }
  | { type: 'failed'; reason: string; statusCode?: number }
  | { type: 'reset' }
  | { type: 'refresh' }

const actionArb: fc.Arbitrary<LifecycleAction> = fc.oneof(
  fc.constant<LifecycleAction>({ type: 'pending' }),
  fc.record({ type: fc.constant<'succeeded'>('succeeded'), data: textArb }),
  fc.record({
    type: fc.constant<'failed'>('failed'),
    reason: textArb,
    statusCode: fc.option(fc.integer({ min: 400, max: 599 }), { nil: undefined })
  }),
  fc.constant<LifecycleAction>({ type: 'reset' }),
  fc.constant<LifecycleAction>({ type: 'refresh' })
)

describe('request lifecycle properties', () => {
  it('Feature: local-openapi-api-integration, Property 9: Request lifecycle transitions are recoverable', () => {
    fc.assert(
      fc.property(fc.array(actionArb, { minLength: 1, maxLength: 25 }), textArb, (actions, operationId) => {
        let requestStateMap = Object.create(null) as Record<string, ReturnType<typeof getRequestLifecycleState>>
        let contractState: ConnectedContractState = {
          kind: 'connected',
          baseUrl: 'http://localhost:8080',
          openApiUrl: 'http://localhost:8080/api-docs',
          contractTitle: 'Documents API',
          contractVersion: '0.1.0',
          operationCount: 12
        }

        for (const action of actions) {
          if (action.type === 'pending') {
            requestStateMap = reduceRequestLifecycleState(requestStateMap, { type: 'pending', operationId })
            contractState = recoverConnectedContractState(contractState)
          }

          if (action.type === 'succeeded') {
            requestStateMap = reduceRequestLifecycleState(requestStateMap, { type: 'succeeded', operationId, data: action.data })
            contractState = recoverConnectedContractState(contractState)
          }

          if (action.type === 'failed') {
            requestStateMap = reduceRequestLifecycleState(requestStateMap, {
              type: 'failed',
              operationId,
              reason: action.reason,
              retryable: true,
              statusCode: action.statusCode
            })
            contractState = createRequestFailureState(contractState, operationId, action.reason, action.statusCode) as ConnectedContractState

            expect(contractState.kind).toBe('request-failed')

            if (contractState.kind === 'request-failed') {
              expect(contractState.operationId).toBe(operationId)
              expect(contractState.reason).toBe(action.reason)
            }
          }

          if (action.type === 'reset') {
            requestStateMap = reduceRequestLifecycleState(requestStateMap, { type: 'reset', operationId })
            contractState = recoverConnectedContractState(contractState)
          }

          if (action.type === 'refresh') {
            contractState = recoverConnectedContractState(contractState)
          }

          const requestState = getRequestLifecycleState(requestStateMap, operationId)
          expect(requestState.operationId).toBe(operationId)

          if (action.type === 'refresh' || action.type === 'reset' || action.type === 'pending' || action.type === 'succeeded') {
            expect(contractState.kind).toBe('connected')
          }
        }
      }),
      { numRuns: 100 }
    )
  })
})

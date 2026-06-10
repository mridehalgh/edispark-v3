import { describe, expect, it } from 'vitest'

import {
  localBackendConfig,
  resolveLocalBackendBaseUrl,
  resolveLocalBackendConfig,
  resolveLocalOpenApiUrl
} from '@/integration/local-backend-config'

describe('local backend configuration', () => {
  it('uses the repository default backend URL when no override is provided', () => {
    expect(resolveLocalBackendBaseUrl(undefined)).toBe(localBackendConfig.defaultBaseUrl)
    expect(resolveLocalOpenApiUrl(undefined)).toBe('http://localhost:8080/api-docs')
  })

  it('normalises the override URL for both contract discovery and API requests', () => {
    expect(resolveLocalBackendConfig(' http://localhost:18080/ ')).toEqual({
      baseUrl: 'http://localhost:18080',
      openApiUrl: 'http://localhost:18080/api-docs'
    })
  })
})

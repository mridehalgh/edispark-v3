import { defineConfig } from '@hey-api/openapi-ts'

export default defineConfig({
  input: '../../contracts/integration/openapi.json',
  output: 'src/generated',
  plugins: [
    '@hey-api/client-fetch',
    {
      name: '@hey-api/sdk',
      operations: {
        containerName: 'IntegrationClient',
        strategy: 'single',
      },
      validator: {
        request: false,
        response: 'zod',
      },
    },
  ],
})

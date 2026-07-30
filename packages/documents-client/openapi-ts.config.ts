import { defineConfig } from '@hey-api/openapi-ts'

export default defineConfig({
  input: '../../api-contracts/documents/openapi.json',
  output: 'src/generated',
  plugins: [
    '@hey-api/client-fetch',
    {
      name: '@hey-api/sdk',
      operations: {
        containerName: 'DocumentsClient',
        strategy: 'single',
      },
      validator: 'zod',
    },
  ],
})

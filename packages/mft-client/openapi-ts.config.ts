import { defineConfig } from '@hey-api/openapi-ts'

export default defineConfig({
  input: '../../contracts/mft/openapi.json',
  output: 'src/generated',
  plugins: [
    '@hey-api/client-fetch',
    {
      name: '@hey-api/sdk',
      operations: {
        containerName: 'MftClient',
        strategy: 'single',
      },
      validator: {
        request: false,
        response: 'zod',
      },
    },
  ],
})

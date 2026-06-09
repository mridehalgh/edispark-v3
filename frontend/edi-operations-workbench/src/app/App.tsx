import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'

import { router } from '@/app/router'
import { IntegrationProvider } from '@/integration/integration-provider'

const queryClient = new QueryClient()

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <IntegrationProvider>
        <RouterProvider router={router} />
      </IntegrationProvider>
    </QueryClientProvider>
  )
}

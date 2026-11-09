import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import { RolProvider } from './rol'
import { router } from './router'

// Las respuestas GET llevan Cache-Control: no-store (arquitectura.md): no se guardan datos viejos entre pantallas.
const clienteConsultas = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 0, retry: false, refetchOnWindowFocus: false },
  },
})

export function App() {
  return (
    <QueryClientProvider client={clienteConsultas}>
      <RolProvider>
        <RouterProvider router={router} />
      </RolProvider>
    </QueryClientProvider>
  )
}

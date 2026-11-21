import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'

/** Monta una pantalla en `ruta` con su patrón de URL (por ejemplo "/galpones/:galponId"). */
export function renderizar(pantalla: ReactElement, ruta: string, patron = ruta.split('?')[0]) {
  const consultas = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: patron, element: pantalla }], { initialEntries: [ruta] })
  return render(
    <QueryClientProvider client={consultas}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

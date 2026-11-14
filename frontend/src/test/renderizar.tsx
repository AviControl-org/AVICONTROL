import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { RolContext, type Rol } from '../app/rol-contexto'

/**
 * Da a un árbol el contexto de rol sin pasar por `RolProvider`, para no depender de localStorage.
 * Por defecto administrador, que es el rol con todos los permisos. Lo usan también las pruebas que
 * montan su propio router con varias pantallas.
 */
export function ConRol({ rol = 'ADMINISTRADOR', children }: { rol?: Rol; children: ReactNode }) {
  return <RolContext.Provider value={{ rol, cambiarRol: () => {} }}>{children}</RolContext.Provider>
}

/** Monta una pantalla en `ruta` con su patrón de URL (por ejemplo "/galpones/:galponId"). */
export function renderizar(
  pantalla: ReactElement,
  ruta: string,
  patron = ruta.split('?')[0],
  rol: Rol = 'ADMINISTRADOR',
) {
  const consultas = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: patron, element: pantalla }], { initialEntries: [ruta] })
  return render(
    <ConRol rol={rol}>
      <QueryClientProvider client={consultas}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ConRol>,
  )
}

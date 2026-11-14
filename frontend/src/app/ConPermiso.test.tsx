import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { ConRol } from '../test/renderizar'
import { ConPermiso } from './ConPermiso'
import type { Rol } from './rol-contexto'

// Tres rutas con la misma guarda que en router.tsx: un listado de consulta y dos formularios protegidos.
function abrir(rol: Rol, ruta: string) {
  const router = createMemoryRouter(
    [
      { path: '/galpones', element: <p>Listado de galpones</p> },
      {
        path: '/galpones/registrar',
        element: (
          <ConPermiso permiso="REGISTRAR_GALPON">
            <p>Formulario de registro</p>
          </ConPermiso>
        ),
      },
      {
        path: '/alertas-mantenimiento/generar',
        element: (
          <ConPermiso permiso="GENERAR_ALERTA_MANTENIMIENTO">
            <p>Formulario de alerta</p>
          </ConPermiso>
        ),
      },
    ],
    { initialEntries: [ruta] },
  )
  render(
    <ConRol rol={rol}>
      <RouterProvider router={router} />
    </ConRol>,
  )
  return router
}

describe('ConPermiso', () => {
  it('deja pasar al administrador a registrar un galpón', () => {
    abrir('ADMINISTRADOR', '/galpones/registrar')

    expect(screen.getByText('Formulario de registro')).toBeInTheDocument()
  })

  it('devuelve al operario al listado si intenta registrar un galpón', async () => {
    const router = abrir('OPERARIO', '/galpones/registrar')

    expect(await screen.findByText('Listado de galpones')).toBeInTheDocument()
    expect(screen.queryByText('Formulario de registro')).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/galpones')
  })

  it('devuelve al técnico a su propia pantalla, que sí puede usar', async () => {
    const router = abrir('TECNICO_INFRAESTRUCTURA', '/galpones/registrar')

    expect(await screen.findByText('Formulario de alerta')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/alertas-mantenimiento/generar')
  })

  // Regresión: la guarda anterior mandaba a todo rol que no fuera administrador a la pantalla del técnico.
  // Con un tercer rol, el operario rebotaba de esa pantalla hacia ella misma sin fin.
  it('no deja al operario en un bucle de redirecciones al pedir una pantalla del técnico', async () => {
    const router = abrir('OPERARIO', '/alertas-mantenimiento/generar')

    expect(await screen.findByText('Listado de galpones')).toBeInTheDocument()
    expect(screen.queryByText('Formulario de alerta')).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/galpones')
  })
})

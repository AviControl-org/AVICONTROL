import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { GalponesPagina } from './GalponesPagina'
import { RegistrarGalponPagina } from './RegistrarGalponPagina'
import { ConRol } from '../../test/renderizar'

function renderizarFlujo() {
  const consultas = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      { path: '/galpones', element: <GalponesPagina /> },
      { path: '/galpones/registrar', element: <RegistrarGalponPagina /> },
    ],
    { initialEntries: ['/galpones/registrar'] },
  )
  render(
    <ConRol>
      <QueryClientProvider client={consultas}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ConRol>,
  )
  return router
}

async function llenar(nombre: string, capacidad: string) {
  const usuario = userEvent.setup()
  if (nombre) await usuario.type(screen.getByLabelText('Nombre del galpón'), nombre)
  if (capacidad) await usuario.type(screen.getByLabelText('Capacidad'), capacidad)
  await usuario.click(screen.getByRole('button', { name: 'Registrar galpón' }))
}

describe('RegistrarGalponPagina', () => {
  it('registra el galpón y vuelve al listado con el aviso de éxito (escenario 1)', async () => {
    const router = renderizarFlujo()
    await llenar('Zona Norte', '1500')

    expect(await screen.findByText('Zona Norte registrado')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?page=2')
    expect((await screen.findByRole('link', { name: 'Zona Norte' })).closest('tr')).toHaveClass('new')
    expect(screen.getByText('Quedó con estado Disponible y con un identificador único.')).toBeInTheDocument()
  })

  it('rechaza en el navegador una capacidad decimal y un nombre vacío sin llamar al servidor (escenarios 3 y 4)', async () => {
    renderizarFlujo()
    await llenar('', '10,5')

    expect(screen.getByText('Revisa 2 campos antes de registrar')).toBeInTheDocument()
    expect(screen.getByText(/El nombre es obligatorio, tiene máximo 100 caracteres/)).toBeInTheDocument()
    expect(screen.getByText('La capacidad debe ser un número entero mayor que cero.')).toBeInTheDocument()
    expect(screen.getByLabelText('Capacidad')).toHaveValue('10,5')
  })

  it('muestra junto al nombre el duplicado que responde el servidor (escenario 2)', async () => {
    renderizarFlujo()
    await llenar('galpón norte', '800')

    const campo = screen.getByLabelText('Nombre del galpón')
    expect(await screen.findByText('Ya existe un galpón con ese nombre.')).toBeInTheDocument()
    expect(campo).toHaveAttribute('aria-invalid', 'true')
    expect(campo).toHaveValue('galpón norte')
    expect(screen.queryByText('galpón norte registrado')).not.toBeInTheDocument()
  })
})

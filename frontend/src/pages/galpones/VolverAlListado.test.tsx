import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { expect, it } from 'vitest'
import { EditarPagina } from './EditarPagina'
import { EstadoPagina } from './EstadoPagina'
import { GalponesPagina } from './GalponesPagina'
import { ConRol } from '../../test/renderizar'

function abrir(accion: 'editar' | 'estado') {
  const id = '550e8400-e29b-41d4-a716-446655440000'
  const router = createMemoryRouter([
    { path: '/galpones', element: <GalponesPagina /> },
    { path: '/galpones/:galponId/editar', element: <EditarPagina /> },
    { path: '/galpones/:galponId/estado', element: <EstadoPagina /> },
  ], { initialEntries: ['/galpones/' + id + '/' + accion] })
  render(<ConRol><QueryClientProvider client={new QueryClient()}>
    <RouterProvider router={router} />
  </QueryClientProvider></ConRol>)
  return router
}


it('FR-009: un cambio manual confirmado vuelve al listado y anuncia el nuevo estado', async () => {
  const router = abrir('estado')
  await userEvent.click(await screen.findByRole('button', { name: 'Pasar a En cosecha' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Actualizar estado' }))
  expect(await screen.findByText('El galpón pasó a En cosecha.')).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/galpones')
})

it('FR-012: guardar una edición vuelve al listado y anuncia el galpón actualizado', async () => {
  const router = abrir('editar')
  const nombre = await screen.findByLabelText('Nombre')
  const usuario = userEvent.setup()
  await usuario.clear(nombre)
  await usuario.type(nombre, 'Galpón Renovado')
  await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))
  await usuario.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Guardar cambios' }))
  expect(await screen.findByText('Se guardaron los cambios de Galpón Renovado.')).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/galpones')
})


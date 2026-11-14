import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { LotesPagina } from './LotesPagina'
import { RegistrarLotePagina } from './RegistrarLotePagina'
import { lotes } from '../../mocks/datos'
import { ConRol } from '../../test/renderizar'

function renderizarFlujo(ruta = '/lotes/registrar') {
  const consultas = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      { path: '/lotes', element: <LotesPagina /> },
      { path: '/lotes/registrar', element: <RegistrarLotePagina /> },
    ],
    { initialEntries: [ruta] },
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

async function llenar(campos: { galpon?: string; nombre?: string; fecha?: string; poblacion?: string; costo?: string }) {
  const usuario = userEvent.setup()
  if (campos.galpon) await usuario.selectOptions(await screen.findByLabelText('Galpón'), campos.galpon)
  if (campos.nombre) await usuario.type(screen.getByLabelText('Nombre del lote'), campos.nombre)
  if (campos.fecha) fireEvent.change(screen.getByLabelText('Fecha de ingreso'), { target: { value: campos.fecha } })
  if (campos.poblacion) await usuario.type(screen.getByLabelText('Población inicial'), campos.poblacion)
  if (campos.costo) await usuario.type(screen.getByLabelText('Costo total'), campos.costo)
  await usuario.click(screen.getByRole('button', { name: 'Registrar lote' }))
}

describe('RegistrarLotePagina', () => {
  it('solo ofrece galpones Disponibles (FR-002)', async () => {
    renderizarFlujo()
    const opciones = within(await screen.findByLabelText('Galpón')).getAllByRole('option').slice(1)
    expect(opciones.map((opcion) => opcion.textContent)).toEqual(['Galpón C', 'Galpón J', 'Galpón Sur'])
    expect(screen.getByText('Solo aparecen galpones Disponibles (3).')).toBeInTheDocument()
  })

  it('preselecciona el galpón que llega desde su detalle', async () => {
    renderizarFlujo('/lotes/registrar?galponId=6ba7b810-9dad-41d1-80b4-00c04fd430c8')
    expect(await screen.findByLabelText('Galpón')).toHaveValue('6ba7b810-9dad-41d1-80b4-00c04fd430c8')
  })

  it('registra el lote y vuelve al listado con el aviso y el lote resaltado (escenario 1, FR-013)', async () => {
    renderizarFlujo()
    await llenar({ galpon: 'Galpón C', nombre: 'Lote C1', fecha: '2026-11-01', poblacion: '1000', costo: '5000000' })

    expect(await screen.findByText('Lote C1 registrado')).toBeInTheDocument()
    expect(screen.getByText(/La población actual quedó igual a la inicial \(1\.000 pollos\)\./)).toBeInTheDocument()
    expect(screen.getByText(/Disponible → Productivo del Galpón C\./)).toBeInTheDocument()
    const fila = (await screen.findByText('Lote C1')).closest('tr')
    expect(fila).toHaveClass('new')
  })

  it('valida en el navegador fecha futura, población y costo con decimales (escenarios 3, 4 y 5)', async () => {
    renderizarFlujo()
    await llenar({ galpon: 'Galpón J', nombre: 'Lote J1', fecha: '2999-01-01', poblacion: 'cien', costo: '100,75' })

    expect(screen.getByText('Revisa 3 campos antes de registrar el lote')).toBeInTheDocument()
    expect(screen.getByText('La fecha de ingreso no puede ser posterior a hoy.')).toBeInTheDocument()
    expect(screen.getByText('La población inicial debe ser un número entero mayor que cero.')).toBeInTheDocument()
    expect(
      screen.getByText('El costo total debe ser un número entero mayor que cero, en pesos colombianos.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Costo total')).toHaveValue('100,75')
  })

  it('pide seleccionar un galpón', async () => {
    renderizarFlujo()
    await screen.findByLabelText('Galpón')
    await llenar({ nombre: 'Lote X', fecha: '2026-11-01', poblacion: '10', costo: '10' })
    expect(screen.getByText('Debes seleccionar un galpón.')).toBeInTheDocument()
  })

  it('muestra junto al nombre el duplicado que responde el servidor (escenario 2)', async () => {
    renderizarFlujo()
    await llenar({ galpon: 'Galpón Sur', nombre: 'lote a1', fecha: '2026-11-01', poblacion: '1000', costo: '5000000' })

    expect(await screen.findByText('Ya existe un lote con ese nombre.')).toBeInTheDocument()
    expect(screen.getByLabelText('Nombre del lote')).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('LotesPagina', () => {
  it('abre la página del lote de ingreso anterior y resalta su fila', async () => {
    const inicio = lotes.length
    try {
      for (let i = 0; i < 10; i++) {
        lotes.push({ ...lotes[0], id: crypto.randomUUID(), nombre: 'Histórico ' + i,
          fechaIngreso: '2024-01-01', desvinculadoEn: '2024-03-01T00:00:00Z' })
      }
      const router = renderizarFlujo()
      await llenar({ galpon: 'Galpón J', nombre: 'Lote anterior', fecha: '2020-02-01', poblacion: '100', costo: '50000' })
      expect(await screen.findByText('Lote anterior registrado')).toBeInTheDocument()
      expect(router.state.location.search).toBe('?page=2')
      expect((await screen.findByText('Lote anterior')).closest('tr')).toHaveClass('new')
    } finally {
      lotes.splice(inicio)
    }
  })
  it('lista activos y anteriores con el nombre de su galpón', async () => {
    renderizarFlujo('/lotes')

    const tabla = await screen.findByRole('table')
    expect(within(tabla).getByText('Lote A0').closest('tr')).toHaveTextContent('Anterior')
    expect(within(tabla).getByText('Lote A1').closest('tr')).toHaveTextContent('Activo')
    expect((await within(tabla).findAllByRole('link', { name: 'Galpón Norte' })).length).toBe(2)
  })
})

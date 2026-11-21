import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderizar } from '../../test/renderizar'
import { GalponDetallePagina } from './GalponDetallePagina'

const PATRON = '/galpones/:galponId'

describe('GalponDetallePagina', () => {
  it('muestra el galpón, su lote activo y el historial (escenarios 5 y 7)', async () => {
    renderizar(<GalponDetallePagina />, '/galpones/550e8400-e29b-41d4-a716-446655440000', PATRON)

    expect(await screen.findByRole('heading', { name: 'Galpón Norte', level: 1 })).toBeInTheDocument()
    expect(screen.getByText('1.500 pollos')).toBeInTheDocument()
    expect(screen.getByText('975')).toBeInTheDocument()
    expect(screen.getByText('2fc03a21-84c4-4c83-bb15-6607bb75cb90')).toBeInTheDocument()
    expect(screen.getByText('$ 5.000.000 COP')).toBeInTheDocument()
    expect(await screen.findByText('Lote A0')).toBeInTheDocument()
    expect(screen.getByText('Activo')).toBeInTheDocument()
    expect(screen.getByText('Anterior')).toBeInTheDocument()
  })

  it('galpón sin lotes: muestra el mensaje de la API (escenario 6)', async () => {
    renderizar(<GalponDetallePagina />, '/galpones/6ba7b810-9dad-41d1-80b4-00c04fd430c8', PATRON)

    expect(await screen.findByText('No existen lotes registrados para este galpón')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Registrar lote' })).toBeInTheDocument()
  })

  it('campo del lote omitido por la API se muestra como "No disponible" (escenario 11)', async () => {
    renderizar(<GalponDetallePagina />, '/galpones/c5e8f3a2-6b17-4d09-9f2e-8a1b3c4d5e6f', PATRON)

    expect(await screen.findByText('El nombre del lote no está disponible.')).toBeInTheDocument()
    expect(screen.getAllByText('No disponible').length).toBeGreaterThan(0)
  })

  it('galpón inexistente: mensaje del 404 y enlace para volver, sin datos de otro galpón', async () => {
    renderizar(<GalponDetallePagina />, '/galpones/d9b2d63d-a233-4123-847a-3f2c0c1e6b55', PATRON)

    expect(await screen.findByText('El galpón consultado no existe.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver al listado' })).toBeInTheDocument()
    expect(screen.queryByText('Lote activo')).not.toBeInTheDocument()
  })
})

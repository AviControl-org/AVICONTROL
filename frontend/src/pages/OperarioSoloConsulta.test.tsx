import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderizar } from '../test/renderizar'
import { servidor } from '../test/servidor'
import { GalponDetallePagina } from './galpones/GalponDetallePagina'
import { GalponesPagina } from './galpones/GalponesPagina'
import { LotesPagina } from './lotes/LotesPagina'

// docs/Casos de uso.md, Operario de granja: «puede consultar el listado de galpones y el detalle de cada
// galpón con su lote activo. No puede registrar, editar ni cambiar el estado de ningún galpón».
describe('el operario de granja solo consulta', () => {
  it('ve el listado de galpones sin las acciones de registro', async () => {
    renderizar(<GalponesPagina />, '/galpones', undefined, 'OPERARIO')

    expect(await screen.findByRole('table')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Registrar galpón/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Registrar lote/ })).not.toBeInTheDocument()
  })

  it('con la granja vacía no le ofrece registrar galpones y le dice quién lo hace', async () => {
    servidor.use(http.get('/api/v1/galpones', () => HttpResponse.json({
      contenido: [], totalElementos: 0, totalPaginas: 0, pagina: 1, tamanio: 10, sinCoincidencias: false,
    })))
    renderizar(<GalponesPagina />, '/galpones', undefined, 'OPERARIO')

    expect(await screen.findByText('El administrador es quien registra los galpones.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Registrar galpón/ })).not.toBeInTheDocument()
  })

  it('ve el detalle del galpón y su lote activo, sin editar ni cambiar el estado', async () => {
    renderizar(
      <GalponDetallePagina />,
      '/galpones/550e8400-e29b-41d4-a716-446655440000',
      '/galpones/:galponId',
      'OPERARIO',
    )

    expect(await screen.findByRole('heading', { name: 'Galpón Norte', level: 1 })).toBeInTheDocument()
    expect(screen.getByText('975')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Volver al listado/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Editar galpón/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Ver estado e historial/ })).not.toBeInTheDocument()
  })

  it('ve el listado de lotes sin la acción de registrar', async () => {
    renderizar(<LotesPagina />, '/lotes', undefined, 'OPERARIO')

    expect(await screen.findByRole('table')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Registrar lote/ })).not.toBeInTheDocument()
  })
})

describe('el administrador conserva sus acciones', () => {
  it('ve registrar galpón y registrar lote en el listado', async () => {
    renderizar(<GalponesPagina />, '/galpones')

    await screen.findByRole('table')
    expect(screen.getByRole('link', { name: /Registrar galpón/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Registrar lote/ })).toBeInTheDocument()
  })

  it('ve editar y cambiar el estado en el detalle', async () => {
    renderizar(<GalponDetallePagina />, '/galpones/550e8400-e29b-41d4-a716-446655440000', '/galpones/:galponId')

    await screen.findByRole('heading', { name: 'Galpón Norte', level: 1 })
    expect(screen.getByRole('link', { name: /Editar galpón/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Ver estado e historial/ })).toBeInTheDocument()
  })
})

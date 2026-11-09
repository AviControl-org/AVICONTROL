import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { CLAVE_SIMULAR_ERROR } from '../../mocks/galpones'
import { renderizar } from '../../test/renderizar'
import { GalponesPagina } from './GalponesPagina'

describe('GalponesPagina', () => {
  it('muestra 10 galpones por página ordenados por nombre, con capacidad y estado (escenario 1)', async () => {
    renderizar(<GalponesPagina />, '/galpones')

    const filas = within(await screen.findByRole('table')).getAllByRole('row').slice(1)
    expect(filas).toHaveLength(10)
    expect(within(filas[0]).getByRole('link', { name: 'Galpón A' })).toBeInTheDocument()
    expect(screen.getByText('Mostrando 1 a 10 de 12 galpones · 10 por página')).toBeInTheDocument()
  })

  it('filtra por estado desde la URL', async () => {
    renderizar(<GalponesPagina />, '/galpones?estado=AISLAMIENTO')

    const filas = within(await screen.findByRole('table')).getAllByRole('row').slice(1)
    expect(filas).toHaveLength(1)
    expect(within(filas[0]).getByText('Galpón F')).toBeInTheDocument()
  })

  it('sin coincidencias avisa con el mensaje de la API y muestra todos los galpones (escenario 4)', async () => {
    renderizar(<GalponesPagina />, '/galpones?nombre=xyz')

    expect(await screen.findByText('No encontramos galpones con «xyz»')).toBeInTheDocument()
    expect(
      screen.getByText('No se encontraron galpones con ese nombre. Se muestran todos los galpones.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Mostrando 1 a 10 de 12 galpones · 10 por página')).toBeInTheDocument()
  })

  it('ante un error conserva los criterios y permite reintentar (escenario 12)', async () => {
    localStorage.setItem(CLAVE_SIMULAR_ERROR, 'true')
    renderizar(<GalponesPagina />, '/galpones?estado=PRODUCTIVO')

    expect(await screen.findByText('No pudimos cargar los galpones')).toBeInTheDocument()
    expect(screen.getByText('No fue posible obtener los datos. Puedes reintentar la consulta.')).toBeInTheDocument()
    expect(screen.getByText('Estado: Productivo')).toBeInTheDocument()

    localStorage.removeItem(CLAVE_SIMULAR_ERROR)
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })
})

import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { renderizar } from '../../test/renderizar'
import { obtenerGalpon } from '../../api/galpones'
import { GenerarAlertaPagina } from './GenerarAlertaPagina'
import { AlertasPagina } from './AlertasPagina'

it('explica la descripción vacía y conserva los espacios hasta que el técnico la corrija', async () => {
  const usuario = userEvent.setup()
  renderizar(<GenerarAlertaPagina/>, '/alertas-mantenimiento/generar')
  const galpon = await screen.findByRole('option', { name: 'Galpón Sur' })
  await usuario.selectOptions(screen.getByLabelText('Galpón disponible'), galpon)
  const campo = screen.getByLabelText('Descripción')
  const boton = screen.getByRole('button', { name: 'Generar alerta' })
  expect(campo).not.toHaveAccessibleDescription('Escribe una descripción del problema')
  await usuario.click(campo)
  await usuario.tab()
  expect(campo).toHaveAccessibleDescription('Escribe una descripción del problema')
  await usuario.type(campo, '   ')
  expect(campo).toHaveValue('   ')
  expect(campo).toHaveAccessibleDescription('Escribe una descripción del problema')
  expect(boton).toBeDisabled()
  await usuario.type(campo, 'Ventilador averiado')
  expect(campo).toBeValid()
  expect(boton).toBeEnabled()
})

it('generar conserva el estado disponible y atender cambia el galpón a mantenimiento', async () => {
  const id = '6ba7b810-9dad-41d1-80b4-00c04fd430c8'
    const usuario = userEvent.setup()
    const formulario = renderizar(<GenerarAlertaPagina/>, '/alertas-mantenimiento/generar')
    await screen.findByRole('option', { name: 'Galpón Sur' })
    await usuario.selectOptions(screen.getByLabelText('Galpón disponible'), id)
    await usuario.type(screen.getByLabelText('Descripción'), 'Reparar ventilación')
    await usuario.click(screen.getByRole('button', { name: 'Generar alerta' }))
    await usuario.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Generar alerta' }))
    await screen.findByText('Alerta generada. Queda pendiente de atención.')
    expect((await obtenerGalpon(id)).estado).toBe('DISPONIBLE')
    formulario.unmount()
    renderizar(<AlertasPagina/>, '/alertas')
    await usuario.click(await screen.findByRole('button', { name: 'Atender alerta' }))
    await usuario.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Atender alerta' }))
    await screen.findByText('Alerta atendida. El galpón pasó a mantenimiento.')
    expect((await obtenerGalpon(id)).estado).toBe('MANTENIMIENTO')

})


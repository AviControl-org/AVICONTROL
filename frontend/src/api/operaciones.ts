import { solicitar } from './cliente'
import type { EstadoGalpon, Pagina } from './tipos'
import type { GalponResponse } from './galpones'

export interface Transiciones {
  galponId: string
  estadoActual: EstadoGalpon
  destinosPermitidos: EstadoGalpon[]
}
export interface Cambio {
  id: string
  galponId: string
  estadoAnterior: EstadoGalpon
  estadoNuevo: EstadoGalpon
  fechaHora: string
  origen: string
}
export interface GenerarAlerta {
  galponId: string
  tecnicoId: string
  descripcion: string
  severidad?: string
  tipo?: string
}
export interface Alerta extends GenerarAlerta {
  id: string
  fechaHora: string
  estado: 'PENDIENTE' | 'ATENDIDA'
}
const galpon = (id: string) => '/galpones/' + encodeURIComponent(id)
const alertas = '/alertas-mantenimiento'
const json = (method: string, data: object) => ({ method, body: JSON.stringify(data) })

export const verTransiciones = (id: string) => solicitar<Transiciones>(galpon(id) + '/transiciones')
export const verHistorial = (id: string, page: number) =>
  solicitar<Pagina<Cambio>>(galpon(id) + '/historial-estados?page=' + page)
export const cambiarEstado = (id: string, estadoEsperado: EstadoGalpon, estadoDestino: EstadoGalpon) =>
  solicitar(galpon(id) + '/estado', json('PATCH', { estadoEsperado, estadoDestino }))
export const editarGalpon = (id: string, cambios: { nombre?: string; capacidad?: number }) =>
  solicitar<GalponResponse>(galpon(id), json('PATCH', cambios))
export const generarAlerta = (datos: GenerarAlerta) => solicitar<Alerta>(alertas, json('POST', datos))
export const listarAlertas = (estado: string, page: number) =>
  solicitar<Pagina<Alerta>>(alertas + '?' + new URLSearchParams({ ...(estado && { estado }), page: String(page) }))
export const atenderAlerta = (id: string) =>
  solicitar(alertas + '/' + encodeURIComponent(id), json('PATCH', { estado: 'ATENDIDA' }))

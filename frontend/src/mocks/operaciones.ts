import { http, HttpResponse } from 'msw'
import type { Alerta, Cambio, GenerarAlerta } from '../api/operaciones'
import type { EstadoGalpon } from '../api/tipos'
import { galpones, type GalponSimulado } from './datos'
import { problema } from './galpones'

const base = '/api/v1/galpones/:id'
const bandeja = '/api/v1/alertas-mantenimiento'
const alertas: Alerta[] = []
const cambios: Cambio[] = []
const destinos: Partial<Record<EstadoGalpon, EstadoGalpon[]>> = {
  PRODUCTIVO: ['EN_COSECHA'], AISLAMIENTO: ['EN_COSECHA'], MANTENIMIENTO: ['DISPONIBLE'],
}
const buscar = (id: unknown) => galpones.find(g => g.id === id)
const fallo = (r: Request, status: number, code: string, mensaje: string) =>
  problema(status, code, mensaje, mensaje, new URL(r.url).pathname)
const ausente = (r: Request) => fallo(r, 404, 'GALPON_NO_ENCONTRADO', 'El galpón no existe.')
function pagina<T>(filas: T[], url: URL) {
  const pagina = Number(url.searchParams.get('page') ?? 1)
  const tamanio = Number(url.searchParams.get('size') ?? 20)
  return { contenido: filas.slice((pagina - 1) * tamanio, pagina * tamanio), pagina, tamanio,
    totalElementos: filas.length, totalPaginas: Math.ceil(filas.length / tamanio) }
}
function transicion(g: GalponSimulado, estadoNuevo: EstadoGalpon) {
  const cambio = { galponId: g.id, estadoAnterior: g.estado, estadoNuevo, fechaHora: new Date().toISOString(), origen: 'ADMINISTRADOR' }
  cambios.unshift({ ...cambio, id: crypto.randomUUID() })
  g.estado = estadoNuevo
  return cambio
}
export const manejadoresOperaciones = [
  http.get(base + '/transiciones', ({ params, request }) => {
    const g = buscar(params.id)
    return g ? HttpResponse.json({ galponId: g.id, estadoActual: g.estado, destinosPermitidos: destinos[g.estado] ?? [] }) : ausente(request)
  }),
  http.get(base + '/historial-estados', ({ params, request }) => buscar(params.id)
    ? HttpResponse.json(pagina(cambios.filter(c => c.galponId === params.id), new URL(request.url))) : ausente(request)),
  http.patch(base + '/estado', async ({ params, request }) => {
    const g = buscar(params.id)
    if (!g) return ausente(request)
    const { estadoEsperado, estadoDestino } = await request.json() as { estadoEsperado: EstadoGalpon; estadoDestino: EstadoGalpon }
    if (estadoEsperado !== g.estado) return fallo(request, 409, 'ESTADO_ESPERADO_CAMBIADO', 'El estado del galpón cambió.')
    if (!destinos[g.estado]?.includes(estadoDestino)) return fallo(request, 409, 'TRANSICION_NO_PERMITIDA', 'Transición no permitida.')
    return HttpResponse.json(transicion(g, estadoDestino))
  }),
  http.patch(base, async ({ params, request }) => {
    const g = buscar(params.id)
    if (!g) return ausente(request)
    const { nombre, capacidad } = await request.json() as { nombre?: string; capacidad?: number }
    if (nombre && galpones.some(otro => otro.id !== g.id && otro.nombre.toLowerCase() === nombre.trim().toLowerCase()))
      return fallo(request, 409, 'NOMBRE_DUPLICADO', 'Ya existe un galpón con ese nombre.')
    if (capacidad !== undefined && g.estado !== 'MANTENIMIENTO') return fallo(request, 409, 'CAPACIDAD_NO_EDITABLE', 'La capacidad solo se edita en mantenimiento.')
    if (nombre) g.nombre = nombre.trim()
    if (capacidad !== undefined) g.capacidad = capacidad
    return HttpResponse.json(g)
  }),
  http.get(bandeja, ({ request }) => {
    const url = new URL(request.url)
    const estado = url.searchParams.get('estado')
    return HttpResponse.json(pagina(alertas.filter(a => !estado || a.estado === estado), url))
  }),
  http.post(bandeja, async ({ request }) => {
    const datos = await request.json() as GenerarAlerta
    const g = buscar(datos.galponId)
    if (!g) return ausente(request)
    if (g.estado !== 'DISPONIBLE') return fallo(request, 409, 'GALPON_NO_DISPONIBLE', 'El galpón no está disponible.')
    if (alertas.some(a => a.galponId === g.id && a.estado === 'PENDIENTE')) return fallo(request, 409, 'ALERTA_ACTIVA_EXISTENTE', 'Ya existe una alerta pendiente.')
    const alerta: Alerta = { ...datos, id: crypto.randomUUID(), fechaHora: new Date().toISOString(), estado: 'PENDIENTE' }
    alertas.unshift(alerta)
    return HttpResponse.json(alerta, { status: 201 })
  }),
  http.patch(bandeja + '/:id', ({ params, request }) => {
    const a = alertas.find(a => a.id === params.id)
    if (!a) return fallo(request, 404, 'ALERTA_NO_ENCONTRADA', 'La alerta no existe.')
    if (a.estado !== 'PENDIENTE') return fallo(request, 409, 'ALERTA_NO_PENDIENTE', 'La alerta ya fue atendida.')
    const g = buscar(a.galponId)
    if (!g || g.estado !== 'DISPONIBLE') return fallo(request, 409, 'ESTADO_ESPERADO_CAMBIADO', 'El galpón ya no está disponible.')
    transicion(g, 'MANTENIMIENTO')
    a.estado = 'ATENDIDA'
    return HttpResponse.json({ alerta: { id: a.id, estado: a.estado }, galpon: { id: g.id, estado: g.estado } })
  }),
]

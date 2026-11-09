import { solicitar } from './cliente'
import type { EstadoGalpon, Pagina } from './tipos'

// DTO de docs/spec-consultar-galpón-lote/plan-consultar-galpon-lote.md, sección Contrato.

export interface GalponResumenResponse {
  id: string
  nombre: string
  capacidad: number
  estado: EstadoGalpon
}

export interface GalponPaginaResponse extends Pagina<GalponResumenResponse> {
  sinCoincidencias: boolean
  mensaje?: string
}

/** Un campo del lote vacío o inválido se omite y se informa en `incidencias` (FR-016). */
export interface LoteDetalleResponse {
  id: string
  nombre?: string
  poblacionInicial?: number
  poblacionActual?: number
  fechaIngreso?: string
  edadDias?: number
  costoTotal?: number
}

export interface IncidenciaResponse {
  codigo: string
  mensaje: string
}

export interface GalponDetalleResponse {
  id: string
  nombre: string
  capacidad: number
  estado: EstadoGalpon
  loteActivo?: LoteDetalleResponse
  mensaje?: string
  incidencias?: IncidenciaResponse[]
}

export interface LoteHistorialResponse {
  galponId: string
  loteActivo?: LoteDetalleResponse
  lotesAnteriores: LoteDetalleResponse[]
  incidencias?: IncidenciaResponse[]
}

export type CampoOrden = 'nombre' | 'capacidad' | 'estado'
export type Direccion = 'asc' | 'desc'

export interface ParametrosListado {
  nombre?: string
  estado?: EstadoGalpon
  /** `campo,direccion`; por defecto `nombre,asc`. */
  orden?: `${CampoOrden},${Direccion}`
  /** Desde 1; tamaño fijo de 10. */
  page?: number
}

export function listarGalpones(parametros: ParametrosListado): Promise<GalponPaginaResponse> {
  const query = new URLSearchParams()
  if (parametros.nombre?.trim()) query.set('nombre', parametros.nombre.trim())
  if (parametros.estado) query.set('estado', parametros.estado)
  if (parametros.orden) query.set('orden', parametros.orden)
  query.set('page', String(parametros.page ?? 1))
  return solicitar(`/galpones?${query}`)
}

// DTO de docs/spec-registrarGalpon/plan-registrar-galpon.md, sección Contrato.

export interface RegistrarGalponRequest {
  nombre: string
  capacidad: number
}

export interface GalponResponse {
  id: string
  nombre: string
  capacidad: number
  estado: EstadoGalpon
}

export function registrarGalpon(request: RegistrarGalponRequest): Promise<GalponResponse> {
  return solicitar('/galpones', { method: 'POST', body: JSON.stringify(request) })
}

export function obtenerGalpon(galponId: string): Promise<GalponDetalleResponse> {
  return solicitar(`/galpones/${encodeURIComponent(galponId)}`)
}

export function obtenerHistorialLotes(galponId: string): Promise<LoteHistorialResponse> {
  return solicitar(`/galpones/${encodeURIComponent(galponId)}/lotes`)
}

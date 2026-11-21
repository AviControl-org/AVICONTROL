import { solicitar } from './cliente'
import type { Pagina } from './tipos'

// DTO de docs/spec-registrarLote/plan-registrar-lote.md, sección Contrato.

export interface RegistrarLoteRequest {
  galponId: string
  nombre: string
  /** `yyyy-MM-dd`; no puede ser futura en America/Bogota. */
  fechaIngreso: string
  poblacionInicial: number
  /** Pesos colombianos, sin decimales. */
  costoTotal: number
}

export interface LoteResponse {
  id: string
  nombre: string
  galponId: string
  fechaIngreso: string
  poblacionInicial: number
  poblacionActual: number
  costoTotal: number
  /** ISO-8601 con desplazamiento; solo en lotes anteriores, se omite mientras el lote está activo. */
  desvinculadoEn?: string
}

export type LotePaginaResponse = Pagina<LoteResponse>

export function registrarLote(request: RegistrarLoteRequest): Promise<LoteResponse> {
  return solicitar('/lotes', { method: 'POST', body: JSON.stringify(request) })
}

/** Todos los lotes (activos y anteriores), del ingreso más reciente al más antiguo; 10 por página. */
export function listarLotes(page: number): Promise<LotePaginaResponse> {
  return solicitar(`/lotes?page=${page}`)
}
